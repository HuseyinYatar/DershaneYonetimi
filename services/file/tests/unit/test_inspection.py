import io

import pytest
from PIL import Image

from app.errors import BusinessRuleError, UnsupportedMediaTypeError
from app.files.inspection import ImageLimits, detect_kind, sanitize_image
from app.models.enums import FileKind
from tests.conftest import PDF_BYTES, png_bytes

DOCUMENTS = frozenset({FileKind.JPEG, FileKind.PNG, FileKind.WEBP, FileKind.PDF})
LIMITS = ImageLimits(max_pixels=40_000_000, max_edge_px=2560)

# EXIF tag numbers
ORIENTATION = 0x0112
GPS_IFD = 0x8825
GPS_LATITUDE = 2


def image_bytes(fmt: str, size: tuple[int, int] = (40, 30), *, exif: Image.Exif | None = None) -> bytes:
    output = io.BytesIO()
    options = {"exif": exif} if exif is not None else {}
    Image.new("RGB", size, (10, 120, 200)).save(output, format=fmt, **options)
    return output.getvalue()


def exif_with_gps_and_orientation(orientation: int = 1) -> Image.Exif:
    exif = Image.Exif()
    exif[ORIENTATION] = orientation
    exif.get_ifd(GPS_IFD)[GPS_LATITUDE] = (40.0, 46.0, 30.0)  # Sakarya
    return exif


class TestDetectKind:
    @pytest.mark.parametrize(
        ("data", "kind"),
        [
            (image_bytes("JPEG"), FileKind.JPEG),
            (png_bytes(), FileKind.PNG),
            (image_bytes("WEBP"), FileKind.WEBP),
            (PDF_BYTES, FileKind.PDF),
        ],
    )
    def test_documents_are_recognised_by_their_first_bytes(self, data: bytes, kind: FileKind) -> None:
        assert detect_kind(data, DOCUMENTS) is kind

    def test_a_renamed_program_is_not_a_photo(self) -> None:
        with pytest.raises(UnsupportedMediaTypeError) as raised:
            detect_kind(b"MZ\x90\x00\x03" + bytes(100), DOCUMENTS)
        assert raised.value.code == "UNSUPPORTED_FILE_TYPE"

    def test_text_is_accepted_only_where_text_is_allowed(self) -> None:
        line = b"12345678 A CBDAEDCBA\n"
        assert detect_kind(line, frozenset({FileKind.TEXT})) is FileKind.TEXT
        with pytest.raises(UnsupportedMediaTypeError):
            detect_kind(line, DOCUMENTS)

    def test_a_pdf_is_not_an_optical_result(self) -> None:
        with pytest.raises(UnsupportedMediaTypeError):
            detect_kind(PDF_BYTES, frozenset({FileKind.TEXT}))

    @pytest.mark.parametrize("data", [b"abc\x00def", "çok güzel".encode("latin-1")])
    def test_binary_or_non_utf8_content_is_not_text(self, data: bytes) -> None:
        with pytest.raises(UnsupportedMediaTypeError):
            detect_kind(data, frozenset({FileKind.TEXT}))

    def test_turkish_utf8_text_is_text(self) -> None:
        assert detect_kind("Öğrenci Ş\n".encode(), frozenset({FileKind.TEXT})) is FileKind.TEXT


class TestSanitizeImage:
    def test_gps_position_is_removed_from_a_phone_photo(self) -> None:
        original = image_bytes("JPEG", exif=exif_with_gps_and_orientation())
        with Image.open(io.BytesIO(original)) as before:
            assert before.getexif().get_ifd(GPS_IFD)

        cleaned = sanitize_image(original, FileKind.JPEG, LIMITS)

        with Image.open(io.BytesIO(cleaned)) as after:
            assert after.format == "JPEG"
            assert not after.getexif().get_ifd(GPS_IFD)
            assert "exif" not in after.info

    def test_orientation_is_applied_to_the_pixels_before_exif_is_dropped(self) -> None:
        # Orientation 6: the camera was held upright, the stored pixels are landscape.
        original = image_bytes("JPEG", size=(60, 20), exif=exif_with_gps_and_orientation(orientation=6))

        cleaned = sanitize_image(original, FileKind.JPEG, LIMITS)

        with Image.open(io.BytesIO(cleaned)) as after:
            assert after.size == (20, 60)

    def test_a_long_edge_is_scaled_down(self) -> None:
        cleaned = sanitize_image(png_bytes((300, 100)), FileKind.PNG, ImageLimits(40_000_000, 150))
        with Image.open(io.BytesIO(cleaned)) as after:
            assert after.size == (150, 50)

    def test_answer_sheet_is_not_scaled_down(self) -> None:
        original = image_bytes("JPEG", size=(3000, 100), exif=exif_with_gps_and_orientation())
        cleaned = sanitize_image(
            original, FileKind.JPEG, ImageLimits(max_pixels=40_000_000, max_edge_px=None)
        )
        with Image.open(io.BytesIO(cleaned)) as after:
            assert after.size == (3000, 100)
            assert not after.getexif().get_ifd(GPS_IFD)

    def test_a_small_photo_keeps_its_size(self) -> None:
        cleaned = sanitize_image(image_bytes("WEBP", (40, 30)), FileKind.WEBP, LIMITS)
        with Image.open(io.BytesIO(cleaned)) as after:
            assert (after.format, after.size) == ("WEBP", (40, 30))

    def test_too_many_pixels_are_rejected_before_decoding(self) -> None:
        with pytest.raises(BusinessRuleError) as raised:
            sanitize_image(
                png_bytes((300, 300)), FileKind.PNG, ImageLimits(max_pixels=10_000, max_edge_px=2560)
            )
        assert raised.value.code == "INVALID_IMAGE"
        assert raised.value.log_context == {"reason": "too_many_pixels"}

    def test_a_broken_photo_is_rejected(self) -> None:
        with pytest.raises(BusinessRuleError) as raised:
            sanitize_image(b"\xff\xd8\xff\xe0" + bytes(50), FileKind.JPEG, LIMITS)
        assert raised.value.code == "INVALID_IMAGE"

    def test_a_png_with_transparency_stays_a_png(self) -> None:
        output = io.BytesIO()
        Image.new("RGBA", (10, 10), (0, 0, 0, 0)).save(output, format="PNG")
        cleaned = sanitize_image(output.getvalue(), FileKind.PNG, LIMITS)
        with Image.open(io.BytesIO(cleaned)) as after:
            assert (after.format, after.mode) == ("PNG", "RGBA")
