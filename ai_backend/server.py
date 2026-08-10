# pyright: reportAttributeAccessIssue=false
import logging
import os
import time
from concurrent import futures

import grpc
import numpy as np
import rasterio
import torch
from torchvision.models.segmentation import deeplabv3_resnet50

import terragis_service_pb2
import terragis_service_pb2_grpc


LOGGER = logging.getLogger("terragis-ai-backend")


def _resolve_state_dict(checkpoint):
    if not isinstance(checkpoint, dict):
        return checkpoint

    for key in ("model_state_dict", "state_dict", "model"):
        if key in checkpoint:
            return checkpoint[key]

    return checkpoint


def _strip_prefixes(state_dict):
    cleaned = {}
    for key, value in state_dict.items():
        new_key = key
        for prefix in ("module.", "model."):
            if new_key.startswith(prefix):
                new_key = new_key[len(prefix):]
        cleaned[new_key] = value
    return cleaned


class SegmentationModel:
    def __init__(self, model_path: str, num_classes: int, device: str):
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Model file not found: {model_path}")

        self.device = torch.device(device)
        self.model = deeplabv3_resnet50(weights=None, weights_backbone=None, num_classes=num_classes)

        checkpoint = torch.load(model_path, map_location=self.device)
        state_dict = _strip_prefixes(_resolve_state_dict(checkpoint))
        missing, unexpected = self.model.load_state_dict(state_dict, strict=False)

        if missing:
            LOGGER.warning("Missing model keys: %s", missing[:10])
        if unexpected:
            LOGGER.warning("Unexpected model keys: %s", unexpected[:10])

        self.model.eval()
        self.model.to(self.device)

    @torch.no_grad()
    def predict_mask(self, rgb: np.ndarray, target_class: int):
        # rgb shape: H x W x 3 uint8
        tensor = torch.from_numpy(rgb).float().permute(2, 0, 1) / 255.0
        tensor = tensor.unsqueeze(0).to(self.device)

        outputs = self.model(tensor)
        logits = outputs["out"]  # [1, C, H, W]
        probs = torch.softmax(logits, dim=1)
        pred = torch.argmax(probs, dim=1).squeeze(0)

        if target_class >= 0:
            mask = pred == target_class
            class_label = f"class_{target_class}"
            confidence = probs[:, target_class, :, :].mean().item()
        else:
            # Treat all non-background classes as foreground.
            mask = pred > 0
            class_label = "foreground_non_background"
            selected = probs[:, 1:, :, :] if probs.shape[1] > 1 else probs
            confidence = selected.max(dim=1).values.mean().item()

        return mask.cpu().numpy().astype(np.uint8), class_label, float(confidence)


def _read_tile_rgb(raster_path: str, tile_x: int, tile_y: int, tile_width: int, tile_height: int):
    with rasterio.open(fp=raster_path) as ds:
        window = ((tile_y, tile_y + tile_height), (tile_x, tile_x + tile_width))
        bands = [1, 2, 3] if ds.count >= 3 else [1]
        arr = ds.read(
            bands,
            window=window,
            out_shape=(len(bands), tile_height, tile_width),
            boundless=True,
            fill_value=0,
        )

    if arr.shape[0] == 1:
        arr = np.repeat(arr, 3, axis=0)

    rgb = np.transpose(arr, (1, 2, 0))

    if rgb.dtype != np.uint8:
        finite = np.isfinite(rgb)
        if not np.any(finite):
            rgb = np.zeros_like(rgb, dtype=np.uint8)
        else:
            lo = np.nanpercentile(rgb[finite], 2)
            hi = np.nanpercentile(rgb[finite], 98)
            if hi <= lo:
                hi = lo + 1.0
            rgb = np.clip((rgb - lo) * (255.0 / (hi - lo)), 0, 255).astype(np.uint8)

    return rgb


class TerraApiService(terragis_service_pb2_grpc.TerraApiServiceServicer):
    def __init__(self, model: SegmentationModel, target_class: int):
        self.model = model
        self.target_class = target_class

    def Ping(self, request, context):
        return terragis_service_pb2.PingResponse(  # type: ignore[attr-defined]
            message="pong:python-ai-backend",
            server_time_epoch_ms=int(time.time() * 1000),
        )

    def SegmentTile(self, request, context):
        raster_path = request.raster_path.strip()
        if not raster_path:
            context.abort(grpc.StatusCode.INVALID_ARGUMENT, "SegmentTile requires raster_path")

        if not os.path.exists(raster_path):
            context.abort(grpc.StatusCode.NOT_FOUND, f"Raster path not found: {raster_path}")

        try:
            rgb = _read_tile_rgb(
                raster_path,
                request.tile_x,
                request.tile_y,
                request.tile_width,
                request.tile_height,
            )
            mask, class_label, confidence = self.model.predict_mask(rgb, self.target_class)
        except Exception as ex:
            LOGGER.exception("SegmentTile inference failed")
            context.abort(grpc.StatusCode.INTERNAL, f"Inference failed: {ex}")

        return terragis_service_pb2.SegmentTileResponse(  # type: ignore[attr-defined]
            mask_width=request.tile_width,
            mask_height=request.tile_height,
            packed_mask=mask.tobytes(order="C"),
            class_label=class_label,
            confidence=confidence,
        )


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")

    model_path = os.getenv("TERRAGIS_MODEL_PATH", "").strip()
    if not model_path:
        raise RuntimeError("Set TERRAGIS_MODEL_PATH to your .pth model file")

    num_classes = int(os.getenv("TERRAGIS_NUM_CLASSES", "8"))
    target_class = int(os.getenv("TERRAGIS_TARGET_CLASS", "-1"))
    port = int(os.getenv("TERRAGIS_PORT", "6565"))
    device = os.getenv("TERRAGIS_DEVICE", "cuda" if torch.cuda.is_available() else "cpu")

    LOGGER.info("Loading model from %s on %s", model_path, device)
    model = SegmentationModel(model_path=model_path, num_classes=num_classes, device=device)

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    terragis_service_pb2_grpc.add_TerraApiServiceServicer_to_server(
        TerraApiService(model=model, target_class=target_class),
        server,
    )
    server.add_insecure_port(f"[::]:{port}")
    server.start()

    LOGGER.info("TerraGIS AI backend started on port %s", port)
    LOGGER.info("Target class mode: %s", "non-background" if target_class < 0 else str(target_class))
    server.wait_for_termination()


if __name__ == "__main__":
    main()
