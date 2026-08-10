# TerraGIS Release Notes

Version: 1.0.0-beta.6
Date: 2026-04-14
Channel: Private Beta

## Highlights

- Added layer rename improvements:
  - Inline rename via double-click
  - Context-menu rename action
- Added layer grouping workflow:
  - Quick `New Group` in layer panel
  - Drag/drop layer assignment to groups
  - Drag/drop reorder behavior aligned with group context
- Session restore now preserves:
  - Group membership
  - Renamed layer titles
- Improved vector export defaults:
  - Default filename now follows selected layer/source name
- Added layout export presets:
  - New `Export Layout...` action
  - Export formats: PDF, PNG, JPEG
  - Preset fields: format, DPI, page size, orientation, filename pattern
  - Preset management: New/Edit/Delete
  - Preset persistence: custom presets + last-used preset

## Packaging and Runtime

- Private beta app-image generated at:
  - `artifacts/private-beta/1.0.0-beta.6-20260414-012059/app-image/TerraGISBeta`
- MSIX package generated at:
  - `artifacts/private-beta/1.0.0-beta.6-20260414-012059/msix-output/TerraGIS.Beta.msix`
- Test-signing completed with helper scripts:
  - `scripts/create-and-sign-msix.ps1`
  - `scripts/sign-msix.ps1`
- Signing scripts were hardened to avoid plaintext password parameter usage and to handle expected self-signed verify warnings as non-blocking.
- Local smoke launch validation passed on developer machine.

## Certification Progress Update (2026-04-17)

- Package manifest display name aligned with reserved Store identity (`TerraGIS`) to clear package validation checks.
- Submission 1 package uploaded and validated in Partner Center.
- Certification remediation completed for policy findings:
  - Tile assets regenerated from high-resolution logo sources for Store-facing quality requirements.
  - Privacy policy published to public HTTPS URL for Partner Center Properties.
- Package identity version updated to `1.0.1.0` to satisfy uniqueness and version rule compliance (revision segment `0`).
- Submission 2 package is rebuilt/signed and ready for resubmission.

## Validation Summary

- Unit/integration tests: 62 passed, 0 failed.
- Full `mvn clean verify` passes, including JaCoCo coverage threshold.

## Upgrade Notes

- Existing users should verify export presets after first launch.
- For Microsoft Store submission, re-sign the MSIX with production certificate matching Partner Center identity.

## Known Limitations

See `docs/known_limitations_1.0.0-beta.6_2026-04-14.md`.
