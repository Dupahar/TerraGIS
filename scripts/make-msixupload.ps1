$msixOutput = "C:\Users\mahaj\Downloads\GIS\TerraGIS\artifacts\private-beta\1.0.0-beta.6-20260414-012059\msix-output"
$src = Join-Path $msixOutput "TerraGIS.Beta.msix"
$ready = "C:\Users\mahaj\Downloads\GIS\TerraGIS\artifacts\private-beta\ready-to-share-20260508"
$zip = Join-Path $ready "TerraGIS.Beta.msixupload.zip"
Compress-Archive -Path $src -DestinationPath $zip -Force
Rename-Item -Path $zip -NewName "TerraGIS.Beta.msixupload" -Force
Copy-Item -Path $src -Destination (Join-Path $ready "TerraGIS.Beta.msix") -Force
Write-Host "Created msixupload and copied msix to ready-to-share"