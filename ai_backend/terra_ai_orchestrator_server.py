#!/usr/bin/env python3
"""
TERRA.AI gRPC Orchestrator Server

Implements TerraAiOrchestrator service used by TerraGIS desktop for
asynchronous job submission, polling, cancellation, and artifact reporting.

Key reliability behavior:
- Artifact list is populated before status transitions to SUCCEEDED.
- Shared job state is guarded by a lock to avoid race conditions.
"""

from __future__ import annotations

import json
import importlib
import logging
import os
import subprocess
import sys
import threading
import time
import traceback
import uuid
from concurrent import futures
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import grpc


def _generate_orchestrator_proto_stubs() -> None:
    backend_dir = Path(__file__).resolve().parent
    repo_root = backend_dir.parent
    proto_file = repo_root / "src" / "main" / "proto" / "terra_ai_service.proto"

    if not proto_file.is_file():
        raise FileNotFoundError(f"Missing proto file: {proto_file}")

    cmd = [
        sys.executable,
        "-m",
        "grpc_tools.protoc",
        f"-I{proto_file.parent}",
        f"--python_out={backend_dir}",
        f"--grpc_python_out={backend_dir}",
        str(proto_file),
    ]

    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(
            "Failed to generate terra_ai_service protobuf stubs. "
            f"stdout={result.stdout.strip()} stderr={result.stderr.strip()}"
        )


def _load_orchestrator_proto_modules() -> tuple[Any, Any]:
    backend_dir = Path(__file__).resolve().parent
    pb2_file = backend_dir / "terra_ai_service_pb2.py"
    pb2_grpc_file = backend_dir / "terra_ai_service_pb2_grpc.py"

    if str(backend_dir) not in sys.path:
        sys.path.insert(0, str(backend_dir))

    if not pb2_file.exists() or not pb2_grpc_file.exists():
        logging.getLogger("terra-ai-orchestrator").warning(
            "terra_ai_service stubs are missing; generating automatically"
        )
        _generate_orchestrator_proto_stubs()

    try:
        pb2 = importlib.import_module("terra_ai_service_pb2")
        pb2_grpc = importlib.import_module("terra_ai_service_pb2_grpc")
        return pb2, pb2_grpc
    except ModuleNotFoundError:
        # One more attempt after regeneration in case files were partially present.
        _generate_orchestrator_proto_stubs()
        pb2 = importlib.import_module("terra_ai_service_pb2")
        pb2_grpc = importlib.import_module("terra_ai_service_pb2_grpc")
        return pb2, pb2_grpc


terra_ai_service_pb2, terra_ai_service_pb2_grpc = _load_orchestrator_proto_modules()


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
LOGGER = logging.getLogger("terra-ai-orchestrator")


PHASES = [
    "Phase 1: Cropland Extraction",
    "Phase 2: Terrain Extraction",
    "Phase 3: Bare Soil Index",
    "Phase 4: QRF Model Training",
    "Phase 5: SOC Prediction",
    "Phase 6: VM0042 Export",
]


JOBS: dict[str, dict[str, Any]] = {}
JOBS_LOCK = threading.Lock()


