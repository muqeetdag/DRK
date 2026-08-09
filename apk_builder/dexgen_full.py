#!/usr/bin/env python3
"""
Full minimal DEX generator for DRK WebView app
"""
import struct
import hashlib
import zlib

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

def encode_string_data(s):
    # utf16 length = len(s) for ascii; for simplicity use len as utf16 count
    # For non-ascii, python len may differ, but our strings are ascii except maybe?
    # All ascii
    utf8 = s.encode('utf-8')
    length = len(s)  # ascii
    return uleb128_encode(length) + utf8 + b'\x00'

def encode_10x(op):
    return struct.pack('<H', op)

def encode_11n(op, A, B):
    B_enc = B & 0xF
    high = (B_enc << 4) | (A & 0xF)
    return struct.pack('<H', (high << 8) | op)

def encode_11x(op, AA):
    return struct.pack('<H', (AA << 8) | op)

def encode_21c(op, AA, BBBB):
    return struct.pack('<HH', (AA << 8) | op, BBBB)

def encode_35c(op, A, method_idx, regs):
    # regs list
    C = regs[0] if A>0 else 0
    D = regs[1] if A>1 else 0
    E = regs[2] if A>2 else 0
    F = regs[3] if A>3 else 0
    G = regs[4] if A>4 else 0
    if A < 5:
        AG = (A << 4) | 0
    else:
        AG = (A << 4) | (G & 0xF)
    unit0 = (AG << 8) | op
    unit2 = (F << 12) | (E << 8) | (D << 4) | C
    return struct.pack('<HHH', unit0, method_idx, unit2)

def build_code_item(reg_size, ins_size, outs_size, insns_bytes):
    insns_size = len(insns_bytes)//2
    header = struct.pack('<HHHHII', reg_size, ins_size, outs_size, 0, 0, insns_size)
    # Actually header is HHHH I I? spec: registers_size H, ins_size H, outs_size H, tries_size H, debug_info_off I, insns_size I = total 16 bytes
    code_item = header + insns_bytes
    if len(code_item) % 4 != 0:
        code_item += b'\x00' * (4 - len(code_item)%4)
    return code_item

# Strings
strings = [
    "Lcom/drkmuqeet/app/MainActivity;",  #0
    "Landroid/app/Activity;",            #1
    "Landroid/webkit/WebView;",          #2
    "Landroid/webkit/WebSettings;",      #3
    "Landroid/os/Bundle;",               #4
    "Landroid/view/View;",               #5
    "Landroid/content/Context;",         #6
    "Ljava/lang/String;",                #7
    "V",                                 #8 void
    "Z",                                 #9 boolean
    "VL",                                #10 shorty void+object
    "L",                                 #11 shorty object return
    "VZ",                                #12 shorty void+bool
    "<init>",                            #13
    "onCreate",                          #14
    "setContentView",                    #15
    "getSettings",                       #16
    "setJavaScriptEnabled",              #17
    "loadUrl",                           #18
    "http://127.0.0.1:8080",             #19
]

str_idx = {s:i for i,s in enumerate(strings)}

type_descriptors = [
    "Lcom/drkmuqeet/app/MainActivity;", #0
    "Landroid/app/Activity;",            #1
    "Landroid/webkit/WebView;",          #2
    "Landroid/webkit/WebSettings;",      #3
    "Landroid/os/Bundle;",               #4
    "Landroid/view/View;",               #5
    "Landroid/content/Context;",         #6
    "Ljava/lang/String;",                #7
    "V",                                 #8
    "Z",                                 #9
]
type_idx_map = {s:i for i,s in enumerate(type_descriptors)}

