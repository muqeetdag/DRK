#!/usr/bin/env python3
"""
Build binary AndroidManifest.xml (AXML) for DRK app
"""
import struct

def uleb128_encode(value):
    out = bytearray()
    while True:
        byte = value & 0x7f
        value >>= 7
        if value != 0:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            break
    return bytes(out)

def encode_axml_string(s):
    # UTF-8 mode: utf16_len, utf8_len, bytes, null
    # For simplicity ascii, utf16_len = len(s), utf8_len = len(utf8)
    utf8 = s.encode('utf-8')
    utf16_len = len(s)  # approximate for ascii
    utf8_len = len(utf8)
    # encode lengths as uleb128 (1 byte for <128)
    return uleb128_encode(utf16_len) + uleb128_encode(utf8_len) + utf8 + b'\x00'

# Define strings
strings = [
    "manifest",  #0
    "package",  #1
    "com.drkmuqeet.app",  #2
    "versionCode",  #3
    "versionName",  #4
    "1.0",  #5
    "uses-sdk",  #6
    "minSdkVersion",  #7
    "targetSdkVersion",  #8
    "application",  #9
    "label",  #10
    "DRK",  #11
    "activity",  #12
    "name",  #13
    ".MainActivity",  #14
    "intent-filter",  #15
    "action",  #16
    "android.intent.action.MAIN",  #17
    "category",  #18
    "android.intent.category.LAUNCHER",  #19
    "http://schemas.android.com/apk/res/android",  #20
    "android",  #21
    "exported",  #22
]

# Add more if needed
str_idx = {s:i for i,s in enumerate(strings)}

# Helper to create string pool chunk
def build_string_pool(strings):
    # encode each string
    encoded_strings = [encode_axml_string(s) for s in strings]
    # offsets relative to strings data start
    offsets = []
    cur = 0
    for enc in encoded_strings:
        offsets.append(cur)
        cur += len(enc)
    # strings data
    strings_data = b''.join(encoded_strings)
    # pad to 4 bytes
    pad_len = (4 - (len(strings_data) % 4)) % 4
    strings_data_padded = strings_data + b'\x00'*pad_len

    # header fields
    # type 0x0001, header_size 28, size = header_size + n*4 + len(strings_data_padded)
    n = len(strings)
    header_size = 28
    strings_start = header_size + n*4
    chunk_size = strings_start + len(strings_data_padded)
    # flags = 0x100 UTF-8
    # stringCount, styleCount, flags, stringsStart, stylesStart
    chunk = struct.pack('<HHI', 0x0001, header_size, chunk_size)
    chunk += struct.pack('<IIIII', n, 0, 0x100, strings_start, 0)
    # offsets
    for off in offsets:
        chunk += struct.pack('<I', off)
    chunk += strings_data_padded
    return chunk

string_pool_chunk = build_string_pool(strings)
print(f"string pool size {len(string_pool_chunk)}")

# Helper to build resource map chunk (optional)
# For simplicity we will include resource map with ids for android attrs
# Resource ids we know:
res_ids = [
    0x0101021b,  # versionCode
    0x0101021c,  # versionName
    0x0101020c,  # minSdk
    0x01010270,  # targetSdk
    0x01010001,  # label
    0x01010003,  # name (activity, action, category)
    0x01010010,  # exported (guess)
]
# Actually resource map should map string indices to resource ids? But spec: resource map is array of resource ids, in order of string pool? Or in order of appearance?
# Simpler: we can omit resource map for now, but we will create one with ids for all strings that are android attrs?
# Let's try to create resource map containing ids for each string that is an android attr name? For minimal, we can skip.

def build_res_map_chunk(res_ids):
    # type 0x0180, header 8
    chunk_size = 8 + len(res_ids)*4
    chunk = struct.pack('<HHI', 0x0180, 8, chunk_size)
    for rid in res_ids:
        chunk += struct.pack('<I', rid)
    return chunk

# We will include res map with the 6 ids
res_map_chunk = build_res_map_chunk(res_ids)
print(f"res map size {len(res_map_chunk)}")

# Helpers for namespace chunks
def build_start_namespace(prefix_idx, uri_idx):
    # type 0x0100, header 16, size 24, line 0, comment -1, prefix, uri
    typ = 0x0100
    hs = 16
    sz = 24
    line = 0
    comment = 0xFFFFFFFF
    chunk = struct.pack('<HHI', typ, hs, sz)
    chunk += struct.pack('<II', line, comment)
    chunk += struct.pack('<II', prefix_idx, uri_idx)
    return chunk

def build_end_namespace(prefix_idx, uri_idx):
    typ = 0x0101
    hs = 16
    sz = 24
    line = 0
    comment = 0xFFFFFFFF
    chunk = struct.pack('<HHI', typ, hs, sz)
    chunk += struct.pack('<II', line, comment)
    chunk += struct.pack('<II', prefix_idx, uri_idx)
    return chunk

