"""What an uploaded file really is, and photos rewritten without their metadata.

The type comes from the file's first bytes; the browser's Content-Type and the file name's
extension are ignored, so a renamed .exe is not accepted as a .jpg.

Photos are decoded and encoded again. This drops EXIF, which on phone photos holds the GPS
position where the photo was taken (KVKK: most students are minors), and anything else hidden
in the file. The orientation stored in EXIF is applied to the pixels first, so the photo is not
shown sideways afterwards.
"""

import io
from dataclasses import dataclass

from PIL import Image, ImageOps, UnidentifiedImageError

from app.errors import BusinessRuleError, UnsupportedMediaTypeError
from app.models.enums import FileKind

_SIGNATURES: tuple[tuple[bytes, FileKind], ...] = (
    (b"\xff\xd8\xff", FileKind.JPEG),
    (b"\x89PNG\r\n\x1a\n", FileKind.PNG),
    (b"%PDF-", FileKind.PDF),
)
_PILLOW_FORMATS = {FileKind.JPEG: "JPEG", FileKind.PNG: "PNG", FileKind.WEBP: "WEBP"}
# Modes JPEG can store; anything else (palette, alpha) is converted to RGB.
_JPEG_MODES = {"RGB", "L", "CMYK"}


def detect_kind(data: bytes, allowed: frozenset[FileKind]) -> FileKind:
    kind = _binary_kind(data)
    if kind is None and FileKind.TEXT in allowed and _is_utf8_text(data):
        kind = FileKind.TEXT
    if kind is None or kind not in allowed:
        raise UnsupportedMediaTypeError(
            "UNSUPPORTED_FILE_TYPE",
            "The file type is not accepted for this purpose",
            log_context={"detected": kind.value if kind else "unknown"},
        )
    return kind


def _binary_kind(data: bytes) -> FileKind | None:
    for signature, kind in _SIGNATURES:
        if data.startswith(signature):
            return kind
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return FileKind.WEBP
    return None


def _is_utf8_text(data: bytes) -> bool:
    if b"\x00" in data:
        return False
    try:
        data.decode("utf-8")
    except UnicodeDecodeError:
        return False
    return True


@dataclass(frozen=True)
class ImageLimits:
    max_pixels: int
    max_edge_px: int


def sanitize_image(data: bytes, kind: FileKind, limits: ImageLimits) -> bytes:
    """Re-encodes a photo: orientation applied, too long edges scaled down, metadata dropped."""
    expected_format = _PILLOW_FORMATS[kind]
    try:
        with Image.open(io.BytesIO(data)) as image:
            # Only the header is read so far: the size is known before any pixel is decoded.
            if image.format != expected_format:
                raise _invalid("format_mismatch")
            if image.width * image.height > limits.max_pixels:
                raise _invalid("too_many_pixels")
            icc_profile = image.info.get("icc_profile")
            upright = ImageOps.exif_transpose(image)
            upright.thumbnail((limits.max_edge_px, limits.max_edge_px))
            if kind is FileKind.JPEG and upright.mode not in _JPEG_MODES:
                upright = upright.convert("RGB")
            output = io.BytesIO()
            # No exif= and no pnginfo=: Pillow writes none of the original metadata. The colour
            # profile is kept, it only describes colours.
            options: dict[str, object] = {"icc_profile": icc_profile} if icc_profile else {}
            if kind in {FileKind.JPEG, FileKind.WEBP}:
                options["quality"] = 85
            if kind is FileKind.PNG:
                options["optimize"] = True
            upright.save(output, format=expected_format, **options)
    except BusinessRuleError:
        raise
    except (UnidentifiedImageError, Image.DecompressionBombError, OSError, SyntaxError, ValueError) as exc:
        raise _invalid(type(exc).__name__) from exc
    return output.getvalue()


def _invalid(reason: str) -> BusinessRuleError:
    return BusinessRuleError(
        "INVALID_IMAGE", "The image could not be decoded", log_context={"reason": reason}
    )