protos = [
    {"shorty": "V", "ret": "V", "params": []},  #0 ()V
    {"shorty": "VL", "ret": "V", "params": ["Landroid/content/Context;"]}, #1 (Context)V
    {"shorty": "VL", "ret": "V", "params": ["Landroid/os/Bundle;"]}, #2 (Bundle)V
    {"shorty": "VL", "ret": "V", "params": ["Landroid/view/View;"]}, #3 (View)V
    {"shorty": "L", "ret": "Landroid/webkit/WebSettings;", "params": []}, #4 ()WebSettings
    {"shorty": "VZ", "ret": "V", "params": ["Z"]}, #5 (Z)V
    {"shorty": "VL", "ret": "V", "params": ["Ljava/lang/String;"]}, #6 (String)V
]

# type_lists dedup
type_list_map = {}
type_list_proto_off_placeholder = {}
unique_type_lists_data = {}

for pi, proto in enumerate(protos):
    if not proto["params"]:
        continue
    key = tuple(proto["params"])
    if key not in type_list_map:
        size = len(proto["params"])
        data = struct.pack('<I', size)
        for t in proto["params"]:
            data += struct.pack('<H', type_idx_map[t])
        if len(data) % 4 != 0:
            data += b'\x00' * (4 - len(data)%4)
        type_list_map[key] = data

# Methods: need order as per final layout - they should be sorted? But for simplicity we create in desired order,
# but method_ids are listed in file order; class_data diff relies on method idx order
# Let's list methods in order that makes class_data diffs simple: sorted by method_idx for MainActivity would be easier if they are consecutive
# We'll order methods such that Activity.<init> is 0, Activity.onCreate 1, MainActivity.<init> 2, MainActivity.onCreate 3, rest follow

methods = [
    {"class": "Landroid/app/Activity;", "proto": 0, "name": "<init>"}, #0 Activity.<init>
    {"class": "Landroid/app/Activity;", "proto": 2, "name": "onCreate"}, #1 Activity.onCreate(Bundle)
    {"class": "Lcom/drkmuqeet/app/MainActivity;", "proto": 0, "name": "<init>"}, #2 Main.<init>
    {"class": "Lcom/drkmuqeet/app/MainActivity;", "proto": 2, "name": "onCreate"}, #3 Main.onCreate
    {"class": "Landroid/webkit/WebView;", "proto": 1, "name": "<init>"}, #4 WebView.<init>
    {"class": "Landroid/app/Activity;", "proto": 3, "name": "setContentView"}, #5 setContentView
    {"class": "Landroid/webkit/WebView;", "proto": 4, "name": "getSettings"}, #6 getSettings
    {"class": "Landroid/webkit/WebSettings;", "proto": 5, "name": "setJavaScriptEnabled"}, #7 setJavaScriptEnabled
    {"class": "Landroid/webkit/WebView;", "proto": 6, "name": "loadUrl"}, #8 loadUrl
]

HEADER_SIZE = 112
string_ids_size = len(strings)
type_ids_size = len(type_descriptors)
proto_ids_size = len(protos)
field_ids_size = 0
method_ids_size = len(methods)
class_defs_size = 1

offset = HEADER_SIZE
string_ids_off = offset
offset += string_ids_size * 4

type_ids_off = offset
offset += type_ids_size * 4

proto_ids_off = offset
offset += proto_ids_size * 12

method_ids_off = offset
offset += method_ids_size * 8

class_defs_off = offset
offset += class_defs_size * 32

data_off = offset

data = bytearray()
string_data_offsets = []

# string_data
for s in strings:
    # align to 1? no
    off = data_off + len(data)
    string_data_offsets.append(off)
    data.extend(encode_string_data(s))

# type_lists
# Build unique list in order of appearance
unique_keys_order = []
seen = set()
for pi, proto in enumerate(protos):
    if not proto["params"]:
        continue
    key = tuple(proto["params"])
    if key not in seen:
        seen.add(key)
        unique_keys_order.append(key)

type_list_offsets = {}  # key -> offset
type_list_proto_off = {}

for key in unique_keys_order:
    # align 4
    pad = (4 - (len(data) % 4)) % 4
    if pad:
        data.extend(b'\x00'*pad)
    off = data_off + len(data)
    type_list_offsets[key] = off
    data.extend(type_list_map[key])

