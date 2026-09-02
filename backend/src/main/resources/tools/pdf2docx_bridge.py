#!/usr/bin/env python3
"""Open-source PDF -> DOCX bridge using Artifex pdf2docx.

The bridge keeps the conversion editable and adds a small OfficeBox cleanup
pass for a common PDF layout artifact: several bullet items being merged into
one Word paragraph. The cleanup only splits embedded bullet markers; it does
not rewrite ordinary prose.
"""
import os
import re
import sys
from copy import deepcopy
from pathlib import Path

from docx import Document
from pdf2docx import Converter


BULLET_RE = re.compile(r"(?<!\S)([•●▪◦‣⁃])\s*")


def _float_env(name: str, default: float) -> float:
    try:
        return float(os.environ.get(name, default))
    except ValueError:
        return default


def _split_embedded_bullets(path: Path) -> int:
    """Split paragraphs containing multiple embedded bullet items.

    pdf2docx intentionally focuses on layout reconstruction and does not yet
    expose a full Word list-style reconstruction. Resume PDFs commonly encode
    bullets as ordinary glyphs, so a paragraph can become ``text.• item``.
    Turning those embedded markers into real paragraph boundaries is a safe,
    editable improvement and preserves the text rather than rasterizing it.
    """
    doc = Document(str(path))
    changed = 0

    for paragraph in list(doc.paragraphs):
        text = paragraph.text
        matches = list(BULLET_RE.finditer(text))
        if not matches or matches[0].start() == 0:
            continue

        # Only split when a bullet appears after non-whitespace content. This
        # avoids touching paragraphs that are already clean bullet items.
        boundaries = [m.start() for m in matches]
        if not boundaries:
            continue

        parts = []
        start = 0
        for boundary in boundaries:
            if boundary > start:
                parts.append(text[start:boundary].strip())
            start = boundary
        if start < len(text):
            parts.append(text[start:].strip())
        parts = [part for part in parts if part]
        if len(parts) < 2:
            continue

        p = paragraph._p
        parent = p.getparent()
        index = parent.index(p)
        ppr = deepcopy(p.pPr) if p.pPr is not None else None
        base_run = deepcopy(paragraph.runs[0]._r) if paragraph.runs else None

        for offset, part in enumerate(parts):
            new_p = deepcopy(p)
            for child in list(new_p):
                if child.tag.endswith('}pPr'):
                    continue
                new_p.remove(child)
            if ppr is not None:
                new_p.insert(0, deepcopy(ppr))

            if base_run is not None:
                new_run = deepcopy(base_run)
                text_nodes = [n for n in new_run.iter() if n.tag.endswith('}t')]
                if text_nodes:
                    text_nodes[0].text = part
                    for node in text_nodes[1:]:
                        node.text = ''
                new_p.append(new_run)
            else:
                # Keep the paragraph structurally valid even if pdf2docx
                # emitted a textless paragraph.
                from docx.oxml import OxmlElement
                run = OxmlElement('w:r')
                t = OxmlElement('w:t')
                t.text = part
                run.append(t)
                new_p.append(run)

            parent.insert(index + offset, new_p)

        parent.remove(p)
        changed += len(parts) - 1

    if changed:
        doc.save(str(path))
    return changed


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

    # Keep the upstream pdf2docx defaults unless OfficeBox explicitly tunes a
    # value. This avoids baking in speculative thresholds that can damage
    # unrelated documents while still allowing deployment-time regression
    # tuning through environment variables.
    settings = {
        "multi_processing": False,
        "ignore_page_error": False,
        "raw_exceptions": True,
        "min_section_height": _float_env("OFFICEBOX_PDF2DOCX_MIN_SECTION_HEIGHT", 20.0),
        "float_image_ignorable_gap": _float_env("OFFICEBOX_PDF2DOCX_FLOAT_IMAGE_GAP", 5.0),
        "page_margin_factor_top": _float_env("OFFICEBOX_PDF2DOCX_TOP_MARGIN_FACTOR", 0.5),
        "page_margin_factor_bottom": _float_env("OFFICEBOX_PDF2DOCX_BOTTOM_MARGIN_FACTOR", 0.5),
        "line_break_width_ratio": _float_env("OFFICEBOX_PDF2DOCX_LINE_BREAK_WIDTH", 0.5),
        "line_break_free_space_ratio": _float_env("OFFICEBOX_PDF2DOCX_LINE_BREAK_SPACE", 0.1),
        "line_separate_threshold": _float_env("OFFICEBOX_PDF2DOCX_LINE_SEPARATE", 5.0),
        "new_paragraph_free_space_ratio": _float_env("OFFICEBOX_PDF2DOCX_PARAGRAPH_SPACE", 0.85),
        "clip_image_res_ratio": _float_env("OFFICEBOX_PDF2DOCX_IMAGE_RES_RATIO", 4.0),
        "min_svg_gap_dx": _float_env("OFFICEBOX_PDF2DOCX_SVG_GAP_X", 15.0),
        "min_svg_gap_dy": _float_env("OFFICEBOX_PDF2DOCX_SVG_GAP_Y", 2.0),
        "min_svg_w": _float_env("OFFICEBOX_PDF2DOCX_SVG_MIN_W", 2.0),
        "min_svg_h": _float_env("OFFICEBOX_PDF2DOCX_SVG_MIN_H", 2.0),
        "parse_lattice_table": True,
        "parse_stream_table": True,
        "list_not_table": True,
    }

    converter = Converter(str(source))
    try:
        converter.convert(str(target), start=0, end=None, **settings)
    finally:
        converter.close()

    split_count = _split_embedded_bullets(target)
    print(f"OfficeBox bullet cleanup: split {split_count} embedded bullet boundaries")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
