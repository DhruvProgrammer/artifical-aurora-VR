param(
  [Parameter(Mandatory=$true)][string]$Path,
  [int]$Top = 30,
  [switch]$Json
)
$dest = Join-Path $env:TEMP '__rc_null__'
function SizeOf($p) {
  $out = robocopy $p $dest /L /E /BYTES /NFL /NDL /NJH /NC /TS /XJ 2>$null
  $line = $out | Select-String '^\s+Bytes' | Select-Object -First 1
  if ($line) { return [int64](($line.ToString() -replace '.*?Bytes\s*:\s*','' -split '\s+')[0]) }
  return 0
}
$results = @()
foreach ($d in (Get-ChildItem -LiteralPath $Path -Force -Directory -ErrorAction SilentlyContinue)) {
  if ($d.Attributes -band [IO.FileAttributes]::ReparsePoint) { continue }
  $results += [pscustomobject]@{ Path = $d.FullName; GB = [math]::Round((SizeOf $d.FullName)/1GB,3) }
}
$results = $results | Sort-Object GB -Descending
if ($Json) {
  $results | Select-Object -First $Top | ConvertTo-Json -Depth 3
} else {
  $results | Select-Object -First $Top | Format-Table -AutoSize | Out-String -Width 200
  "TOTAL (all children): {0} GB" -f [math]::Round((($results | Measure-Object GB -Sum).Sum),2)
}
