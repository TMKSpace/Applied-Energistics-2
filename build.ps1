param([string]$Task = 'compileJava', [switch]$NoAnalyze)
# gradle пишет в stderr, а при Stop PowerShell считает это фатальной ошибкой
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dir = Join-Path $root '.opencode'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# следующий номер прогона
$n = 0
Get-ChildItem $dir -Filter 'build-probe*.log' -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.Name -match 'build-probe(\d+)\.log') { $n = [Math]::Max($n, [int]$Matches[1]) }
}
$n++
$logName = "build-probe$n.log"
$logPath = Join-Path $dir $logName

$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot'
Push-Location $root
try {
    # ВАЖНО: не через Tee-Object — в PowerShell 5.1 он форматирует текст и режет
    # строки по ширине консоли, из-за чего лог становится непригодным для разбора.
    $out = & .\gradlew.bat --no-daemon --console=plain $Task 2>&1 | ForEach-Object { $_.ToString() }
    Set-Content -Path $logPath -Value $out -Encoding UTF8
    $out | Select-Object -Last 4
} finally {
    Pop-Location
}

""
"log: $logName"
if ($NoAnalyze) { return }
powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'analyze.ps1') -LogName $logName | Select-Object -First 5
powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'compat-summary.ps1') -LogName $logName -Top 8
