import os, re, sys, zipfile, io
from jclass import ClassFile

# Пути можно переопределить переменными окружения (см. README рядом).
GUIDEME = os.environ.get('GUIDEME_JAR', r"C:\Users\Kot\.gradle\caches\modules-2\files-2.1\org.appliedenergistics\guideme\26.3.7-alpha\ce305981c7cb5fc79fe2fe7e75bf95d360646abe\guideme-26.3.7-alpha.jar")
VANILLA = os.environ.get('VANILLA_JAR', r"C:\Users\Kot\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.3\minecraft-merged-deobf-26.3.jar")
SHIM_DIR = os.environ.get('SHIM_DIR', r"D:\Code\java\ae2-fabric-probe\build\classes\java\main")
LIBS = {
    "gson": r"C:\Users\Kot\.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson\2.14.0\efc0e34ede4e3204eaefb84a00e55e8c86634382\gson-2.14.0.jar",
    "joml": r"C:\Users\Kot\.gradle\caches\modules-2\files-2.1\org.joml\joml\1.10.9\438e036486bad66b189bff385dd07dea4f74a146\joml-1.10.9.jar",
    "dfu":  r"C:\Users\Kot\.gradle\caches\modules-2\files-2.1\com.mojang\datafixerupper\10.0.21\b6b2ae770c02e0c1eb90f9985b151e9085a38d0b\datafixerupper-10.0.21.jar",
    "brigadier": r"C:\Users\Kot\.gradle\caches\modules-2\files-2.1\com.mojang\brigadier\1.3.11\3373d1e7bf00c8b99bed1ea4efb8c47344e4a887\brigadier-1.3.11.jar",
}


class Info:
    __slots__ = ("fields", "methods", "super", "ifaces", "origin")
    def __init__(self, sup, ifaces, origin):
        self.fields = set(); self.methods = set()
        self.super = sup; self.ifaces = ifaces; self.origin = origin


def load_jar(path, origin, idx):
    with zipfile.ZipFile(path) as z:
        for n in z.namelist():
            if not n.endswith(".class"):
                continue
            try:
                cf = ClassFile(z.read(n))
            except Exception as e:
                continue
            if cf.this_name is None:
                continue
            inf = Info(cf.super_name, cf.interfaces, origin)
            inf.fields = cf.fields
            inf.methods = cf.methods
            idx[cf.this_name] = inf


def load_dir(root, origin, idx):
    for dp, dn, fn in os.walk(root):
        for f in fn:
            if not f.endswith(".class"):
                continue
            p = os.path.join(dp, f)
            rel = os.path.relpath(p, root).replace("\\", "/")[:-6]
            try:
                cf = ClassFile(open(p, "rb").read())
            except Exception:
                continue
            inf = Info(cf.super_name, cf.interfaces, origin)
            inf.fields = cf.fields; inf.methods = cf.methods
            idx[cf.this_name or rel] = inf


def resolve(idx, owner, name, desc, kind, seen=None):
    """True if member exists on owner or any ancestor/interface."""
    if owner is None:
        return False
    if seen is None: seen = set()
    if owner in seen: return False
    seen.add(owner)
    inf = idx.get(owner)
    if inf is None:
        return None  # class unknown
    s = inf.fields if kind == "field" else inf.methods
    if (name, desc) in s:
        return True
    if kind != "field":
        # for methods, desc must match; but check name-only for a hint
        for r in ([inf.super] if inf.super else []) + list(inf.ifaces):
            if r and resolve(idx, r, name, desc, kind, seen):
                return True
        return False
    for r in [inf.super] + list(inf.ifaces):
        if r and resolve(idx, r, name, desc, kind, seen):
            return True
    return False


def name_exists(idx, owner, name, kind):
    seen = set()
    st = [owner]
    while st:
        o = st.pop()
        if o in seen: continue
        seen.add(o)
        inf = idx.get(o)
        if inf is None: continue
        s = inf.fields if kind == "field" else inf.methods
        if any(n == name for (n, d) in s):
            return True
        if inf.super: st.append(inf.super)
        st.extend(inf.ifaces)
    return False


TARGET_PREFIXES = ("net/minecraft/", "com/mojang/blaze3d/", "com/mojang/serialization/",
                   "com/google/gson/", "org/joml/", "com/mojang/math/", "com/mojang/brigadier/")


def main():
    print("loading vanilla..."); sys.stdout.flush()
    van = {}
    load_jar(VANILLA, "vanilla", van)
    print("  vanilla classes:", len(van))
    libs = {}
    for k, v in LIBS.items():
        load_jar(v, k, libs)
        print("  ", k, "classes:", len(libs))
    shim = {}
    load_dir(SHIM_DIR, "shim", shim)
    print("  shim classes:", len(shim))
    allidx = {}
    allidx.update(libs); allidx.update(van); allidx.update(shim)

    print("scanning guideme..."); sys.stdout.flush()
    results = []   # (gmclass, gmctx, kind, owner, name, desc, status)
    with zipfile.ZipFile(GUIDEME) as z:
        for n in z.namelist():
            if not n.endswith(".class"):
                continue
            cf = ClassFile(z.read(n))
            gm = cf.this_name
            for (kind, owner, name, desc, ctx) in cf.refs:
                if owner is None: continue
                if not owner.startswith(TARGET_PREFIXES) and not owner.startswith("net/neoforged/"):
                    continue
                st = resolve(allidx, owner, name, desc, kind)
                if st is True:
                    continue
                status = "NO_CLASS" if st is None else ("NO_MEMBER" )
                results.append((gm, ctx, kind, owner, name, desc, status))

    # dedupe: keep all call sites (class, ctx) but dedupe (owner,name,desc,class,ctx)
    seen = set(); out = []
    for r in results:
        k = (r[0], r[1], r[3], r[4], r[5], r[2])
        if k in seen: continue
        seen.add(k); out.append(r)
    out.sort(key=lambda r: (r[3], r[4], r[0], r[1]))
    import json
    json.dump([list(r) for r in out], open("missing.json", "w"), indent=0)
    print("MISSING REFS:", len(out))
    for r in out:
        print("%s | %s %s.%s%s | in %s.%s" % (r[6], r[2], r[3], r[4], r[5], r[0].split("/")[-1], r[1]))


if __name__ == "__main__":
    main()
