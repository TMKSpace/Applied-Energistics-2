$ErrorActionPreference = 'Stop'
$dir = 'D:\Code\java\ae2-fabric-probe\compat\java'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$changed = 0
Get-ChildItem $dir -Recurse -Filter *.java | ForEach-Object {
    $text = [System.IO.File]::ReadAllText($_.FullName, $utf8)
    if ($text.Contains('ICancellableEvent.super.setCanceled(')) {
        $new = $text.Replace('ICancellableEvent.super.setCanceled(', 'super.setCanceled(')
        [System.IO.File]::WriteAllText($_.FullName, $new, $utf8)
        $changed++
    }
}
"files patched: $changed"
