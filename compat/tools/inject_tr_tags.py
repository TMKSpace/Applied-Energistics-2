#!/usr/bin/env python3
"""Пост-шаг сборки: вкладывает в джар теги совместимости с Tech Reborn.

Зачем. TechReborn 6.2.1 включает свои AE2-рецепты (`fabric:all_mods_loaded: [ae2]`)
и ссылается в них на `#c:certus_quartz` и `#c:ores/certus_quartz`. В AE2 26.3 таких
тегов нет, а руды сертус-кварца нет вовсе (жеоды и кластеры), поэтому реестр
рецептов падает и сервер не стартует:

    Missing tag: 'c:certus_quartz' in 'minecraft:item'
    Failed to load datapacks, can't proceed with server load

На NeoForge с AE2 26.3 то же самое - это баг интеграции в TR, чинить его должен он.
Пока фикса нет, подкладываем два тега в свой джар: имена `c:`-тегов никто больше
не объявляет, так что ни с кем не конфликтуем.

- `c:certus_quartz`  -> `#c:gems/certus_quartz` (помол работает);
- `c:ores/certus_quartz` -> пустой тег (рецепты «с рудой» парсятся, но ни с чем
  не совпадают - руды в AE2 нет, и это честно).

УБРАТЬ, когда TR починят (или если AE2 начнёт поставлять теги сам).

Запуск: python compat/tools/inject_tr_tags.py <путь к собранному джару>
Идемпотентно: если записи уже есть, джар не трогается.
"""
import json
import sys
import zipfile
from pathlib import Path

ENTRIES = {
    "data/c/tags/item/certus_quartz.json": {
        "values": ["#c:gems/certus_quartz"],
    },
    "data/c/tags/item/ores/certus_quartz.json": {
        "_comment": (
            "AE2 26.3 не имеет руды сертус-кварца (только жеоды и кластеры), поэтому тег пуст: "
            "рецепты TechReborn с ним парсятся, но ни с чем не совпадают."
        ),
        "values": [],
    },
}


def main() -> None:
    if len(sys.argv) != 2:
        print(__doc__)
        raise SystemExit(2)
    jar = Path(sys.argv[1])
    if not jar.is_file():
        raise SystemExit(f"нет файла: {jar}")

    with zipfile.ZipFile(jar) as z:
        existing = set(z.namelist())
        missing = {name: data for name, data in ENTRIES.items() if name not in existing}
        if not missing:
            return
        # Сохраняем всё, что было: имя, дату, содержимое (в распакованном виде).
        items = [(i.filename, i.date_time, z.read(i.filename)) for i in z.infolist()]

    tmp = jar.with_suffix(jar.suffix + ".tmp")
    with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for name, date_time, payload in items:
            info = zipfile.ZipInfo(name, date_time)
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, payload)
        for name, data in missing.items():
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, json.dumps(data, ensure_ascii=False, indent=2) + "\n")

    tmp.replace(jar)
    print(f"inject_tr_tags: добавлено {len(missing)} тег(ов) в {jar.name}")


if __name__ == "__main__":
    main()
