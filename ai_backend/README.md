# TerraGIS Python AI Backend

This backend serves TerraGIS `SegmentTile` requests over gRPC and runs real model inference using your `.pth` file.

## 1) Setup

```powershell
cd TerraGIS\ai_backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
.\generate_proto.ps1
```

## 2) Configure model

Set environment variables before running:

```powershell
$env:TERRAGIS_MODEL_PATH = "C:\Users\mahaj\Downloads\GIS\Models\Feature Exraction\best_model_multiclass.pth"
$env:TERRAGIS_NUM_CLASSES = "8"
$env:TERRAGIS_TARGET_CLASS = "-1"
$env:TERRAGIS_PORT = "6565"
```

Notes:
- `TERRAGIS_TARGET_CLASS=-1` means return mask for all non-background classes.
- Set `TERRAGIS_TARGET_CLASS` to a specific class index (for example `1`) to return one class only.

## 3) Run server

```powershell
python .\server.py
```

## 4) Use with TerraGIS app

1. Start this backend.
2. Launch TerraGIS desktop.
3. Open a raster `.tif`.
4. Click `AI Segment Raster`.

The Java client now passes `raster_path` and tile bounds to backend `SegmentTile`, so inference is performed on real raster pixels.

## 5) TerraAI Orchestrator Server (SubmitJob/GetJobStatus/CancelJob)

TerraGIS also supports the TerraAI orchestrator contract from `terra_ai_service.proto`.

After running `generate_proto.ps1`, start the orchestrator with:

```powershell
python .\terra_ai_orchestrator_server.py
```

Optional environment variables:

```powershell
$env:TERRAGIS_TERRA_AI_PORT = "50051"
$env:TERRAGIS_TERRA_AI_OUTPUT_DIR = "C:\path\to\TerraAI\Output"
$env:TERRAGIS_TERRA_AI_PHASE_SECONDS = "5"
```

Notes:
- The orchestrator publishes artifact URIs before marking a job `SUCCEEDED` to avoid success-without-artifacts race conditions.
- If `TERRAGIS_TERRA_AI_OUTPUT_DIR` is not set, artifacts are written under `./Output/Job_<job_id>`.