# Now map proto idx to offset
for pi, proto in enumerate(protos):
    if not proto["params"]:
        type_list_proto_off[pi] = 0
    else:
        key = tuple(proto["params"])
        type_list_proto_off[pi] = type_list_offsets[key]

# Now code items
# We need to generate code for two methods: method 2 (Main.<init>) and method 3 (Main.onCreate)
# For Main.<init>: registers 1, ins 1, outs 1, invoke-direct {p0} Activity.<init> (method 0), return-void
# p0 = v0 because reg_size 1 ins_size 1

code_constructor_insns = bytearray()
code_constructor_insns += encode_35c(0x70, 1, 0, [0])  # invoke-direct method 0, reg v0
code_constructor_insns += encode_10x(0x0e)  # return-void

code_constructor = build_code_item(1, 1, 1, code_constructor_insns)

# For Main.onCreate: registers_size=6, ins_size=2, outs_size=3, p0=v4, p1=v5
# Sequence:
# invoke-super {v4, v5}, Activity.onCreate (method 1)
# new-instance v0, WebView type_idx 2
# invoke-direct {v0, v4}, WebView.<init> method 4
# invoke-virtual {v4, v0}, setContentView method 5
# invoke-virtual {v0}, getSettings method 6, move-result-object v1
# const/4 v2, 1
# invoke-virtual {v1, v2}, setJavaScriptEnabled method 7
# const-string v3, url string idx 19
# invoke-virtual {v0, v3}, loadUrl method 8
# return-void

insns = bytearray()
# invoke-super: op 0x6f, A=2, method 1, regs [4,5]
insns += encode_35c(0x6f, 2, 1, [4,5])
# new-instance v0, type WebView type_idx 2
insns += encode_21c(0x22, 0, type_idx_map["Landroid/webkit/WebView;"])
# invoke-direct v0,v4 method 4
insns += encode_35c(0x70, 2, 4, [0,4])
# invoke-virtual v4,v0 method 5
insns += encode_35c(0x6e, 2, 5, [4,0])
# invoke-virtual v0 method 6
insns += encode_35c(0x6e, 1, 6, [0])
# move-result-object v1
insns += encode_11x(0x0c, 1)
# const/4 v2, 1
insns += encode_11n(0x12, 2, 1)
# invoke-virtual v1,v2 method 7
insns += encode_35c(0x6e, 2, 7, [1,2])
# const-string v3, url idx 19
insns += encode_21c(0x1a, 3, str_idx["http://127.0.0.1:8080"])
# invoke-virtual v0,v3 method 8
insns += encode_35c(0x6e, 2, 8, [0,3])
# return-void
insns += encode_10x(0x0e)

code_oncreate = build_code_item(6, 2, 3, insns)

print(f"constructor insns len {len(code_constructor_insns)} code item len {len(code_constructor)}")
print(f"oncreate insns len {len(insns)} code item len {len(code_oncreate)}")
print(f"constructor bytes: {code_constructor_insns.hex()}")
print(f"oncreate bytes: {insns.hex()}")

# Now we need to place code items in data section, with 4-byte alignment
# Align data
pad = (4 - (len(data) % 4)) % 4
if pad:
    data.extend(b'\x00'*pad)
code1_off = data_off + len(data)
data.extend(code_constructor)
pad = (4 - (len(data) % 4)) % 4
if pad:
    data.extend(b'\x00'*pad)
code2_off = data_off + len(data)
data.extend(code_oncreate)

# Now class_data
# Format: uleb128 static_fields, instance_fields, direct_methods, virtual_methods
# Then direct methods entries
# For MainActivity class:
# direct_methods_size =1 (constructor)
# virtual_methods_size=1 (onCreate)
# method entries: diff, access_flags, code_off

