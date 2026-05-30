<#
  Native Windows build for the BurpTheme extension.

  Burp Suite's bundled JDK provides javac but NOT jar.exe / the jdk.jartool
  module, so packaging is done by tools/MakeJar.java (java.util.jar).

  Defaults target the local Burp Suite install. Override with parameters:
    .\build.ps1 -BurpJar "C:\path\burpsuite.jar" -JdkBin "C:\path\jre\bin"
#>
param(
  [string]$BurpJar = "C:\Users\ragha\AppData\Local\BurpSuite\burpsuite.jar",
  [string]$JdkBin  = "C:\Users\ragha\AppData\Local\BurpSuite\jre\bin",
  [string]$Release = "17"
)

$ErrorActionPreference = "Stop"
$root      = $PSScriptRoot
$src       = Join-Path $root "src"
$build     = Join-Path $root "build"
$classes   = Join-Path $build "classes"
$resources = Join-Path $build "resources"
$assetsOut = Join-Path $resources "assets"
$toolsBin  = Join-Path $build "tools"
$dist      = Join-Path $root "dist"
$outJar    = Join-Path $dist "arcade-burp-community.jar"
$javac     = Join-Path $JdkBin "javac.exe"
$java      = Join-Path $JdkBin "java.exe"

if (-not (Test-Path $BurpJar)) { throw "Burp jar not found: $BurpJar" }
if (-not (Test-Path $javac))   { throw "javac not found: $javac" }

Write-Host "Cleaning build directory..."
if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force -Path $classes, $assetsOut, $toolsBin, $dist | Out-Null

Write-Host "Compiling sources (--release $Release)..."
$sources = Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release $Release -classpath $BurpJar -sourcepath $src -implicit:none -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed (exit $LASTEXITCODE)" }

Write-Host "Staging assets..."
$assetSrc = Join-Path $root "assets"
if (Test-Path $assetSrc) {
  Copy-Item -Path (Join-Path $assetSrc "*") -Destination $assetsOut -Recurse -Force
}

Write-Host "Writing manifest..."
$manifest = Join-Path $build "manifest.mf"
@(
  "Manifest-Version: 1.0",
  "Burp-Extension-Class: burp.arcade.BurpThemeExtension",
  "Implementation-Title: BurpTheme",
  "Implementation-Version: 1.0.0",
  "Implementation-Vendor: Ashtaksha Labs",
  "Built-By: Raghav Vivekanandan @ Ashtaksha Labs",
  ""
) | Set-Content -Path $manifest -Encoding ascii

Write-Host "Compiling MakeJar packager..."
& $javac -d $toolsBin (Join-Path $root "tools\MakeJar.java")
if ($LASTEXITCODE -ne 0) { throw "MakeJar compile failed (exit $LASTEXITCODE)" }

Write-Host "Packaging jar..."
& $java -cp $toolsBin MakeJar $outJar $manifest $classes $resources
if ($LASTEXITCODE -ne 0) { throw "packaging failed (exit $LASTEXITCODE)" }

Write-Host "Build OK -> $outJar"
