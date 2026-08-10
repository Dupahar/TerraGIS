param(
    [string]$JavaHome = "C:\Program Files\Java\jdk-25.0.2",
    [string]$VersionTag = "1.0.0-beta.1",
    [switch]$SkipVerify,
    [switch]$SkipBuild,
    [switch]$SkipJPackage
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not (Test-Path $JavaHome)) {
    throw "JAVA_HOME not found: $JavaHome"
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$artifactsRoot = Join-Path $repoRoot "artifacts\private-beta"
$runDir = Join-Path $artifactsRoot "$VersionTag-$timestamp"
$appImageDir = Join-Path $runDir "app-image"
$bundleDir = Join-Path $runDir "bundle"
$evidenceDir = Join-Path $repoRoot "docs\release_evidence"
$evidenceFile = Join-Path $evidenceDir "${VersionTag}_$timestamp.md"

New-Item -ItemType Directory -Path $runDir -Force | Out-Null
New-Item -ItemType Directory -Path $appImageDir -Force | Out-Null
New-Item -ItemType Directory -Path $bundleDir -Force | Out-Null

$commandsRun = New-Object System.Collections.Generic.List[string]

function Start-PrivateBetaStep {
    param(
        [string]$Command,
        [string]$Description
    )

    Write-Host "==> $Description"
    Write-Host "    $Command"
    $commandsRun.Add($Command) | Out-Null
    & cmd /c $Command
    if ($LASTEXITCODE -ne 0) {
        throw ("Command failed with exit code {0} for {1}" -f $LASTEXITCODE, $Command)
    }
}

if (-not $SkipVerify) {
    Start-PrivateBetaStep ".\mvnw.cmd clean verify" "Running quality gate"
}

if (-not $SkipBuild) {
    Start-PrivateBetaStep ".\mvnw.cmd -q -DskipTests dependency:copy-dependencies" "Copying runtime dependencies"
}

$jarPath = Join-Path $repoRoot "target\TerraGIS-1.0-SNAPSHOT.jar"
if (-not (Test-Path $jarPath)) {
    throw "Expected JAR not found: $jarPath"
}

Copy-Item $jarPath -Destination $bundleDir -Force
if (Test-Path (Join-Path $repoRoot "target\dependency")) {
    Copy-Item (Join-Path $repoRoot "target\dependency\*") -Destination $bundleDir -Recurse -Force
}

$javafxRuntimeDir = Join-Path $bundleDir "javafx"
New-Item -ItemType Directory -Path $javafxRuntimeDir -Force | Out-Null
Get-ChildItem -Path $bundleDir -Filter "javafx-*.jar" | ForEach-Object {
    Copy-Item $_.FullName -Destination $javafxRuntimeDir -Force
}

Copy-Item (Join-Path $repoRoot "Start-TerraGIS.cmd") -Destination $bundleDir -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $repoRoot "start-terragis.ps1") -Destination $bundleDir -Force -ErrorAction SilentlyContinue

$jpackagePath = (Get-Command jpackage -ErrorAction SilentlyContinue)
$hasJPackage = $null -ne $jpackagePath

if (-not $SkipJPackage -and $hasJPackage) {
    $jpackageCmd = @(
        "jpackage",
        "--type", "app-image",
        "--name", "TerraGISBeta",
        "--dest", ('"' + $appImageDir + '"'),
        "--input", ('"' + $bundleDir + '"'),
        "--main-jar", "TerraGIS-1.0-SNAPSHOT.jar",
        "--main-class", "com.terra.gis.App",
        "--java-options", '"--module-path=$APPDIR\javafx"',
        "--java-options", '"--add-modules=javafx.controls,javafx.fxml,javafx.swing"',
        "--java-options", '"--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED"',
        "--java-options", '"-Dterragis.release.channel=private-beta"'
    ) -join " "

    Start-PrivateBetaStep $jpackageCmd "Creating jpackage app-image"

    $javafxAppImageDir = Join-Path $appImageDir "TerraGISBeta\javafx"
    New-Item -ItemType Directory -Path $javafxAppImageDir -Force | Out-Null
    Copy-Item (Join-Path $javafxRuntimeDir "*") -Destination $javafxAppImageDir -Recurse -Force
} elseif (-not $hasJPackage) {
    Write-Warning "jpackage not found on PATH; skipping app-image creation"
}

$jarHash = (Get-FileHash $jarPath -Algorithm SHA256).Hash
$javaVersion = (& java -version 2>&1 | Out-String).Trim()
$mvnVersion = (& .\mvnw.cmd -version 2>&1 | Out-String).Trim()

$makeAppx = Get-Command makeappx.exe -ErrorAction SilentlyContinue
$signtool = Get-Command signtool.exe -ErrorAction SilentlyContinue

$appImageExists = Test-Path (Join-Path $appImageDir "TerraGISBeta")

$evidence = New-Object System.Text.StringBuilder
[void]$evidence.AppendLine("# TerraGIS Private Beta Packaging Evidence")
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("- Version tag: $VersionTag")
[void]$evidence.AppendLine("- Timestamp: $timestamp")
[void]$evidence.AppendLine("- Workspace: $repoRoot")
[void]$evidence.AppendLine("- Branch: $(git branch --show-current)")
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("## Toolchain")
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("### java -version")
foreach ($line in ($javaVersion -split "`r?`n")) {
    [void]$evidence.AppendLine("    $line")
}
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("### mvnw -version")
foreach ($line in ($mvnVersion -split "`r?`n")) {
    [void]$evidence.AppendLine("    $line")
}
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("## Build Artifacts")
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("- Main jar: $jarPath")
[void]$evidence.AppendLine("- Main jar SHA256: $jarHash")
[void]$evidence.AppendLine("- Bundle directory: $bundleDir")
[void]$evidence.AppendLine("- App image created: $appImageExists")
[void]$evidence.AppendLine("- App image path: $(Join-Path $appImageDir 'TerraGISBeta')")
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("## Store Packaging Prerequisites")
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("- makeappx.exe available: $([bool]$makeAppx)")
[void]$evidence.AppendLine("- signtool.exe available: $([bool]$signtool)")
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("## Commands Executed")
[void]$evidence.AppendLine()
foreach ($line in $commandsRun) {
    [void]$evidence.AppendLine("- $line")
}
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("## Next Actions")
[void]$evidence.AppendLine()
[void]$evidence.AppendLine("1. Validate app-image launch on clean Windows 10/11 machines.")
[void]$evidence.AppendLine("2. Package/sign MSIX in CI or local signing environment.")
[void]$evidence.AppendLine("3. Submit to Microsoft Store private audience with known issues note.")

Set-Content -Path $evidenceFile -Value $evidence.ToString() -Encoding UTF8

Write-Host ""
Write-Host "Private beta packaging prep complete"
Write-Host "Evidence: $evidenceFile"
Write-Host "Artifacts: $runDir"