# Access flags: public =0x0001, constructor =0x10000? Actually ACC_CONSTRUCTOR 0x00010000
# For <init> we need public + constructor = 0x10001? Let's use 0x10001
# onCreate: public =0x0001? But protected? Use public 0x1

# method_idx_diff for direct: method idx 2 -> diff from 0 =2
# For virtual: method idx 3 diff from 0 =3

class_data = bytearray()
class_data += uleb128_encode(0)  # static_fields
class_data += uleb128_encode(0)  # instance_fields
class_data += uleb128_encode(1)  # direct_methods
class_data += uleb128_encode(1)  # virtual_methods

# direct method entry: method_idx_diff =2, access_flags=0x10001, code_off=code1_off
class_data += uleb128_encode(2)
class_data += uleb128_encode(0x10001)
class_data += uleb128_encode(code1_off)

# virtual method entry: method_idx_diff=3, access_flags=0x1 (public), code_off=code2_off
class_data += uleb128_encode(3)
class_data += uleb128_encode(0x0001)
class_data += uleb128_encode(code2_off)

# Now place class_data in data section
pad = (4 - (len(data) % 4)) % 4
if pad:
    data.extend(b'\x00'*pad)
class_data_off = data_off + len(data)
data.extend(class_data)

# Now we have data section with everything except map_list
# Map list: array of map_item: type (2 bytes), unused (2), size (4), offset (4) =12 bytes each
# Map item types: we should include all sections
# Types codes from dex spec:
# 0x0000 header
# 0x0001 string_id
# 0x0002 type_id
# 0x0003 proto_id
# 0x0004 field_id
# 0x0005 method_id
# 0x0006 class_def
# 0x2000 map_list (but map_list itself)
# 0x2001 type_list
# 0x2002 annotation_set_ref etc not needed
# We need: string_data (0x2001? actually string_data is 0x2002? Let's recall map types)
# Actually spec:
# 0x0000 HEADER_ITEM
# 0x0001 STRING_ID_ITEM
# 0x0002 TYPE_ID_ITEM
# 0x0003 PROTO_ID_ITEM
# 0x0004 FIELD_ID_ITEM
# 0x0005 METHOD_ID_ITEM
# 0x0006 CLASS_DEF_ITEM
# 0x1000 MAP_LIST
# 0x1001 TYPE_LIST
# 0x1002 ANNOTATION_SET_REF_LIST etc
# 0x1003 ANNOTATION_SET_ITEM
# 0x2000 CLASS_DATA_ITEM
# 0x2001 CODE_ITEM
# 0x2002 STRING_DATA_ITEM
# Might be slightly off, but we need check dex spec for map item type codes

# From spec:
# TYPE_MAP_LIST = 0x1000
# TYPE_TYPE_LIST = 0x1001
# ...
# TYPE_ANNOTATION_SET_REF_LIST =0x1002
# TYPE_ANNOTATION_SET_ITEM=0x1003
# TYPE_CLASS_DATA_ITEM=0x2000
# TYPE_CODE_ITEM=0x2001
# TYPE_STRING_DATA_ITEM=0x2002
# TYPE_DEBUG_INFO_ITEM=0x2003
# TYPE_ANNOTATION_ITEM=0x2004
# TYPE_ENCODED_ARRAY_ITEM=0x2005
# TYPE_ANNOTATIONS_DIRECTORY_ITEM=0x2006

# For our file, we have: header, string_id, type_id, proto_id, method_id, class_def, string_data, type_list, class_data, code

# Let's create map_items list
map_items = []

def add_map_item(type_code, size, offset):
    map_items.append((type_code, size, offset))

