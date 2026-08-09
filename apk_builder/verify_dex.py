import struct, hashlib, zlib

with open('/home/user/DRK/apk_builder/classes.dex','rb') as f:
    data=f.read()

print(f"Size {len(data)}")
magic=data[:8]
print("magic", magic)
checksum=struct.unpack_from('<I', data, 8)[0]
sig=data[12:32].hex()
print("checksum", hex(checksum), "sig", sig)
# recompute adler
adler=zlib.adler32(data[12:]) & 0xffffffff
print("computed adler", hex(adler), "match", adler==checksum)
sha=hashlib.sha1(data[32:]).digest().hex()
print("computed sha", sha, "match", sha==data[12:32].hex())

header_size=struct.unpack_from('<I', data, 36)[0]
print("header_size", header_size)
map_off=struct.unpack_from('<I', data, 52)[0]
str_size=struct.unpack_from('<I', data, 56)[0]
str_off=struct.unpack_from('<I', data, 60)[0]
type_size=struct.unpack_from('<I', data, 64)[0]
type_off=struct.unpack_from('<I', data, 68)[0]
proto_size=struct.unpack_from('<I', data, 72)[0]
proto_off=struct.unpack_from('<I', data, 76)[0]
method_size=struct.unpack_from('<I', data, 88)[0]
method_off=struct.unpack_from('<I', data, 92)[0]
class_size=struct.unpack_from('<I', data, 96)[0]
class_off=struct.unpack_from('<I', data, 100)[0]
data_size=struct.unpack_from('<I', data, 104)[0]
data_off=struct.unpack_from('<I', data, 108)[0]
print(f"str {str_size}@{str_off} type {type_size}@{type_off} proto {proto_size}@{proto_off} method {method_size}@{method_off} class {class_size}@{class_off} data {data_size}@{data_off} map@{map_off}")

# read string_ids
for i in range(str_size):
    off=struct.unpack_from('<I', data, str_off+i*4)[0]
    # read uleb128 length
    idx=off
    length=0
    shift=0
    while True:
        b=data[idx]
        idx+=1
        length |= (b & 0x7f) << shift
        shift+=7
        if not (b & 0x80):
            break
    s=data[idx:idx+length].decode()
    # print
    if i<20:
        print(f"str {i} off {off} len {length} = {s!r}")

# type_ids
for i in range(type_size):
    s_idx=struct.unpack_from('<I', data, type_off+i*4)[0]
    print(f"type {i} -> str {s_idx} = { [data[str_off+s_idx*4] ] } = {s_idx}")

# proto
for i in range(proto_size):
    base=proto_off+i*12
    shorty, ret, param_off=struct.unpack_from('<III', data, base)
    print(f"proto {i} shorty str {shorty} ret type {ret} paramoff {param_off}")

# methods
for i in range(method_size):
    base=method_off+i*8
    class_idx, proto_idx, name_idx=struct.unpack_from('<HHI', data, base)
    print(f"method {i} class {class_idx} proto {proto_idx} name {name_idx}")

# class def
for i in range(class_size):
    base=class_off+i*32
    vals=struct.unpack_from('<IIIIIIII', data, base)
    print(f"class_def {i} {vals}")

# map
map_size=struct.unpack_from('<I', data, map_off)[0]
print(f"map size {map_size}")
for j in range(map_size):
    base=map_off+4+j*12
    type_code, unused, size, off=struct.unpack_from('<HHII', data, base)
    print(f"map {j} type {hex(type_code)} size {size} off {off}")

# try to read class_data and code
# Find class_data off
class_data_off = struct.unpack_from('<IIIIIIII', data, class_off)[6]
print(f"class_data_off {class_data_off}")
# decode uleb
def read_uleb(d, offset):
    result=0
    shift=0
    pos=offset
    while True:
        b=d[pos]
        pos+=1
        result|=(b & 0x7f) << shift
        shift+=7
        if not (b & 0x80):
            break
    return result, pos

off=class_data_off
for name in ["static","instance","direct","virtual"]:
    val, off = read_uleb(data, off)
    print(f"{name} size {val} next off {off}")
# Now direct method
for i in range(1):
    mid_diff, off = read_uleb(data, off)
    acc, off = read_uleb(data, off)
    code_off, off = read_uleb(data, off)
    print(f"direct {i} diff {mid_diff} acc {hex(acc)} code_off {code_off}")
for i in range(1):
    mid_diff, off = read_uleb(data, off)
    acc, off = read_uleb(data, off)
    code_off, off = read_uleb(data, off)
    print(f"virtual {i} diff {mid_diff} acc {hex(acc)} code_off {code_off}")

# read code items
for co in [code_off for _ in range(2)]:
    # Actually we have two code offs from above - need to capture
    pass
