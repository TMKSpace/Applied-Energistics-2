#!/usr/bin/env python3
"""Генератор ключей перевода для экрана конфигурации (ForgeConfigAPIPort).

NeoForge-экран конфигурации (и порт FCP) ищет подписи по ключам
`<modid>.configuration.<путь>` и `<modid>.configuration.<путь>.tooltip`.
AE2 такие ключи не поставляет, поэтому вместо названий видно сырые пути
(`matterCannonBlockDamage`). Скрипт читает сгенерированные файлы конфигурации
(`ae2-common.toml`, `ae2-client.toml`), вытаскивает секции/опции/комментарии
и собирает lang-файл.

Запуск (из корня проекта):
    python compat/tools/gen_config_lang.py <папка с ae2-*.toml> <выходной json>

Lang-файл кладётся в свой namespace (`assets/nfcompat/lang/`): Minecraft
мержит lang всех namespace в одну таблицу, поэтому ключи `ae2.configuration.*`
из чужого namespace работают, а файл AE2 (`assets/ae2/lang/en_us.json`) мы
не трогаем и с ним не конфликтуем.
"""
import json
import re
import sys
from pathlib import Path

MOD_ID = "ae2"

# Ключи nfcompat, которые не выводятся из конфига, но должны лежать там же:
# свой namespace нужен только как контейнер, Minecraft мержит lang всех namespace.
BASE_KEYS = {
    "nfcompat.config.guideme.title": "GuideME",
    "nfcompat.config.guideme.hint": "Settings: config/guideme-client.toml",
}
SECTION_RE = re.compile(r"^\s*\[([^\]]+)\]\s*$")
KEY_RE = re.compile(r"^\s*([A-Za-z0-9_]+)\s*=")
COMMENT_RE = re.compile(r"^\s*#\s?(.*)$")
# Служебные строки NeoForge дописывает сам (значения по умолчанию, диапазон, варианты).
NOISE = ("Default:", "Range:", "Allowed Values:")


# Особые случаи: одной эвристикой не разобрать (нет границы слов / устоявшееся написание).
SECTION_NAMES = {"spatialio": "Spatial IO"}

ACRONYMS = {"tnt", "io", "cpu", "fe", "p2p", "ae", "gui", "ui", "id", "nbt", "me", "jei", "pch"}


def humanize(name: str) -> str:
    """unsupportedDeveloperTools -> Unsupported Developer Tools, tinyTnt -> Tiny TNT"""
    spaced = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", " ", name)
    spaced = spaced.replace("_", " ").strip()
    words = [w.upper() if w.lower() in ACRONYMS else w for w in spaced.split()]
    text = " ".join(words)
    return text[:1].upper() + text[1:] if text else name


def parse(path: Path):
    """Возвращает [(путь_секции_список, [(ключ, [комментарии])])]"""
    sections = []
    current = None
    comments = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        sec = SECTION_RE.match(raw)
        if sec:
            current = [p.strip() for p in sec.group(1).split(".")]
            sections.append((current, []))
            comments = []
            continue
        com = COMMENT_RE.match(raw)
        if com and current is not None:
            text = com.group(1).strip()
            if text and not text.startswith(NOISE):
                comments.append(text)
            continue
        key = KEY_RE.match(raw)
        if key and current is not None:
            sections[-1][1].append((key.group(1), comments))
            comments = []
    return sections


def main() -> None:
    if len(sys.argv) != 3:
        print(__doc__)
        raise SystemExit(2)
    src_dir, out_file = Path(sys.argv[1]), Path(sys.argv[2])

    lang = dict(BASE_KEYS)
    for toml in sorted(src_dir.glob("ae2-*.toml")):
        for path, entries in parse(toml):
            section_key = ".".join([MOD_ID, "configuration", *path])
            lang.setdefault(section_key, SECTION_NAMES.get(path[-1], humanize(path[-1])))
            for name, comments in entries:
                # Экран (NeoForge и порт FCP) строит ключ значения как
                # <modid>.configuration.<ИМЯ ОПЦИИ> - без пути секции.
                # Полный путь оставляем тоже: он ничему не мешает и помогает,
                # если апстрим когда-нибудь перейдёт на путь целиком.
                for key in (section_key + "." + name, MOD_ID + ".configuration." + name):
                    lang.setdefault(key, humanize(name))
                    if comments:
                        lang.setdefault(key + ".tooltip", " ".join(comments))

    out_file.parent.mkdir(parents=True, exist_ok=True)
    out_file.write_text(
        json.dumps(lang, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"{out_file}: ключей {len(lang)}")


if __name__ == "__main__":
    main()
