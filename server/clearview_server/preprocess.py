"""上传乳腺超声影像的解码与源图保真预处理。

超声为灰度影像，仍统一转成 sRGB JPEG 以便 Android 端直接展示；
只限制解码尺寸，不做极性翻转或白边裁剪，避免改变病灶回声表现。
"""
import io
from dataclasses import dataclass

import numpy as np
from PIL import Image, ImageOps

PREPROCESS_VERSION = "busi-rgb-source-v1"


class UnsupportedImageError(ValueError):
    pass


class CollageImageError(ValueError):
    """保留异常类型供旧调用方捕获；乳腺超声链路不再做拼图判定。"""


def decode_to_rgb8(data: bytes) -> Image.Image:
    try:
        with Image.open(io.BytesIO(data)) as original:
            if min(original.size) < 64 or original.width * original.height > 40_000_000:
                raise UnsupportedImageError("图像尺寸不支持（最小边64，最多4000万像素）")
            original.load()
            img = ImageOps.exif_transpose(original).convert("RGB")
            img.info.clear()
    except UnsupportedImageError:
        raise
    except (OSError, ValueError, Image.DecompressionBombError) as exc:
        raise UnsupportedImageError("无法解码的图像") from exc
    return img


def canonical_source(data: bytes) -> tuple[bytes, Image.Image]:
    """The bytes served to viewers are also the exact decoded inference input."""
    image = decode_to_rgb8(data)
    output = io.BytesIO()
    image.save(output, format="JPEG", quality=92, exif=b"")
    encoded = output.getvalue()
    return encoded, decode_to_rgb8(encoded)


@dataclass(frozen=True)
class PreparedImage:
    rgb: np.ndarray
    width: int
    height: int

    def geometry(self) -> dict:
        bounds = [0, 0, self.width, self.height]
        return {
            "width": self.width, "height": self.height,
            "trimBounds": bounds,
            "anatomyBounds": bounds,
            "coordinateSystem": "SOURCE_PIXELS",
        }


def prepare(img: Image.Image) -> PreparedImage:
    rgb = np.asarray(img.convert("RGB"), dtype=np.uint8)
    return PreparedImage(rgb=rgb.copy(), width=img.width, height=img.height)


def center_crop_offsets(height: int, width: int):
    crop = min(height, width)
    start_y = height // 2 - crop // 2
    start_x = width // 2 - crop // 2
    return start_y, start_x, crop


def paste_square_into_source(square: np.ndarray, height: int, width: int) -> np.ndarray:
    start_y, start_x, crop = center_crop_offsets(height, width)
    canvas = np.zeros((height, width), dtype=square.dtype)
    canvas[start_y:start_y + crop, start_x:start_x + crop] = square
    return canvas
