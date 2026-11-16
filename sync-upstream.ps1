param(
    [string]$UpstreamUrl = 'https://github.com/AppliedEnergistics/Applied-Energistics-2.git',
    [string]$UpstreamBranch = 'main',
    [string]$OurBranch = 'fabric-26.3',
    [switch]$NoBuild
)
# Обновление исходников AE2 с апстрима с сохранением наших маркеров.
#
# Зачем: все правки в src/ — это блоки //#if FABRIC (комментарии), поэтому git умеет
# переносить их на новую версию сам. Конфликты возможны только там, где апстрим
# тронул ровно те же строки, что и мы, — их скрипт показывает списком.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    if (-not (git remote get-url upstream 2>$null)) {
        git remote add upstream $UpstreamUrl
    } else {
        git remote set-url upstream $UpstreamUrl
    }

    "== состояние =="
    git status --porcelain | ForEach-Object { "  $_" }
    $dirty = (git status --porcelain | Measure-Object -Line).Lines
    if ($dirty -gt 0) {
        throw "в дереве есть незакоммиченные изменения ($dirty) — сначала закоммить, потом синхронизировать"
    }

    "== текущая ветка =="
    $current = git rev-parse --abbrev-ref HEAD
    "  $current"
    if ($current -ne $OurBranch) {
        throw "мы не на ветке $OurBranch (сейчас $current)"
    }

    "== тянем апстрим =="
    # Глубина 1 ломает поиск общего предка: shallow-коммит выглядит корнем, и git
    # переигрывает ВСЮ нашу историю (отсюда конфликты add/add на файлах, которые
    # апстрим на самом деле не менял). Поэтому тянем с запасом.
    git fetch --depth=200 upstream $UpstreamBranch
    if ($LASTEXITCODE -ne 0) { throw "fetch не прошёл" }
    $upstreamTip = (git rev-parse FETCH_HEAD).Trim()
    "  апстрим: $upstreamTip"

    "== rebase наших коммитов поверх апстрима =="
    # Переигрываем только своё: всё, что после общего предка.
    $base = (git merge-base HEAD FETCH_HEAD).Trim()
    if (-not $base) { throw "общий предок не найден — нужна история глубже: git fetch --unshallow upstream" }
    "  общий предок: $base"
    git rebase --onto FETCH_HEAD $base
    if ($LASTEXITCODE -ne 0) {
        "конфликты:"
        git diff --name-only --diff-filter=U | ForEach-Object { "  $_" }
        "Разбирать так: в файле оставить ОБЕ стороны маркеров (наш //#if FABRIC и код апстрима),"
        "затем git add <файл>; git rebase --continue"
        throw "rebase остановлен на конфликтах"
    }

    "== инвентарь маркеров =="
    python .opencode\tools\marker-inventory.py

    if ($NoBuild) { return }
    "== сборка =="
    powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'build.ps1')
} catch {
    "ОШИБКА: $($_.Exception.Message)"
    exit 1
} finally {
    Pop-Location
}