def current_utc_time() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _read_env_int(name: str, default: int) -> int:
    raw = os.getenv(name, "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def _update_job(job_id: str, **changes: Any) -> None:
    with JOBS_LOCK:
        job = JOBS.get(job_id)
        if not job:
            return
        job.update(changes)


def _resolve_output_base_dir(metadata: dict[str, str]) -> Path:
    md_dir = (metadata or {}).get("TERRAGIS_TERRA_AI_OUTPUT_DIR", "").strip()
    env_dir = os.getenv("TERRAGIS_TERRA_AI_OUTPUT_DIR", "").strip()
    selected = md_dir or env_dir
    if selected:
        return Path(selected).expanduser().resolve()
    return (Path.cwd() / "Output").resolve()


@dataclass
class JobArtifacts:
    raster_tif: Path | None
    soc_polygons_geojson: Path
    provenance_manifest: Path

    def to_proto_items(self) -> list[dict[str, str]]:
        items: list[dict[str, str]] = []
        if self.raster_tif is not None:
            items.append(
                {
                    "name": "SOC Prediction Raster",
                    "uri": self.raster_tif.resolve().as_uri(),
                    "media_type": "image/tiff",
                }
            )
        items.append(
            {
                "name": "SOC Prediction Polygons",
                "uri": self.soc_polygons_geojson.resolve().as_uri(),
                "media_type": "application/geo+json",
            }
        )
        items.append(
            {
                "name": "Provenance Manifest",
                "uri": self.provenance_manifest.resolve().as_uri(),
                "media_type": "application/json",
            }
        )
        return items


class JobThread(threading.Thread):
    def __init__(self, job_id: str, request: Any):
        super().__init__(daemon=True)
        self.job_id = job_id
        self.request = request
        self.total_phases = len(PHASES)

        self.base_output_dir = _resolve_output_base_dir(dict(request.metadata))
        self.job_output_dir = self.base_output_dir / f"Job_{job_id}"
        self.job_output_dir.mkdir(parents=True, exist_ok=True)

    def _is_cancel_requested(self) -> bool:
        with JOBS_LOCK:
            job = JOBS.get(self.job_id)
            return bool(job and job.get("cancel_requested"))

    def _run_phases(self) -> None:
        phase_sleep_s = max(1, _read_env_int("TERRAGIS_TERRA_AI_PHASE_SECONDS", 5))

        _update_job(
            self.job_id,
            status="RUNNING",
            phase_total=self.total_phases,
            updated_at_utc=current_utc_time(),
        )

        for i, phase_name in enumerate(PHASES):
            if self._is_cancel_requested():
                _update_job(
                    self.job_id,
                    status="CANCELLED",
                    message="Job was cancelled by the user.",
                    updated_at_utc=current_utc_time(),
                )
                LOGGER.info("Job %s cancelled", self.job_id)
                return

            _update_job(
                self.job_id,
                phase_index=i + 1,
                phase=phase_name,
                message=f"Starting {phase_name}...",
                progress_percent=int((i / self.total_phases) * 100),
                updated_at_utc=current_utc_time(),
            )
            LOGGER.info("Job %s: %s", self.job_id, phase_name)

            ticks = max(2, phase_sleep_s * 2)
            for t in range(ticks):
                if self._is_cancel_requested():
                    _update_job(
                        self.job_id,
                        status="CANCELLED",
                        message="Job was cancelled by the user.",
                        updated_at_utc=current_utc_time(),
                    )
                    LOGGER.info("Job %s cancelled", self.job_id)
                    return
                time.sleep(0.5)
                progress = int(((i + (t / ticks)) / self.total_phases) * 100)
                _update_job(
                    self.job_id,
                    progress_percent=max(0, min(99, progress)),
                    updated_at_utc=current_utc_time(),
                )

    def _create_artifacts(self) -> JobArtifacts:
        raster_target = self.job_output_dir / "soc_prediction_10m.tif"
        geojson_target = self.job_output_dir / "soc_prediction_10m.geojson"
        manifest_target = self.job_output_dir / "provenance_manifest.json"

        # Prefer a real sample TIFF if available, but keep server dependency-light.
        sample_tif = Path.cwd() / "Test_Output" / "_shared" / "soc_prediction_10m.tif"
        if sample_tif.exists() and sample_tif.is_file():
            raster_target.write_bytes(sample_tif.read_bytes())
            raster_path: Path | None = raster_target
        else:
            raster_path = None

        # Always create an importable vector artifact so desktop can load results.
        feature_collection = {
            "type": "FeatureCollection",
            "features": [
                {
                    "type": "Feature",
                    "properties": {
                        "job_id": self.job_id,
                        "soc_mean": 1.8,
                        "confidence": 0.92,
                        "source": "mock",
                    },
                    "geometry": {
                        "type": "Polygon",
                        "coordinates": [
                            [
                                [77.0000, 28.5000],
                                [77.0100, 28.5000],
                                [77.0100, 28.5100],
                                [77.0000, 28.5100],
                                [77.0000, 28.5000],
                            ]
                        ],
                    },
                }
            ],
        }
        geojson_target.write_text(json.dumps(feature_collection, indent=2), encoding="utf-8")

        manifest_target.write_text(
            json.dumps(
                {
                    "job_id": self.job_id,
                    "model_version": os.getenv("TERRAGIS_TERRA_AI_MODEL_VERSION", "v1.0"),
                    "seed_hash": "abc123def456",
                    "source_timestamp": current_utc_time(),
                    "output_dir": str(self.job_output_dir),
                    "request": {
                        "request_id": self.request.request_id,
                        "model_profile": self.request.model_profile,
                        "input_raster_uri": self.request.input_raster_uri,
                    },
                },
                indent=2,
            ),
            encoding="utf-8",
        )

        return JobArtifacts(raster_tif=raster_path, soc_polygons_geojson=geojson_target, provenance_manifest=manifest_target)

    def run(self) -> None:
        try:
            self._run_phases()

            with JOBS_LOCK:
                final_status = JOBS.get(self.job_id, {}).get("status")
            if final_status == "CANCELLED":
                return

            _update_job(
                self.job_id,
                phase="Finalizing outputs",
                message="Writing output artifacts...",
                progress_percent=99,
                updated_at_utc=current_utc_time(),
            )

            artifacts = self._create_artifacts()

            # Important: publish artifacts before setting SUCCEEDED.
            _update_job(
                self.job_id,
                artifacts=artifacts.to_proto_items(),
                status="SUCCEEDED",
                phase="Completed",
                progress_percent=100,
                message="SOC generation and export complete.",
                updated_at_utc=current_utc_time(),
            )
            LOGGER.info("Job %s completed successfully", self.job_id)

        except Exception as exc:
            LOGGER.exception("Job %s failed", self.job_id)
            _update_job(
                self.job_id,
                status="FAILED",
                message="Pipeline execution failed.",
                error={
                    "code": "ERR_PIPELINE_CRASH",
                    "title": "Pipeline Execution Error",
                    "detail": f"{exc}\n{traceback.format_exc(limit=3)}",
                    "retryable": True,
                },
                updated_at_utc=current_utc_time(),
            )


class TerraAiOrchestratorServicer(terra_ai_service_pb2_grpc.TerraAiOrchestratorServicer):
    def SubmitJob(self, request, context):
        job_id = str(uuid.uuid4())
        req_preview = (request.aoi_wkt or "")[:30]
        LOGGER.info("SubmitJob received (aoi preview=%r) -> job_id=%s", req_preview, job_id)

        with JOBS_LOCK:
            JOBS[job_id] = {
                "job_id": job_id,
                "status": "QUEUED",
                "phase": "Pending",
                "phase_index": 0,
                "phase_total": len(PHASES),
                "progress_percent": 0,
                "message": "Job is queued for execution.",
                "updated_at_utc": current_utc_time(),
                "artifacts": [],
                "error": None,
                "cancel_requested": False,
            }

        JobThread(job_id, request).start()

        return terra_ai_service_pb2.JobSubmissionResponse(
            job_id=job_id,
            accepted_at_utc=current_utc_time(),
            status="ACCEPTED",
        )

    def GetJobStatus(self, request, context):
        job_id = request.job_id
        with JOBS_LOCK:
            job = JOBS.get(job_id)

        if not job:
            context.set_code(grpc.StatusCode.NOT_FOUND)
            context.set_details(f"Job {job_id} not found")
            return terra_ai_service_pb2.JobStatusResponse()

        response = terra_ai_service_pb2.JobStatusResponse(
            job_id=job["job_id"],
            status=job["status"],
            phase=job["phase"],
            phase_index=job["phase_index"],
            phase_total=job["phase_total"],
            progress_percent=job["progress_percent"],
            message=job["message"],
            updated_at_utc=job["updated_at_utc"],
        )

        for artifact in job.get("artifacts", []):
            response.artifacts.append(
                terra_ai_service_pb2.ArtifactReference(
                    name=artifact.get("name", ""),
                    uri=artifact.get("uri", ""),
                    media_type=artifact.get("media_type", ""),
                )
            )

        err = job.get("error")
        if err:
            response.error.CopyFrom(
                terra_ai_service_pb2.ErrorInfo(
                    code=err.get("code", "ERR_UNKNOWN"),
                    title=err.get("title", "Unknown Error"),
                    detail=err.get("detail", ""),
                    retryable=bool(err.get("retryable", False)),
                )
            )

        return response

    def CancelJob(self, request, context):
        job_id = request.job_id
        with JOBS_LOCK:
            job = JOBS.get(job_id)
            if not job:
                return terra_ai_service_pb2.CancelResponse(
                    job_id=job_id,
                    cancelled=False,
                    message="Job not found.",
                )

            if job["status"] in {"SUCCEEDED", "FAILED", "CANCELLED"}:
                return terra_ai_service_pb2.CancelResponse(
                    job_id=job_id,
                    cancelled=False,
                    message=f"Job already finished with status: {job['status']}",
                )

            job["cancel_requested"] = True
            job["updated_at_utc"] = current_utc_time()

        return terra_ai_service_pb2.CancelResponse(
            job_id=job_id,
            cancelled=True,
            message="Cancel signal sent to executing thread.",
        )


def serve() -> None:
    port = _read_env_int("TERRAGIS_TERRA_AI_PORT", 50051)
    max_workers = max(4, _read_env_int("TERRAGIS_TERRA_AI_MAX_WORKERS", 10))

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=max_workers))
    terra_ai_service_pb2_grpc.add_TerraAiOrchestratorServicer_to_server(
        TerraAiOrchestratorServicer(),
        server,
    )
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    LOGGER.info("TERRA.AI Orchestrator server started on port %s", port)

    try:
        server.wait_for_termination()
    except KeyboardInterrupt:
        LOGGER.info("Shutting down orchestrator server")
        server.stop(0)


if __name__ == "__main__":
    serve()
