import io
import os
import re

ROOT = r"D:\Code\java\ae2-fabric-probe"
LOG_DIR = ".opencode"

RESOLVER = (
    "// логи тестов лежат в .opencode, чтобы не мусорить в корне проекта\n"
    "$logDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '%s'\n"
    "$logPath = Join-Path $logDir $LogName\n"
    "if (-not (Test-Path $logPath)) {\n"
    "    $logPath = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) $LogName\n"
    "}\n"
) % LOG_DIR

files = ["analyze.ps1", "compat-summary.ps1", "compat-detail.ps1",
         "ae2-detail.ps1", "missing-symbols.ps1", "compat-errors.ps1"]

for name in files:
    path = os.path.join(ROOT, name)
    with io.open(path, encoding="utf-8-sig") as fh:
        text = fh.read()

    if "$logPath" in text:
        print("skip (уже правили):", name)
        continue

    # вставляем резолвер сразу после строки param(...)
    m = re.search(r"^param\([^\n]*\)\n", text, re.M)
    if not m:
        print("!! нет param в", name)
        continue
    text = text[:m.end()] + RESOLVER + text[m.end():]

    # все чтения лога идут через $logPath
    text = re.sub(r"Get-Content \(Join-Path [^)]*\$LogName\)", "Get-Content $logPath", text)
    text = re.sub(r"Get-Content \(\$[A-Za-z]+ \+ \$LogName\)", "Get-Content $logPath", text)
    text = text.replace("(Join-Path 'D:\\Code\\java\\ae2-fabric-probe' $LogName)", "$logPath")

    with io.open(path, "w", encoding="utf-8-sig", newline="\r\n") as fh:
        fh.write(text)
    print("patched", name)

# .gitignore
gi = os.path.join(ROOT, ".gitignore")
with io.open(gi, encoding="utf-8") as fh:
    g = fh.read()
if ".opencode/" not in g:
    g = g.rstrip("\n") + "\n\n# логи прогонов компиляции\n\n.opencode/\n"
    with io.open(gi, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(g)
    print("patched .gitignore")
