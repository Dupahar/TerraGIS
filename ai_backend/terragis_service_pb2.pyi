from typing import Any
from google.protobuf.message import Message


class PingRequest(Message):
    client_id: str
    def __init__(self, client_id: str = ...) -> None: ...


class PingResponse(Message):
    message: str
    server_time_epoch_ms: int
    def __init__(self, message: str = ..., server_time_epoch_ms: int = ...) -> None: ...


class SegmentTileRequest(Message):
    model_name: str
    raster_width: int
    raster_height: int
    tile_x: int
    tile_y: int
    tile_width: int
    tile_height: int
    raster_path: str
    def __init__(
        self,
        model_name: str = ...,
        raster_width: int = ...,
        raster_height: int = ...,
        tile_x: int = ...,
        tile_y: int = ...,
        tile_width: int = ...,
        tile_height: int = ...,
        raster_path: str = ...,
    ) -> None: ...


class SegmentTileResponse(Message):
    mask_width: int
    mask_height: int
    packed_mask: bytes
    class_label: str
    confidence: float
    def __init__(
        self,
        mask_width: int = ...,
        mask_height: int = ...,
        packed_mask: bytes = ...,
        class_label: str = ...,
        confidence: float = ...,
    ) -> None: ...


DESCRIPTOR: Any
