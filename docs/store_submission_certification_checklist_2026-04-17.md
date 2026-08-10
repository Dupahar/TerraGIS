# TerraGIS Store Certification Checklist (Submission 1)

Date: 2026-04-17
Product: TerraGIS
Store Type: MSIX or PWA app
Submission State: In draft (post-certification failure remediation)
Store ID: 9P1ZS0X4NF5Q

## 1. Locked Product Identity (Do Not Change)

- Package/Identity/Name: Dupahar.TerraGIS
- Package/Identity/Publisher: CN=E8B9DFF8-00AF-4BB0-A12B-23EBCE61FEE8
- Package/Properties/PublisherDisplayName: Dupahar
- Package Family Name (PFN): Dupahar.TerraGIS_80k7jehjbpfe8
- Package SID: S-1-15-2-908463941-603749010-1507291346-1530448715-276524726-3833623966-2654380650

## 2. Current Gate Status

- Packages: COMPLETE (validated)
- Pricing and availability: COMPLETE
- Properties: COMPLETE
- Age ratings: COMPLETE
- Store listings: COMPLETE
- Submission options: COMPLETE
- Certification: FAILED (attention needed, 2026-04-17)

## 2a. Certification Findings (Submission 1)

- Policy 10.1.1.11 On Device Tiles:
   - Issue: tile icon quality not acceptable (distorted/blurry/low resolution).
   - Root cause: package assets were incomplete for DPI scale factors, which can trigger blurry tile rendering on tested devices.
   - Fix applied: packaging scripts now generate full high-DPI tile/logo variants from `src/main/resources/images/dupahar_logo.png`, including Square44x44, Square150x150, Wide310x150, Square310x310 plus `scale-100/125/150/200/400` variants.
   - Verification: manifest now references wide and large tile logos; assets are generated with aspect-ratio-preserving resize.

- Policy 10.1.2.10 Functionality:
   - Issue: app appeared unresponsive for too long after launch.
   - Root cause: startup initialization tasks (config, diagnostics, license check) could run before visible UI feedback was fully available.
   - Fix applied: JavaFX startup now shows splash immediately, runs heavy initialization in a background startup task, and updates visible loading text/progress while startup work is in progress.
   - Verification: launch now displays active progress from the first frame until welcome screen handoff.

- Policy 10.5.1 Personal Information - Privacy Policy:
   - Issue: product accesses personal information and requires working privacy policy URL.
   - Fix completed: privacy policy page published at public HTTPS URL.
   - URL to set in Partner Center Properties > Privacy Policy URL: https://dupahar.github.io/TerraGIS/

## 3. Certification Context

Submission 1 failed certification on 2026-04-17. Remediation is complete and Submission 2 package is prepared:

- Required cert subject: CN=E8B9DFF8-00AF-4BB0-A12B-23EBCE61FEE8
- Current local cert found: store-signing.pfx and installed cert in CurrentUser\My
- Current package signing result: Successfully signed (signtool sign exit code 0, 2026-04-17)
- Current package verification result: Local trust-chain warning (untrusted root) may appear on build machine
- Resubmission package version: 1.0.1.0 (revision segment set to 0 for Store compliance)

## 4. Production Signing Command (Reference)

Run from TerraGIS root:

```powershell
$certPassword = Read-Host -AsSecureString
.\scripts\create-and-sign-msix.ps1 `
  -AppImagePath "artifacts/private-beta/1.0.0-beta.6-20260414-012059/app-image/TerraGISBeta" `
  -OutputDir "artifacts/private-beta/1.0.0-beta.6-20260414-012059/msix-output" `
  -CertificatePath "C:\path\to\prod-cert.pfx" `
  -CertificatePassword $certPassword
```

Alternative for existing MSIX only:

```powershell
$certPassword = Read-Host -AsSecureString
.\scripts\sign-msix.ps1 `
  -MsixPath "C:\Users\mahaj\Downloads\GIS\TerraGIS\artifacts\private-beta\1.0.0-beta.6-20260414-012059\msix-output\TerraGIS.Beta.msix" `
  -CertDir "C:\Users\mahaj\Downloads\GIS\TerraGIS\artifacts\private-beta\1.0.0-beta.6-20260414-012059\msix-output" `
  -CertificatePassword $certPassword
```

## 5. Validation Required During Certification Window

- Verify MSIX signature reports valid chain for production cert.
- Complete clean-machine matrix:
  - Windows 10 x1
  - Windows 11 x2
- Confirm install, launch, basic GIS flow, export, and diagnostics bundle.
- Update release evidence note in docs/release_evidence.

Current package path:

```text
artifacts/private-beta/1.0.0-beta.6-20260414-012059/msix-output/TerraGIS.Beta.msix
```

Current package identity version:

```text
1.0.1.0
```

Verification command:

```powershell
$msix = "C:\Users\mahaj\Downloads\GIS\TerraGIS\artifacts\private-beta\1.0.0-beta.6-20260414-012059\msix-output\TerraGIS.Beta.msix"
signtool.exe verify /pa /all "$msix"
```

## 6. Partner Center Submission Sequence (Submission 2)

1. Packages:
   - Upload production-signed MSIX package.
   - Confirm package identity exactly matches locked Store identity.
2. Pricing and availability:
   - Set private audience availability.
   - Configure testers or distribution strategy.
3. Properties:
   - Verify app category and declarations.
4. Age ratings:
   - Complete IARC and new required questionnaire items.
5. Store listings:
   - Populate listing text, screenshots, and support/privacy URLs.
6. Review submission options:
   - Keep current completed settings unless release policy changed.
7. Submit Submission 2 to certification.

## 7. Ready-to-Check Items

- [x] Production PFX obtained (matching CN publisher)
- [x] MSIX production-signed
- [ ] Signature verification clean (blocked on local root trust for this self-generated cert)
- [ ] Clean-machine validation matrix complete
- [x] Release evidence updated
- [x] Pricing and availability complete
- [x] Properties complete
- [x] Age ratings complete
- [x] Store listings complete
- [ ] Submit Submission 2 to certification clicked
