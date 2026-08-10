$certPassword = ConvertTo-SecureString '149120' -AsPlainText -Force
Set-Location 'C:\Users\mahaj\Downloads\GIS\TerraGIS'
.\scripts\create-and-sign-msix.ps1 -AppImagePath 'artifacts/private-beta/1.0.0-beta.6-20260414-012059/app-image/TerraGISBeta' -OutputDir 'artifacts/private-beta/1.0.0-beta.6-20260414-012059/msix-output' -CertificatePath 'artifacts/private-beta/store-signing.pfx' -CertificatePassword $certPassword