add_map_item(0x0000, 1, 0)  # header
add_map_item(0x0001, string_ids_size, string_ids_off)
add_map_item(0x0002, type_ids_size, type_ids_off)
add_map_item(0x0003, proto_ids_size, proto_ids_off)
add_map_item(0x0005, method_ids_size, method_ids_off)
add_map_item(0x0006, class_defs_size, class_defs_off)
add_map_item(0x2002, string_ids_size, data_off)  # string_data - but offset is data_off first string, size = number of strings, but we need actually offset of first string_data item is data_off, but we have multiple string_data. The map for string_data should be one entry covering all? Actually typical dex map has one entry for string_data with size = number of strings and offset = offset of first string_data. So using data_off as offset should work if first string_data at data_off (which it is)
# For our layout, string_data starts at data_off, yes
# We also need to add map for type_list, class_data, code

# type_list: we have 3? Actually unique type lists =3? Let's calculate: keys are (Context), (Bundle), (View), (Z), (String) => 5 unique
add_map_item(0x1001, len(unique_keys_order), type_list_offsets[unique_keys_order[0]] if unique_keys_order else 0)
# But we have multiple type_lists at different offsets; map expects size = number of type_lists? Might need to count each list as separate? In original spec, each type_list is separate but map entry can group them if they are contiguous? Our type_lists are scattered with string_data? No they are after string_data and contiguous? We placed them sequentially with possible padding, so they are not necessarily contiguous? Actually they are sequential in data after string_data, each aligned. So they form a contiguous region from first type_list offset to last type_list end. For map we could give offset of first type_list and size = number of type_lists
# Let's use first offset
if unique_keys_order:
    first_type_list_off = type_list_offsets[unique_keys_order[0]]
else:
    first_type_list_off = 0

# class_data
add_map_item(0x2000, 1, class_data_off)
# code
add_map_item(0x2001, 2, code1_off)  # 2 code items starting at code1_off? but they are not contiguous due to padding? They are nearly contiguous: code1 and code2 separated by padding but we can have two separate map entries? Simplify: we have 2 code items, but we give first offset and size 2, expecting they are not necessarily contiguous but dex tools allow map to list each? Usually map has separate entries for each code item? Actually spec says code items are separate and map should list number of code items and offset of first. The offset of first code item and size 2 implies second is after first, which is true (contiguous with padding). So okay.

# Now build map_list binary
# map_list header: size (uint32) = number of items, then each map_item 12 bytes
map_size = len(map_items) + 1  # +1 for map_list itself?
# Actually we need to include map_list item itself in map
# So total items = len(map_items)+1 (for map_list)
# We'll build map_list after we know its own offset

# First we need to place map_list in data section
pad = (4 - (len(data) % 4)) % 4
if pad:
    data.extend(b'\x00'*pad)
map_off = data_off + len(data)

# Build map_list content temporarily to calculate size
# We'll include map_list entry last
all_map_items = map_items.copy()
all_map_items.append((0x1000, 1, map_off))  # map_list itself

map_list_bin = struct.pack('<I', len(all_map_items))
for type_code, size, off in all_map_items:
    map_list_bin += struct.pack('<HHII', type_code, 0, size, off)

# Append map_list_bin to data
data.extend(map_list_bin)

# Now we have all sections
# Build header and other sections

# Calculate file size
file_size = data_off + len(data)

# Now build bytearray for whole file
out = bytearray(file_size)

# Header: 112 bytes
# magic
out[0:8] = b'dex\n035\x00'
# checksum placeholder 4 bytes at 8
# signature placeholder 20 bytes at 12
# file_size at 32
struct.pack_into('<I', out, 32, file_size)
# header_size at 36
struct.pack_into('<I', out, 36, HEADER_SIZE)
# endian tag at 40: 0x12345678
struct.pack_into('<I', out, 40, 0x12345678)
# link_size 0, link_off 0 at 44,48
# map_off at 52
struct.pack_into('<I', out, 52, map_off)
# string_ids_size at 56
struct.pack_into('<I', out, 56, string_ids_size)
struct.pack_into('<I', out, 60, string_ids_off)
# type_ids_size at 64
struct.pack_into('<I', out, 64, type_ids_size)
struct.pack_into('<I', out, 68, type_ids_off)
# proto_ids_size at 72
struct.pack_into('<I', out, 72, proto_ids_size)
struct.pack_into('<I', out, 76, proto_ids_off)
# field_ids at 80,84
struct.pack_into('<I', out, 80, field_ids_size)
struct.pack_into('<I', out, 84, 0)
# method_ids at 88,92
struct.pack_into('<I', out, 88, method_ids_size)
struct.pack_into('<I', out, 92, method_ids_off)
# class_defs at 96,100
struct.pack_into('<I', out, 96, class_defs_size)
struct.pack_into('<I', out, 100, class_defs_off)
# data_size at 104
struct.pack_into('<I', out, 104, len(data))
struct.pack_into('<I', out, 108, data_off)

