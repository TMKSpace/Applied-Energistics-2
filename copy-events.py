import os
import re
import shutil
from collections import deque

NF = os.path.expandvars(r"%LOCALAPPDATA%\Temp\opencode\nf")
SRC = r"D:\Code\java\ae2-fabric-probe\src"
DEST = r"D:\Code\java\ae2-fabric-probe\compat\java"

ROOTS = [
    (os.path.join(NF, r"src\main\java"), "net.neoforged.neoforge.event."),
    (os.path.join(NF, r"src\client\java"), "net.neoforged.neoforge.client.event."),
]

def resolve(fqn):
    """FQN класса (возможно с вложенным) -> путь к файлу и его FQN."""
    parts = fqn.split(".")
    for cut in range(len(parts), 1, -1):
        cand = ".".join(parts[:cut])
        for root, _ in ROOTS:
            p = os.path.join(root, *cand.split(".")) + ".java"
            if os.path.exists(p):
                return p, cand
    return None, None

# 1) что AE2 импортирует
ae2_imports = set()
imp_re = re.compile(r"^import\s+(net\.neoforged\.neoforge\.(?:client\.)?event\.[\w.]+);")
for dirpath, _, files in os.walk(SRC):
    for f in files:
        if not f.endswith(".java"):
            continue
        with open(os.path.join(dirpath, f), encoding="utf-8", errors="ignore") as fh:
            for line in fh:
                m = imp_re.match(line.strip())
                if m:
                    ae2_imports.add(m.group(1))

print("AE2 импортирует event-классов:", len(ae2_imports))

copied = {}          # file -> fqn
queue = deque()
for fqn in ae2_imports:
    p, real = resolve(fqn)
    if p and p not in copied:
        copied[p] = real
        queue.append(p)

pkg_import = re.compile(r"^import\s+(net\.neoforged\.neoforge\.(?:client\.)?event\.[\w.]+);")
ext_re = re.compile(r"^public\s+(?:abstract\s+)?class\s+\w+(?:<[^>]*>)?\s+extends\s+(\w+)")

while queue:
    p = queue.popleft()
    pkg = os.path.dirname(p)
    with open(p, encoding="utf-8", errors="ignore") as fh:
        text = fh.read()
    for line in text.splitlines():
        s = line.strip()
        m = pkg_import.match(s)
        if m:
            dep, real = resolve(m.group(1))
            if dep and dep not in copied:
                copied[dep] = real
                queue.append(dep)
            continue
        m = ext_re.match(s)
        if m:
            # суперкласс в том же пакете?
            dep = os.path.join(pkg, m.group(1) + ".java")
            if os.path.exists(dep) and dep not in copied:
                copied[dep] = os.path.basename(dep)[:-5]
                queue.append(dep)

print("нужно скопировать:", len(copied))

for p, real in copied.items():
    rel = os.path.relpath(p, os.path.join(NF, "src", "main", "java")) if "\\src\\main\\" in p else None
    if rel is None or rel.startswith(".."):
        rel = os.path.relpath(p, os.path.join(NF, "src", "client", "java"))
    dst = os.path.join(DEST, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(p, dst)

# 3) какие импорты в скопированном ссылаются на то, чего у нас нет
missing = {}
all_imp = re.compile(r"^import\s+(net\.neoforged\.[\w.]+);")
have_pkgs = set()
for dirpath, _, files in os.walk(DEST):
    for f in files:
        if f.endswith(".java"):
            have_pkgs.add(os.path.relpath(dirpath, DEST).replace(os.sep, "."))

for p in copied:
    with open(p, encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            m = all_imp.match(line.strip())
            if not m:
                continue
            fq = m.group(1)
            pkg = fq.rsplit(".", 1)[0]
            if pkg not in have_pkgs and pkg + ".package-info" not in have_pkgs:
                if not os.path.isdir(os.path.join(DEST, *pkg.split("."))):
                    missing[pkg] = missing.get(pkg, 0) + 1

print("\nПакеты net.neoforged.*, которых у нас нет (по числу импортов):")
for pkg, cnt in sorted(missing.items(), key=lambda kv: -kv[1]):
    print(f"  {cnt:4d}  {pkg}")
