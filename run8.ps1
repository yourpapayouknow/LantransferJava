$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$tokenFile = Join-Path (Join-Path $env:APPDATA "lantransfer") "acco"
if (!(Test-Path -LiteralPath $tokenFile)) {
    throw "run .\tok.ps1 first"
}

$base = Join-Path $PSScriptRoot ".multi"
$ports = 1..8 | ForEach-Object { 45329 + ($_ * 2) }
$scanPorts = $ports -join ","

function Q($text) {
    return $text.Replace("'", "''")
}

for ($i = 1; $i -le 8; $i++) {
    $dir = Join-Path $base "p$i"
    $findPort = $ports[$i - 1]
    $sendPort = $findPort + 1
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    $cmd = @"
`$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath '$(Q $PSScriptRoot)'
`$env:LANTRANSFER_DATA_DIR = '$(Q $dir)'
`$env:LANTRANSFER_DISCOVERY_PORT = '$findPort'
`$env:LANTRANSFER_DISCOVERY_PORTS = '$scanPorts'
`$env:LANTRANSFER_TRANSFER_PORT = '$sendPort'
`$Host.UI.RawUI.WindowTitle = 'LanTransfer-$i'
& '.\run.ps1'
"@
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($cmd))
    Start-Process powershell.exe -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-EncodedCommand", $encoded
    Start-Sleep -Milliseconds 600
}

1..8 | ForEach-Object {
    $findPort = $ports[$_ - 1]
    [pscustomobject]@{
        Id = $_
        Data = Join-Path $base "p$_"
        Discovery = $findPort
        Transfer = $findPort + 1
    }
} | Format-Table -AutoSize
