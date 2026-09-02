#!/usr/bin/env python3
"""Open-source PDF -> DOCX bridge using Artifex pdf2docx.

The bridge keeps the conversion editable and adds a small OfficeBox cleanup
pass for common PDF layout artifacts. Cleanup is deliberately conservative:
it only changes paragraph boundaries for embedded bullet markers and preserves
the original run formatting instead of flattening a paragraph to one run.
"""
import os
import re
import sys
from copy import deepcopy
from pathlib import Path

from docx import Document
from pdf2docx import Converter


BULLET_RE = re.compile(r"(?<!\\S)([•●▪◦‣⁃])\\s*")


def _float_env(name: str, default: float) -> float:
    try:
        return float(os.environ.get(name, default))
    except ValueError:
        return default


def _clone_run_slice(run, start: int, end: int):
    """Clone a run while keeping its formatting and only a text slice."""
    text = run.text or ""
    if start >= end or not text:
        return None
    clone = deepcopy(run._r)
    value = text[start:end]
    text_nodes = [node for node in clone.iter() if node.tag.endswith("}t")]
    if not text_nodes:
        return None
    text_nodes[0].text = value
    for node in text_nodes[1:]:
        node.text = ""
    return clone


def _build_split_paragraphs(paragraph, boundaries):
    """Create paragraphs split at character offsets while preserving runs.

    pdf2docx can put normal prose, a bullet glyph and the following item into
    one paragraph. The old cleanup rebuilt every new paragraph from the first
    run, which silently discarded bold/italic/font changes inside the source
    paragraph. This implementation maps each output range back onto the
    original runs, so formatting survives the split.
    """
    text = paragraph.text
    ranges = []
    start = 0
    for boundary in boundaries:
        if boundary > start:
            ranges.append((start, boundary))
        start = boundary
    if start < len(text):
        ranges.append((start, len(text)))

    ranges = [(s, e) for s, e in ranges if text[s:e].strip()]
    if len(ranges) < 2:
        return []

    ppr = deepcopy(paragraph._p.pPr) if paragraph._p.pPr is not None else None
    runs = list(paragraph.runs)
    output = []
    cursor = 0

    for range_start, range_end in ranges:
        new_p = deepcopy(paragraph._p)
        for child in list(new_p):
            if child.tag.endswith("}pPr"):
                continue
            new_p.remove(child)
        if ppr is not None:
            new_p.insert(0, deepcopy(ppr))

        run_cursor = 0
        for run in runs:
            run_text = run.text or ""
            run_start = run_cursor
            run_end = run_cursor + len(run_text)
            run_cursor = run_end
            if run_end <= range_start or run_start >= range_end:
                continue
            slice_start = max(range_start, run_start) - run_start
            slice_end = min(range_end, run_end) - run_start
            clone = _clone_run_slice(run, slice_start, slice_end)
            if clone is not None:
                new_p.append(clone)

        # Preserve a structurally valid paragraph even when the source range
        # contains only non-text XML elements.
        if len(new_p) == (1 if ppr is not None else 0):
            from docx.oxml import OxmlElement
            run = OxmlElement("w:r")
            t = OxmlElement("w:t")
            t.text = text[range_start:range_end].strip()
            run.append(t)
            new_p.append(run)

        output.append(new_p)
        cursor = range_end

    return output


def _split_embedded_bullets(path: Path) -> int:
    """Split paragraphs containing multiple embedded bullet items.

    Only paragraphs that contain an embedded bullet after non-whitespace text
    are changed. Existing bullet paragraphs remain untouched. Paragraphs with
    drawings are skipped because moving inline drawing XML without a richer
    layout model can change image placement.
    """
    doc = Document(str(path))
    changed = 0

    for paragraph in list(doc.paragraphs):
        text = paragraph.text
        matches = list(BULLET_RE.finditer(text))
        if not matches or matches[0].start() == 0:
            continue
        if paragraph._p.xpath(".//w:drawing"):
            continue

        boundaries = [match.start() for match in matches]
        new_paragraphs = _build_split_paragraphs(paragraph, boundaries)
        if len(new_paragraphs) < 2:
            continue

        p = paragraph._p
        parent = p.getparent()
        index = parent.index(p)
        for offset, new_p in enumerate(new_paragraphs):
            parent.insert(index + offset, new_p)
        parent.remove(p)
        changed += len(new_paragraphs) - 1

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
