$ErrorActionPreference = 'Stop'
$root = 'D:\Code\java\ae2-fabric-probe'
"=== usages of CommonHooks.* ==="
Get-ChildItem "$root\src","$root\compat" -Recurse -Filter *.java | Select-String -Pattern 'CommonHooks\.\w+' | ForEach-Object { $_.Line.Trim() } | Sort-Object -Unique
""
"=== FluidStackTemplate 50-56 ==="
Get-Content "$root\compat\java\net\neoforged\neoforge\fluids\FluidStackTemplate.java" | Select-Object -Skip 49 -First 8
""
"=== PlayerInventoryWrapper 15-20 / 230-260 ==="
Get-Content "$root\compat\java\net\neoforged\neoforge\transfer\item\PlayerInventoryWrapper.java" | Select-Object -Skip 14 -First 6
Get-Content "$root\compat\java\net\neoforged\neoforge\transfer\item\PlayerInventoryWrapper.java" | Select-Object -Skip 229 -First 34