def build_start_element(ns_idx, name_idx, attrs, line=0):
    # attrs: list of dict {ns, name, raw, dtype, data}
    # ns = string idx or 0xFFFFFFFF
    # name = string idx
    # raw = string idx or 0xFFFFFFFF
    # dtype, data
    # type 0x0102, header 16
    # after header, payload: ns, name, attrStart, attrSize, attrCount, idIndex, classIndex, styleIndex
    # attrStart=20, attrSize=20
    typ = 0x0102
    hs = 16
    attr_count = len(attrs)
    sz = 16 + 20 + attr_count*20
    chunk = struct.pack('<HHI', typ, hs, sz)
    chunk += struct.pack('<II', line, 0xFFFFFFFF)  # line, comment
    # ns, name
    chunk += struct.pack('<I', ns_idx if ns_idx is not None else 0xFFFFFFFF)
    chunk += struct.pack('<I', name_idx)
    # attrStart, attrSize, attrCount, idIndex, classIndex, styleIndex
    # idIndex, classIndex, styleIndex typically 0
    chunk += struct.pack('<HHHHHH', 20, 20, attr_count, 0, 0, 0)
    # attrs
    for attr in attrs:
        ns_a = attr['ns']
        name_a = attr['name']
        raw_a = attr['raw']
        dtype = attr['dtype']
        data_a = attr['data']
        # ns
        if ns_a is None:
            ns_val = 0xFFFFFFFF
        else:
            ns_val = ns_a
        # name
        name_val = name_a
        # raw
        if raw_a is None:
            raw_val = 0xFFFFFFFF
        else:
            raw_val = raw_a
        chunk += struct.pack('<III', ns_val, name_val, raw_val)
        # typed value: size 8, res0 0, dtype, data
        chunk += struct.pack('<HBBI', 8, 0, dtype, data_a)
    return chunk

def build_end_element(ns_idx, name_idx, line=0):
    typ = 0x0103
    hs = 16
    sz = 24
    chunk = struct.pack('<HHI', typ, hs, sz)
    chunk += struct.pack('<II', line, 0xFFFFFFFF)
    chunk += struct.pack('<II', ns_idx if ns_idx is not None else 0xFFFFFFFF, name_idx)
    return chunk

# Now build manifest chunks

# Strings indices
def idx(s): return str_idx[s]

# Namespace uri and prefix
android_uri_idx = idx("http://schemas.android.com/apk/res/android")
android_prefix_idx = idx("android")

chunks = []

# Header: type 0x0003, header_size 8, file_size placeholder
# We'll build all chunks then prepend header and append with total size

# String pool
chunks.append(string_pool_chunk)
# Optional res map
chunks.append(res_map_chunk)

# Start namespace android
chunks.append(build_start_namespace(android_prefix_idx, android_uri_idx))

# Manifest element
# Attributes:
# package (no ns)
# versionCode (android ns)
# versionName (android ns)
manifest_attrs = [
    {"ns": None, "name": idx("package"), "raw": idx("com.drkmuqeet.app"), "dtype": 0x03, "data": idx("com.drkmuqeet.app")},
    {"ns": android_uri_idx, "name": idx("versionCode"), "raw": None, "dtype": 0x10, "data": 1},
    {"ns": android_uri_idx, "name": idx("versionName"), "raw": idx("1.0"), "dtype": 0x03, "data": idx("1.0")},
]
chunks.append(build_start_element(0xFFFFFFFF, idx("manifest"), manifest_attrs))

# uses-sdk element - use target 28 to avoid exported requirement but include exported anyway
uses_sdk_attrs = [
    {"ns": android_uri_idx, "name": idx("minSdkVersion"), "raw": None, "dtype": 0x10, "data": 21},
    {"ns": android_uri_idx, "name": idx("targetSdkVersion"), "raw": None, "dtype": 0x10, "data": 28},
]
chunks.append(build_start_element(0xFFFFFFFF, idx("uses-sdk"), uses_sdk_attrs))
chunks.append(build_end_element(0xFFFFFFFF, idx("uses-sdk")))

# application element
app_attrs = [
    {"ns": android_uri_idx, "name": idx("label"), "raw": idx("DRK"), "dtype": 0x03, "data": idx("DRK")},
]
chunks.append(build_start_element(0xFFFFFFFF, idx("application"), app_attrs))

# activity element with exported true
activity_attrs = [
    {"ns": android_uri_idx, "name": idx("name"), "raw": idx(".MainActivity"), "dtype": 0x03, "data": idx(".MainActivity")},
    {"ns": android_uri_idx, "name": idx("exported"), "raw": None, "dtype": 0x12, "data": 0xFFFFFFFF},
]
chunks.append(build_start_element(0xFFFFFFFF, idx("activity"), activity_attrs))

# intent-filter
chunks.append(build_start_element(0xFFFFFFFF, idx("intent-filter"), []))

# action
action_attrs = [
    {"ns": android_uri_idx, "name": idx("name"), "raw": idx("android.intent.action.MAIN"), "dtype": 0x03, "data": idx("android.intent.action.MAIN")},
]
chunks.append(build_start_element(0xFFFFFFFF, idx("action"), action_attrs))
chunks.append(build_end_element(0xFFFFFFFF, idx("action")))

# category
cat_attrs = [
    {"ns": android_uri_idx, "name": idx("name"), "raw": idx("android.intent.category.LAUNCHER"), "dtype": 0x03, "data": idx("android.intent.category.LAUNCHER")},
]
chunks.append(build_start_element(0xFFFFFFFF, idx("category"), cat_attrs))
chunks.append(build_end_element(0xFFFFFFFF, idx("category")))

# end intent-filter
chunks.append(build_end_element(0xFFFFFFFF, idx("intent-filter")))
# end activity
chunks.append(build_end_element(0xFFFFFFFF, idx("activity")))
# end application
chunks.append(build_end_element(0xFFFFFFFF, idx("application")))
# end manifest
chunks.append(build_end_element(0xFFFFFFFF, idx("manifest")))

# end namespace
chunks.append(build_end_namespace(android_prefix_idx, android_uri_idx))

# Now assemble file with header type 0x0003
# header: type 0x0003, header_size 8, file_size = 8 + sum(len(c) for c in chunks)
total_size = 8 + sum(len(c) for c in chunks)
header = struct.pack('<HHI', 0x0003, 8, total_size)

full = header + b''.join(chunks)
print(f"total size {total_size} actual {len(full)}")
with open('/home/user/DRK/apk_builder/AndroidManifest.xml', 'wb') as f:
    f.write(full)
print("Wrote AndroidManifest.xml binary")
