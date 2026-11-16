"""Дописать в markers.tsv маркеры, которых там ещё нет.

markers.tsv — ручной инвентарь мест, где src/ расходится с апстримом (колонки:
file, line, note, fabric_code). Заметки руками писать лень, поэтому скрипт только
дописывает отсутствующие пары (file, line) с пустой заметкой и кодом первой
FABRIC-строки блока. Существующие строки (с заметками) не трогает.
"""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent
TSV = ROOT / "markers.tsv"


def read_rows():
    text = TSV.read_text(encoding="utf-8")
    lines = text.splitlines()
    header, rows = lines[0], lines[1:]
    return header, rows


def collect_markers():
    found = []
    for path in sorted((ROOT / "src").rglob("*.java")):
        rel = path.relative_to(ROOT).as_posix().replace("/", "\\")
        for idx, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), start=1):
            if "//#if FABRIC" not in line:
                continue
            found.append((rel, idx))
    return found


def main():
    header, rows = read_rows()
    have = set()
    for row in rows:
        parts = row.split("\t")
        if len(parts) >= 2:
            try:
                have.add((parts[0], int(parts[1])))
            except ValueError:
                pass

    added = 0
    for rel, line in collect_markers():
        if (rel, line) in have:
            continue
        rows.append(f"{rel}\t{line}\t\t")
        have.add((rel, line))
        added += 1

    TSV.write_text("\n".join([header] + rows) + "\n", encoding="utf-8")
    print(f"markers.tsv: добалено {added}, всего строк {len(rows)}")


if __name__ == "__main__":
    main()
