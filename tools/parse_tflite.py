"""Minimal TFLite flatbuffer inspector (no external deps).

Walks root -> SubGraphs -> SubGraph[0] printing tensors, operators, buffers,
metadata, and signature defs. Field ids follow tensorflow/lite/schema/schema.fbs.
"""
import struct
import sys

PATH = sys.argv[1] if len(sys.argv) > 1 else r"D:\projects\driver-drowsiness detection\eye_model.tflite"
buf = open(PATH, 'rb').read()
N = len(buf)
print("file size:", N)
print("identifier:", buf[4:8])

u16 = lambda o: struct.unpack_from('<H', buf, o)[0]
u32 = lambda o: struct.unpack_from('<I', buf, o)[0]
i32 = lambda o: struct.unpack_from('<i', buf, o)[0]
f32 = lambda o: struct.unpack_from('<f', buf, o)[0]


def slot(t, fid):
    vt = t - i32(t)
    if not (0 <= vt <= N - 4):
        raise ValueError(f"bad vtable at {vt}")
    vsize = u16(vt + 2)
    nf = (vsize - 4) // 2
    if fid >= nf:
        return None
    o = u16(vt + 4 + 2 * fid)
    return None if o == 0 else t + o


def val(t, fid, default=None):
    s = slot(t, fid)
    return default if s is None else u32(s)


def val8(t, fid, default=None):
    s = slot(t, fid)
    return default if s is None else buf[s]


def ind(s):
    return s + u32(s)


def tab(t, fid):
    s = slot(t, fid)
    return None if s is None else ind(s)


def vec(t, fid):
    s = slot(t, fid)
    if s is None:
        return 0, None
    v = ind(s)
    return u32(v), v + 4


def elem(vfirst, i):
    e = vfirst + 4 * i
    return e + u32(e)


root = ind(0)

sg_count, sg0 = vec(root, 2)
print("subgraph count:", sg_count)
sg = elem(sg0, 0)

n_tensors, tvec = vec(sg, 0)
print("tensors:", n_tensors)
for i in range(n_tensors):
    t = elem(tvec, i)
    sv, sd = vec(t, 0)
    shape = [u32(sd + 4 * j) for j in range(sv)]
    ty = val8(t, 1, 0)
    bidx = val(t, 2, 0)
    nv, nd = vec(t, 3)
    name = buf[nd:nd + nv].decode('utf-8', 'replace') if nv else ''
    qtab = tab(t, 4)
    qinfo = None
    if qtab is not None:
        qn, qd = vec(qtab, 0)
        if qn == 1:
            qt = ind(qd)
            qinfo = (round(f32(qt), 6), i32(qt + 4))
        elif qn > 1:
            qinfo = f"per-axis[{qn}]"
    tname = {0: 'fp32', 1: 'fp16', 2: 'i32', 3: 'u8', 4: 'i64', 6: 'bool', 7: 'i16', 8: 'i8'}.get(ty, f't{ty}')
    print(f"  [{i:2}] {name!r:44} shape={shape} {tname} buf={bidx} q={qinfo}")

n_codes, cvec = vec(root, 1)
opcodes = []
for i in range(n_codes):
    ct = elem(cvec, i)
    dep = val8(ct, 0, 0)
    full = val(ct, 3, 0)
    opcodes.append(full if dep == 127 else dep)
BUILTIN = {0: 'ADD', 1: 'AVERAGE_POOL_2D', 2: 'DEPTHWISE_CONV_2D', 3: 'RESHAPE', 9: 'FULLY_CONNECTED',
           10: 'SOFTMAX', 11: 'TANH', 13: 'RELU6', 17: 'CONV_2D', 21: 'LOGISTIC', 22: 'RELU',
           26: 'RSQRT', 27: 'SHAPE', 30: 'MEAN', 31: 'MUL', 34: 'MAX_POOL_2D', 36: 'STRIDED_SLICE',
           39: 'SUM', 40: 'CONCATENATION', 44: 'RESIZE_BILINEAR', 46: 'SQUARED_DIFFERENCE',
           48: 'TILE', 52: 'CAST', 54: 'SPLIT', 55: 'SQUEEZE', 57: 'TRANSPOSE', 61: 'PACK',
           64: 'LOG_SOFTMAX', 66: 'EXPAND_DIMS'}
n_ops, ovec = vec(sg, 3)
print("\noperators:", n_ops)
for i in range(n_ops):
    op = elem(ovec, i)
    oidx = val(op, 0)
    code = opcodes[oidx] if oidx is not None and oidx < len(opcodes) else None
    ic, idata = vec(op, 1)
    inputs = [u32(idata + 4 * j) for j in range(ic)]
    oc, odata = vec(op, 2)
    outputs = [u32(odata + 4 * j) for j in range(oc)]
    print(f"  op[{i:2}] {BUILTIN.get(code, f'builtin_{code}'):20} in={inputs} out={outputs}")

n_bufs, bvec = vec(root, 4)
sizes = []
for i in range(1, n_bufs):
    b = elem(bvec, i)
    dl, _ = vec(b, 0)
    sizes.append(dl)
print("\nbuffer data lengths (non-empty):", [s for s in sizes if s])

n_in, ivec = vec(sg, 1)
n_out, ovec2 = vec(sg, 2)
print("\nsubgraph inputs:", [u32(ivec + 4 * j) for j in range(n_in)])
print("subgraph outputs:", [u32(ovec2 + 4 * j) for j in range(n_out)])

n_meta, mvec = vec(root, 5)
print("\nmetadata:", n_meta)
for i in range(n_meta):
    m = elem(mvec, i)
    nl, nd = vec(m, 0)
    print(f"  meta[{i}] name={buf[nd:nd + nl].decode('utf-8', 'replace')!r} buffer={val(m, 1, -1)}")

n_sig, svec = vec(root, 7)
print("signature_defs:", n_sig)
for i in range(n_sig):
    s = elem(svec, i)
    nl, nd = vec(s, 1)
    print("  sig:", buf[nd:nd + nl].decode() if nl else "(unnamed)")
    n_is, isig = vec(s, 2)
    for j in range(n_is):
        x = elem(isig, j)
        l2, b2 = vec(x, 0)
        print("    in:", buf[b2:b2 + l2].decode())
    n_os, osig = vec(s, 3)
    for j in range(n_os):
        x = elem(osig, j)
        l2, b2 = vec(x, 0)
        print("    out:", buf[b2:b2 + l2].decode())

d = tab(root, 3)
if d is not None:
    ln = u32(d)
    print("\ndescription:", buf[d + 4:d + 4 + ln].decode('utf-8', 'replace'))
