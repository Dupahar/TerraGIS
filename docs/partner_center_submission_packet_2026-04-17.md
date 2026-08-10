# TerraGIS Partner Center Submission Packet

Date: 2026-04-17
Target Submission: Product release, Submission 1
Store ID: 9P1ZS0X4NF5Q
Status: Certification failed (attention needed), preparing resubmission

## 0. Certification Report Findings (2026-04-17)

- 10.1.1.11 On Device Tiles:
	- Need clear tile icons; package was flagged for low-resolution tile assets.
- 10.5.1 Personal Information - Privacy Policy:
	- Need a working privacy policy URL in Properties because app accesses on-device documents/files.

Remediation status:
- Tile asset fix: Completed in packaging scripts and rebuilt package.
- Privacy policy URL fix: Completed. Hosted URL is available for Partner Center Properties.

## 1. Identity Values (Must Match Package)

- Package Name: Dupahar.TerraGIS
- Publisher: CN=E8B9DFF8-00AF-4BB0-A12B-23EBCE61FEE8
- Publisher Display Name: Dupahar
- PFN: Dupahar.TerraGIS_80k7jehjbpfe8

## 2. Package Upload

Upload file:
- artifacts/private-beta/1.0.0-beta.6-20260414-012059/msix-output/TerraGIS.Beta.msix

Pre-upload gate:
- File must be production-signed with certificate subject matching publisher CN above.
- Current status in repo: package rebuilt and signed for Submission 2.
- Package identity version for Submission 2: 1.0.1.0
- Version rule compliance: revision segment is 0.

## 3. Pricing and Availability Draft

- Distribution scope: Private audience
- Visibility: Hidden from broad public catalog
- Audience list: pilot testers only
- Markets: Start with intended pilot region set only

## 4. Properties Draft

- Category: Productivity or Developer tools (pick final category in Partner Center)
- App type: Desktop MSIX
- Capabilities declaration: keep minimal required capabilities only
- Notes: Beta release for controlled validation

## 5. Age Ratings (IARC)

- Complete all newly added questionnaire prompts.
- Keep evidence notes in case questionnaire details need audit later.
- Blocker rule: do not submit if any section remains incomplete.

## 6. Store Listing Draft (en-US)

Product title:
- TerraGIS

Short description:
- Modern desktop GIS for vector and raster analysis with AI-assisted workflows.

Full description:
- TerraGIS is a professional desktop GIS platform built for practical spatial analysis workflows. It supports core vector and raster data operations, map interaction, editing, export, and AI-assisted analysis integration. The beta focuses on stability, performance, and role-oriented workflows for survey, hydrology, and environmental analysis teams.

What is new in this beta:
- Layer rename and grouping improvements
- Group/session persistence enhancements
- Better vector export default naming
- Layout export presets for PDF, PNG, and JPEG
- MSIX packaging workflow improvements

Support URL:
- Replace with production support URL before submission

Privacy policy URL:
- Use hosted URL: https://dupahar.github.io/TerraGIS/
- Source file: docs/privacy_policy_terragis_store_en-us.html

## 7. Submission Options

- Keep current completed options unless release policy changed.

## 8. Final Certification Gate

Completed before entering certification:
- Production PFX available and matches publisher CN
- MSIX signed with production certificate
- Pricing and availability complete
- Properties complete
- Age ratings complete
- Store listing complete

Remaining internal validation item:
- Clean-machine matrix completed (Win10 x1, Win11 x2)

## 9. One-Page Operator Sequence (Resubmission)

1. Ensure Properties > Privacy Policy URL points to a public HTTPS policy page.
2. Upload rebuilt package with corrected tile assets (if package was replaced).
3. Re-run full Partner Center validation checks.
4. Submit Submission 2 for certification.