# Now fill string_ids
for i, off in enumerate(string_data_offsets):
    struct.pack_into('<I', out, string_ids_off + i*4, off)

# type_ids: each entry is string idx (uint32)
for i, td in enumerate(type_descriptors):
    s_idx = str_idx[td]
    struct.pack_into('<I', out, type_ids_off + i*4, s_idx)

# proto_ids: 12 bytes each: shorty_idx, return_type_idx, parameters_off
for pi, proto in enumerate(protos):
    shorty_idx = str_idx[proto["shorty"]]
    ret_type_idx = type_idx_map[proto["ret"]]
    param_off = type_list_proto_off[pi]
    base = proto_ids_off + pi*12
    struct.pack_into('<III', out, base, shorty_idx, ret_type_idx, param_off)

# method_ids: class_idx (H), proto_idx (H), name_idx (I)
for mi, m in enumerate(methods):
    class_idx = type_idx_map[m["class"]]
    proto_idx = m["proto"]
    name_idx = str_idx[m["name"]]
    base = method_ids_off + mi*8
    struct.pack_into('<HHI', out, base, class_idx, proto_idx, name_idx)

# class_defs: 32 bytes
# class_idx, access_flags, superclass_idx, interfaces_off, source_file_idx, annotations_off, class_data_off, static_values_off
# For MainActivity: class_idx 0, access public 1, superclass 1 (Activity), interfaces 0, source_file 0xffffffff? or 0? use 0xFFFFFFFF for no source? Use NO_INDEX = 0xFFFFFFFF -> but as uint32 0xFFFFFFFF.
# For simplicity use 0xFFFFFFFF for source_file idx meaning none? Actually spec says NO_INDEX = 0xFFFFFFFF for that field. We'll use 0xFFFFFFFF.
# annotations_off 0, class_data_off, static_values_off 0

# Access flags public = 0x1
class_idx = type_idx_map["Lcom/drkmuqeet/app/MainActivity;"]
super_idx = type_idx_map["Landroid/app/Activity;"]
base = class_defs_off
struct.pack_into('<IIIIIIII', out, base,
                 class_idx,
                 0x1,  # access
                 super_idx,
                 0,  # interfaces_off
                 0xFFFFFFFF,  # source_file_idx = NO_INDEX
                 0,  # annotations_off
                 class_data_off,
                 0)  # static_values_off

# Now data
out[data_off:] = data

# Now compute signature: SHA1 of file excluding magic, checksum and signature (i.e., from offset 32 onwards)
# According to spec, signature is SHA1 of rest of file after 32 bytes
sha1 = hashlib.sha1(out[32:]).digest()
out[12:32] = sha1

# checksum: adler32 of file excluding magic and checksum (from 12 onwards)
adler = zlib.adler32(out[12:]) & 0xffffffff
struct.pack_into('<I', out, 8, adler)

# Write to file
with open('/home/user/DRK/apk_builder/classes.dex', 'wb') as f:
    f.write(out)

print(f"DEX generated: {len(out)} bytes")
print(f"map_off {map_off}, data_off {data_off}, file_size {file_size}")
print("strings offsets", string_data_offsets[:5])
