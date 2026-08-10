# Direct sign MSIX using store certificate
param(
    [string]$MsixPath = "c:\Users\mahaj\Downloads\GIS\TerraGIS\artifacts\private-beta\1.0.0-beta.6-20260414-012059\msix-output\TerraGIS.Beta.msix"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Direct MSIX Signing ==="
Write-Host "MSIX: $MsixPath"
Write-Host ""

# Add signtool to PATH
$env:Path = "C:\Program Files (x86)\Windows Kits\10\bin\10.0.26100.0\x64;$env:Path"

# Sign using the certificate store directly  
Write-Host "[1/2] Signing MSIX with store certificate..."
$signCmd = @(
    "signtool.exe",
    "sign",
    "/s", "My",
    "/n", "CN=TerraGIS.Beta.Test",
    "/fd", "SHA256",
    "/v",
    ('"' + $MsixPath + '"')
) -join " "

Write-Host "Command: signtool sign /s My /n CN=TerraGIS.Beta.Test /fd SHA256 /v MSIX"
Write-Host ""

& cmd /c $signCmd 2>&1

Write-Host ""
Write-Host "[2/2] Verifying signature..."
$verifyCmd = "signtool.exe verify /pa " + ('"' + $MsixPath + '"')
& cmd /c $verifyCmd 2>&1

Write-Host ""
Write-Host "=== Signing Complete ==="
Write-Host "Signed MSIX: $MsixPath"
