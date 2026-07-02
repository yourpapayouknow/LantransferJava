param(
    [switch]$SkipCompile,
    [switch]$CompileOnly
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Classes = Join-Path $Root "target\classes"
$Dependencies = Join-Path $Root "target\dependency"
$MainClass = "com.zjh.lanudp.app.LanUdpFileDistributorApp"
$Maven = "mvn.cmd"
$PinnedMaven = "D:\Programs\Java_UniversalLanguage\apache-maven-3.9.16\bin\mvn.cmd"
if (Test-Path -LiteralPath $PinnedMaven) {
    $Maven = $PinnedMaven
}

if (-not $SkipCompile) {
    & $Maven -q -DskipTests compile dependency:copy-dependencies "-DincludeScope=runtime"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if ($CompileOnly) {
    Write-Host "Compile finished: $Classes"
    exit 0
}

java -cp "$Classes;$Dependencies\*" $MainClass
exit $LASTEXITCODE
