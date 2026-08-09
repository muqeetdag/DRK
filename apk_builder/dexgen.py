#!/usr/bin/env python3
"""
Minimal DEX generator for DRK app - WebView to 127.0.0.1:8080
Generates classes.dex without Java toolchain
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
    # s is python str, encode as utf8, with uleb128 length (utf16 length? but for ascii same)
    # Dalvik uses mutf8, but ascii ok
    # length is utf16 length? For ascii, same as byte length
    utf8 = s.encode('utf-8')
    # length in utf16 code units
    # for simplicity, len(s) for ascii; but for general, use len(s) (since ascii)
    length = len(s)  # for ascii this matches
    # Actually need to count utf16 length, for ascii same
    return uleb128_encode(length) + utf8 + b'\x00'

def align(offset, alignment):
    return (offset + alignment - 1) & ~(alignment - 1)

def encode_10x(op):
    return struct.pack('<H', op)

def encode_11n(op, A, B):
    # B 4-bit signed
    B_enc = B & 0xF
    high = (B_enc << 4) | (A & 0xF)
    return struct.pack('<H', (high << 8) | op)

def encode_11x(op, AA):
    return struct.pack('<H', (AA << 8) | op)

def encode_21c(op, AA, BBBB):
    return struct.pack('<HH', (AA << 8) | op, BBBB)

def encode_35c(op, A, method_idx, regs):
    # regs list length
    assert len(regs) == A or (A==0 and len(regs)==0)
    # For less than 5 args, G=0
    C = regs[0] if A>0 else 0
    D = regs[1] if A>1 else 0
    E = regs[2] if A>2 else 0
    F = regs[3] if A>3 else 0
    G = regs[4] if A>4 else 0
    AG = (A << 4) | (G & 0xF) if A<=5 else (A <<4) # but A max 5 in this format
    # Actually spec says AG byte: high nibble A, low nibble G
    # So if A=5, G is 5th reg, else G=0
    if A < 5:
        G = 0
        AG = (A << 4) | 0
    else:
        AG = (A << 4) | (G & 0xF)
    unit0 = (AG << 8) | op
    unit2 = (F << 12) | (E << 8) | (D << 4) | C
    return struct.pack('<HHH', unit0, method_idx, unit2)

# Define strings
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

# Map string name to idx
str_idx = {s:i for i,s in enumerate(strings)}

# Type descriptors - these are types
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
type_idx = {s:i for i,s in enumerate(type_descriptors)}

# Proto definitions: list of dict
# Each proto: shorty_str, return_type_str, params list of type strs
protos = [
    {"shorty": "V", "ret": "V", "params": []},  #0 ()V
    {"shorty": "VL", "ret": "V", "params": ["Landroid/content/Context;"]}, #1 (Context)V
    {"shorty": "VL", "ret": "V", "params": ["Landroid/os/Bundle;"]}, #2 (Bundle)V
    {"shorty": "VL", "ret": "V", "params": ["Landroid/view/View;"]}, #3 (View)V
    {"shorty": "L", "ret": "Landroid/webkit/WebSettings;", "params": []}, #4 ()WebSettings
    {"shorty": "VZ", "ret": "V", "params": ["Z"]}, #5 (Z)V
    {"shorty": "VL", "ret": "V", "params": ["Ljava/lang/String;"]}, #6 (String)V
]

# Build type_lists data for protos with params
# type_list format: uint32 size, then size * uint16 type_idx
type_lists = []  # list of (proto_index, byte_data, offset_placeholder)
type_list_datas = []
# We'll generate unique type_list blobs
# Need to dedup: map tuple(params) -> offset
type_list_map = {}
type_list_bytes = {}
for pi, proto in enumerate(protos):
    if not proto["params"]:
        continue
    key = tuple(proto["params"])
    if key not in type_list_map:
        # build byte
        size = len(proto["params"])
        data = struct.pack('<I', size)
        for t in proto["params"]:
            data += struct.pack('<H', type_idx[t])
        # pad to 4 bytes? type_list should be 4-byte aligned, but size of list is 4+2*size, may need padding to 4
        # Pad with 0 if odd size
        if len(data) % 4 != 0:
            data += b'\x00' * (4 - len(data) % 4)
        type_list_map[key] = data
        type_list_bytes[pi] = data
    else:
        type_list_bytes[pi] = type_list_map[key]

# Method definitions
methods = [
    {"class": "Landroid/app/Activity;", "proto": 0, "name": "<init>"}, #0 Activity.<init>
    {"class": "Lcom/drkmuqeet/app/MainActivity;", "proto": 0, "name": "<init>"}, #1 Main.<init>
    {"class": "Lcom/drkmuqeet/app/MainActivity;", "proto": 2, "name": "onCreate"}, #2 Main.onCreate
    {"class": "Landroid/webkit/WebView;", "proto": 1, "name": "<init>"}, #3 WebView.<init>
    {"class": "Landroid/app/Activity;", "proto": 3, "name": "setContentView"}, #4 setContentView
    {"class": "Landroid/webkit/WebView;", "proto": 4, "name": "getSettings"}, #5 getSettings
    {"class": "Landroid/webkit/WebSettings;", "proto": 5, "name": "setJavaScriptEnabled"}, #6 setJavaScriptEnabled
    {"class": "Landroid/webkit/WebView;", "proto": 6, "name": "loadUrl"}, #7 loadUrl
]

# Pre-calc indices for quick
def get_string_idx(s): return str_idx[s]
def get_type_idx(s): return type_idx[s]

# Build string_data and keep offsets
# We'll need to build data section incrementally

# Header size 112
HEADER_SIZE = 112

# We'll have sections in order: string_ids, type_ids, proto_ids, field_ids (0), method_ids, class_defs, then data

string_ids_size = len(strings)
type_ids_size = len(type_descriptors)
proto_ids_size = len(protos)
field_ids_size = 0
method_ids_size = len(methods)
class_defs_size = 1

# Offsets calculation
offset = HEADER_SIZE
string_ids_off = offset
offset += string_ids_size * 4

type_ids_off = offset
offset += type_ids_size * 4

proto_ids_off = offset
offset += proto_ids_size * 12  # each proto 12 bytes

field_ids_off = 0  # no fields, offset 0

method_ids_off = offset
offset += method_ids_size * 8

class_defs_off = offset
offset += class_defs_size * 32

data_off = offset
# data will contain string_data, type_lists, class_data, code_items, map_list
# We will build data bytearray and track offsets

data = bytearray()
# Keep track of string_data offsets relative to file start
string_data_offsets = []

# string_data
for s in strings:
    # align data? string_data doesn't need specific alignment, but we keep 1
    off = data_off + len(data)
    string_data_offsets.append(off)
    data.extend(encode_string_data(s))

# type_lists: need to keep map from proto index to offset
# Also need to dedup type_lists as before
# We already have unique type_list blobs, but we need to assign offsets for each unique
# For simplicity, assign offset for each unique blob then reuse for protos that share same params
unique_type_lists = {}  # key -> offset
type_list_proto_off = {}  # proto idx -> offset
for pi, proto in enumerate(protos):
    if not proto["params"]:
        type_list_proto_off[pi] = 0
        continue
    key = tuple(proto["params"])
    if key not in unique_type_lists:
        # align to 4
        # pad data to 4 alignment
        pad = (4 - (len(data) % 4)) % 4
        if pad:
            data.extend(b'\x00'*pad)
        off = data_off + len(data)
        unique_type_lists[key] = off
        # data for this key is type_list_map[key]
        data.extend(type_list_map[key])
        type_list_proto_off[pi] = off
    else:
        type_list_proto_off[pi] = unique_type_lists[key]

# Now class_data
# Need to create class_data for MainActivity
# class_data format: uleb128 fields: static_fields_size, instance_fields_size, direct_methods_size, virtual_methods_size
# Then for each direct method: method_idx_diff, access_flags, code_off
# virtual similarly

# method indices in order: we have 8 methods, but for class_data we need only methods belonging to MainActivity
# Direct: method 1 (<init>)
# Virtual: method 2 (onCreate)
# For diff, we need to sort by method idx within direct and virtual separately
# direct list sorted: only [1] => diff 1
# virtual list sorted: only [2] => diff 2

# We need code_offs - we don't know yet, we will generate code items after class_data placeholder, then fill

# Let's create placeholder for class_data and code items with offsets

# We'll pre-generate code items bytearrays

# Code for MainActivity.<init>
# registers_size =1, ins_size=1, outs_size=1, tries_size=0, debug_info_off=0, insns_size
# Bytecode: invoke-direct {p0} Activity.<init> ; return-void
# p0 is v0 when registers_size=1 ins_size=1

code_constructor_insns = b''
# invoke-direct {v0}, method 0 (Activity.<init>)
code_constructor_insns += encode_35c(0x70, 1, 0, [0])
code_constructor_insns += encode_10x(0x0e)  # return-void

# Build code_item for constructor
# registers_size 1, ins_size 1, outs_size 1, tries 0, debug_off 0, insns_size = len(insns)/2
def build_code_item(reg_size, ins_size, outs_size, insns_bytes):
    insns_size = len(insns_bytes)//2
    header = struct.pack('<HHHHI', reg_size, ins_size, outs_size, 0, 0)  # tries_size=0, debug=0
    header += struct.pack('<I', insns_size)
    # insns must be 4-byte aligned? code_item itself is 4-byte aligned, insns array follows
    # Header is 16 bytes
    code_item = header + insns_bytes
    # Pad to 4 bytes if needed? But insns_size already in 16-bit units, total size even
    # Ensure code_item size multiple of 4? For simplicity pad
    if len(code_item) % 4 != 0:
        code_item += b'\x00' * (4 - len(code_item)%4)
    return code_item

code1 = build_code_item(1, 1, 1, code_constructor_insns)

# Code for onCreate
# registers_size=6, ins_size=2, outs_size=3 (max args for invoke with 2 args + maybe)
# p0=v4, p1=v5 as earlier

insns = bytearray()
# 1. invoke-super {v4, v5}, method 0? Wait Activity.onCreate is not method 0? Activity.onCreate would be different from Activity.<init>
# Actually we need method id for Activity.onCreate(Bundle)... we don't have it yet!
# We have Activity.<init> as 0, but Activity.onCreate is missing!
# We need to add Activity.onCreate method id

# Let's fix methods list - we need Activity.onCreate as well

# To avoid breaking, we will rebuild methods later - but for now we have only Activity.<init>
# So we need to add method for Activity.onCreate

# We'll abort this file generation and redo with corrected methods list in next step

print("This script is incomplete - need to fix method list")
