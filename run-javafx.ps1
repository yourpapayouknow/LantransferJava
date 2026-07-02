param(
    [switch]$SkipCompile,
    [switch]$CompileOnly
)

$ErrorActionPreference = "Stop"

$JavaFxVersion = "17.0.18"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Cache = Join-Path $Root ".javafx-cache\javafx-$JavaFxVersion-win"
$Out = Join-Path $Root "out\fx-classes"
$SourceRoot = Join-Path $Root "src\main\java"
$ResourceRoot = Join-Path $Root "src\main\resources"
$MainClass = "com.zjh.lanudp.app.LanUdpFileDistributorApp"

New-Item -ItemType Directory -Force -Path $Cache | Out-Null

$Artifacts = @("javafx-base", "javafx-graphics", "javafx-controls")
foreach ($Artifact in $Artifacts) {
    $Jar = Join-Path $Cache "$Artifact-$JavaFxVersion-win.jar"
    if (-not (Test-Path -LiteralPath $Jar)) {
        $Url = "https://repo1.maven.org/maven2/org/openjfx/$Artifact/$JavaFxVersion/$Artifact-$JavaFxVersion-win.jar"
        Write-Host "Downloading $Artifact $JavaFxVersion..."
        Invoke-WebRequest -Uri $Url -OutFile $Jar
    }
}

if (-not $SkipCompile) {
    if (Test-Path -LiteralPath $Out) {
        Remove-Item -LiteralPath $Out -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path $Out | Out-Null

    $Sources = Join-Path $env:TEMP "lanudp-javafx-sources.txt"
    $SourcePaths = Get-ChildItem -LiteralPath $SourceRoot -Recurse -Filter "*.java" |
            ForEach-Object { $_.FullName }
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Sources, [string[]]$SourcePaths, $Utf8NoBom)

    javac -encoding UTF-8 -cp "$Cache\*" -d $Out "@$Sources"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

if ($CompileOnly) {
    Write-Host "Compile finished: $Out"
    exit 0
}

java --module-path $Cache --add-modules javafx.controls -cp "$Out;$ResourceRoot" $MainClass
exit $LASTEXITCODE
