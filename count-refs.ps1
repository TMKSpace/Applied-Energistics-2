$ErrorActionPreference = 'Stop'
$src = 'D:\Code\java\ae2-fabric-probe\src'
$text = (Get-ChildItem $src -Recurse -Filter *.java | Get-Content -Raw) -join "`n"
$names = @(
 'FluidUtil','ItemAccessItemHandler','PlayerInventoryWrapper','VanillaContainerWrapper',
 'ResourceHandlerSlot','StackItemAccess','ItemStackResourceHandler','StacksResourceHandler',
 'FluidStacksResourceHandler','ItemStacksResourceHandler','HandlerItemAccess','OneByOneItemAccess',
 'ItemAccess','EnergyHandler','ItemAccessResourceHandler','CombinedResourceHandler','RangedResourceHandler',
 'ResourceStacksResourceHandler','SnapshotJournal','RootCommitJournal','TransactionManager','SoundActions'
)
foreach ($n in $names) {
    $c = ([regex]::Matches($text, "\b" + $n + "\b")).Count
    "{0,-28} {1}" -f $n, $c
}
