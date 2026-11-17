import struct

OPLEN = {}
def _s(l, ops):
    for o in ops: OPLEN[o] = l

_s(1, range(0x00, 0x10))          # nop..dconst_1
_s(2, [0x10, 0x12])              # bipush, ldc
_s(3, [0x11, 0x13, 0x14])        # sipush, ldc_w, ldc2_w
OPLEN[0x15]=2; OPLEN[0x16]=2; OPLEN[0x17]=2; OPLEN[0x18]=2; OPLEN[0x19]=2  # iload..aload
OPLEN[0x36]=2; OPLEN[0x37]=2; OPLEN[0x38]=2; OPLEN[0x39]=2; OPLEN[0x3a]=2  # istore..astore
_s(1, [0x1a,0x1b,0x1c,0x1d,0x1e,0x1f,0x20,0x21,0x22,0x23,0x24,0x25,0x26,0x27,0x28,0x29])
_s(1, [0x2a,0x2b,0x2c,0x2d,0x2e,0x2f,0x30,0x31,0x32,0x33,0x34,0x35])
_s(1, [0x3b,0x3c,0x3d,0x3e,0x3f,0x40,0x41,0x42,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4a,0x4b,0x4c,0x4d,0x4e])
_s(1, range(0x4f, 0x84))         # istore..iinc? careful
# 0x50..0x83 array ops / stack ops are 1 byte; 0x84 iinc is 3
OPLEN[0x84]=3
_s(1, [0x85,0x86,0x87,0x88,0x89,0x8a,0x8b,0x8c,0x8d,0x8e,0x8f,0x90,0x91,0x92,0x93,0x94,0x95,0x96,0x97,0x98])
_s(1, [0xac,0xad,0xae,0xaf,0xb0,0xb1])
_s(3, [0xb2,0xb3,0xb4,0xb5])     # getstatic,putstatic,getfield,putfield
_s(3, [0xb6,0xb7,0xb8,0xb9])     # invoke*
OPLEN[0xba]=5                     # invokedynamic
_s(3, [0xbb,0xbc,0xbd])          # new, newarray, anewarray
_s(1, [0xbe,0xbf])               # arraylength, athrow
_s(3, [0xc0,0xc1])               # checkcast, instanceof
_s(1, [0xc2,0xc3])               # monitorenter/exit
OPLEN[0xc4]=0                     # wide (special)
OPLEN[0xc5]=4                     # multianewarray
_s(3, [0xc6,0xc7])               # ifnull, ifnonnull
OPLEN[0xc8]=5; OPLEN[0xc9]=5
OPLEN[0xaa]=0; OPLEN[0xab]=0      # switch (special)
_s(3, range(0x99, 0xa9))         # if* / goto / jsr
_s(1, [0xca])                    # breakpoint


def _u1(b, i): return b[i]
def _u2(b, i): return struct.unpack_from(">H", b, i)[0]
def _u4(b, i): return struct.unpack_from(">I", b, i)[0]


class ClassFile:
    def __init__(self, data):
        self.data = data
        self.cp = []      # list of tuples
        self.refs = []    # (kind, owner, name, desc, method_ctx)
        self.classrefs = set()
        self.this_name = None
        self.super_name = None
        self.interfaces = []
        self.fields = set()   # (name, desc)
        self.methods = set()
        self._parse()

    def _parse(self):
        b = self.data
        if _u4(b, 0) != 0xCAFEBABE:
            raise ValueError("not a class")
        pos = 8
        cpcount = _u2(b, pos); pos += 2
        cp = [None]*cpcount
        i = 1
        while i < cpcount:
            tag = b[pos]; pos += 1
            if tag == 1:
                ln = _u2(b, pos); pos += 2
                cp[i] = ("utf8", b[pos:pos+ln].decode("utf-8", "replace")); pos += ln
            elif tag in (7, 8, 16, 19, 20):
                cp[i] = (tag, _u2(b, pos)); pos += 2
            elif tag in (15,):
                cp[i] = (tag, b[pos], _u2(b, pos+1)); pos += 3
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                cp[i] = (tag, _u2(b, pos), _u2(b, pos+2)); pos += 4
            elif tag in (5, 6):
                cp[i] = (tag,); pos += 8; i += 1
            else:
                raise ValueError("bad cp tag %d at %d" % (tag, pos))
            i += 1
        self.cp = cp

        def utf(idx):
            e = cp[idx]
            return e[1] if e and e[0] == "utf8" else ""

        def clsname(idx):
            if idx == 0: return None
            e = cp[idx]
            if e[0] == 7: return utf(e[1])
            return None

        pos += 2  # access
        self.this_name = clsname(_u2(b, pos)); pos += 2
        self.super_name = clsname(_u2(b, pos)); pos += 2
        nif = _u2(b, pos); pos += 2
        for _ in range(nif):
            self.interfaces.append(clsname(_u2(b, pos))); pos += 2
        # fields
        n = _u2(b, pos); pos += 2
        for _ in range(n):
            pos += 2
            nm = utf(_u2(b, pos)); pos += 2
            ds = utf(_u2(b, pos)); pos += 2
            self.fields.add((nm, ds))
            na = _u2(b, pos); pos += 2
            for _ in range(na):
                pos += 2
                ln = _u4(b, pos); pos += 4
                pos += ln
        # methods
        n = _u2(b, pos); pos += 2
        for _ in range(n):
            pos += 2
            nm = utf(_u2(b, pos)); pos += 2
            ds = utf(_u2(b, pos)); pos += 2
            self.methods.add((nm, ds))
            na = _u2(b, pos); pos += 2
            code = None
            for _ in range(na):
                an = utf(_u2(b, pos)); pos += 2
                ln = _u4(b, pos); pos += 4
                if an == "Code":
                    code = (pos, ln)
                pos += ln
            if code is not None:
                self._scan_code(code[0], nm + ds, utf, clsname)

    def _scan_code(self, p, ctx, utf, clsname):
        b = self.data
        mx = _u2(b, p); ml = _u2(b, p+2); clen = _u4(b, p+4)
        p = p + 8
        end = p + clen
        i = p
        while i < end:
            op = b[i]
            if op in (0xaa, 0xab):
                pad = (4 - ((i + 1 - p) % 4)) % 4
                j = i + 1 + pad
                if op == 0xaa:
                    lo = struct.unpack_from(">i", b, j+4)[0]
                    hi = struct.unpack_from(">i", b, j+8)[0]
                    i = j + 12 + 4*(hi-lo+1)
                else:
                    npairs = struct.unpack_from(">i", b, j+4)[0]
                    i = j + 8 + 8*npairs
                continue
            if op == 0xc4:  # wide
                op2 = b[i+1]
                i += 6 if op2 == 0x84 else 4
                continue
            ln = OPLEN.get(op)
            if ln is None:
                i += 1
                continue
            if op in (0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9):
                idx = _u2(b, i+1)
                e = self.cp[idx]
                if e and e[0] in (9, 10, 11):
                    nm_i, ds_i = e[1], e[2]
                    nat = self.cp[ds_i]
                    owner = clsname(nm_i)
                    kind = {9: "field", 10: "method", 11: "imethod"}[e[0]]
                    self.refs.append((kind, owner, utf(nat[1]), utf(nat[2]), ctx))
            elif op in (0xbb, 0xc0, 0xc1, 0xbd):
                idx = _u2(b, i+1)
                self.classrefs.add(clsname(idx))
            elif op == 0xb9:
                pass
            i += ln
        # also catch new/checkcast handled above; ldc of class
        return
