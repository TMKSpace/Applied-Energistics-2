param(
    [string]$Org = 'TMKSpace',
    [string]$Upstream = 'AppliedEnergistics/Applied-Energistics-2',
    [string]$Branch = 'main'
)
# Форкает апстрим AE2 в организацию и пушит наш коммит поверх его истории.
# Запускать после `gh auth login` (нужны права создавать репозитории в организации).
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    gh auth status | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "gh не авторизован: сначала выполните 'gh auth login'"
    }

    $repoName = ($Upstream -split '/')[1]
    $fork = "$Org/$repoName"

    $existing = gh repo view $fork --json name 2>$null
    if ($LASTEXITCODE -ne 0) {
        "форкаю $Upstream -> $fork"
        gh repo fork $Upstream --org $Org --clone=false
        if ($LASTEXITCODE -ne 0) { throw "не удалось форкнуть (есть ли права в $Org?)" }
    } else {
        "форк уже есть: $fork"
    }

    gh auth setup-git | Out-Null
    $url = "https://github.com/$fork.git"
    $existingRemote = git remote get-url origin 2>$null
    if ($LASTEXITCODE -eq 0) {
        git remote set-url origin $url
    } else {
        git remote add origin $url
    }

    "пушу $Branch"
    git push -u origin $Branch
    if ($LASTEXITCODE -ne 0) { throw "push не прошёл" }

    "готово: https://github.com/$fork"
} catch {
    "ОШИБКА: $($_.Exception.Message)"
    exit 1
} finally {
    Pop-Location
}
