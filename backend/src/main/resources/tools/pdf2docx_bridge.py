#!/usr/bin/env python3
"""Open-source PDF -> DOCX bridge using Artifex pdf2docx.

The conversion profile is tuned for editable Word output: column-aware layout,
fewer accidental line breaks, and less aggressive image clipping. All
components remain open-source and are installed in the OfficeBox image.
"""
import os
import sys
from pathlib import Path

from pdf2docx import Converter


def _float_env(name: str, default: float) -> float:
    try:
        return float(os.environ.get(name, default))
    except ValueError:
        return default


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: pdf2docx_bridge.py INPUT.pdf OUTPUT.docx", file=sys.stderr)
        return 2

    source = Path(sys.argv[1]).resolve()
    target = Path(sys.argv[2]).resolve()
    if not source.is_file():
        print(f"input PDF does not exist: {source}", file=sys.stderr)
        return 2
    target.parent.mkdir(parents=True, exist_ok=True)

    # Tuned layout profile. Values can be overridden in Docker without a
    # rebuild, which makes regression tuning safe for real customer PDFs.
    settings = {
        "multi_processing": False,
        "ignore_page_error": False,
        "raw_exceptions": True,
        "min_section_height": _float_env("OFFICEBOX_PDF2DOCX_MIN_SECTION_HEIGHT", 12.0),
        "float_image_ignorable_gap": _float_env("OFFICEBOX_PDF2DOCX_FLOAT_IMAGE_GAP", 10.0),
        "page_margin_factor_top": _float_env("OFFICEBOX_PDF2DOCX_TOP_MARGIN_FACTOR", 0.35),
        "page_margin_factor_bottom": _float_env("OFFICEBOX_PDF2DOCX_BOTTOM_MARGIN_FACTOR", 0.35),
        "line_break_width_ratio": _float_env("OFFICEBOX_PDF2DOCX_LINE_BREAK_WIDTH", 0.35),
        "line_break_free_space_ratio": _float_env("OFFICEBOX_PDF2DOCX_LINE_BREAK_SPACE", 0.15),
        "line_separate_threshold": _float_env("OFFICEBOX_PDF2DOCX_LINE_SEPARATE", 4.0),
        "new_paragraph_free_space_ratio": _float_env("OFFICEBOX_PDF2DOCX_PARAGRAPH_SPACE", 0.72),
        "clip_image_res_ratio": _float_env("OFFICEBOX_PDF2DOCX_IMAGE_RES_RATIO", 5.0),
        "min_svg_gap_dx": _float_env("OFFICEBOX_PDF2DOCX_SVG_GAP_X", 12.0),
        "min_svg_gap_dy": _float_env("OFFICEBOX_PDF2DOCX_SVG_GAP_Y", 2.0),
        "min_svg_w": _float_env("OFFICEBOX_PDF2DOCX_SVG_MIN_W", 1.5),
        "min_svg_h": _float_env("OFFICEBOX_PDF2DOCX_SVG_MIN_H", 1.5),
        "parse_lattice_table": True,
        "parse_stream_table": True,
        "list_not_table": True,
    }

    converter = Converter(str(source))
    try:
        converter.convert(str(target), start=0, end=None, **settings)
    finally:
        converter.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
