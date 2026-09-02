#!/usr/bin/env python3
"""OfficeBox PDF -> editable DOCX bridge powered directly by Docling."""
import os
import sys
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Pt

from docling.datamodel.accelerator_options import AcceleratorDevice, AcceleratorOptions
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions, TesseractCliOcrOptions
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling_core.types.doc import ContentLayer


def _num(name, default):
    try:
        return float(os.environ.get(name, default))
    except (TypeError, ValueError):
        return default


def _bbox(item):
    prov = getattr(item, "prov", None)
    return prov[0].bbox if prov else None


def _page(item):
    prov = getattr(item, "prov", None)
    return prov[0].page_no if prov else None


def _cell_margins(cell):
    tc_pr = cell._tc.get_or_add_tcPr()
    mar = OxmlElement("w:tcMar")
    for side, value in (("top", 45), ("start", 70), ("bottom", 45), ("end", 70)):
        node = OxmlElement(f"w:{side}")
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")
        mar.append(node)
    tc_pr.append(mar)


def _shade(cell, fill="EDEDED"):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    tc_pr.append(shd)


def _render_text(out, item, page_height, cursor):
    box = _bbox(item)
    text = getattr(item, "text", None) or getattr(item, "orig", None) or ""
    if box is None or not text.strip():
        return cursor
    top = page_height - box.b
    p = out.add_paragraph()
    pf = p.paragraph_format
    pf.space_before = Pt(max(0, top - cursor))
    pf.space_after = Pt(0)
    pf.left_indent = Pt(max(0, box.l))
    pf.line_spacing = 1.0
    run = p.add_run(text)
    label = str(getattr(item, "label", ""))
    size = 15 if "section_header" in label else 10.5
    if "title" in label:
        size = 20
    run.font.size = Pt(size)
    run.bold = "section_header" in label or "title" in label
    if "list_item" in label:
        p.style = out.styles["List Bullet"]
    return max(cursor, top + box.height)


def _render_table(out, item, page_height, cursor):
    box = _bbox(item)
    data = getattr(item, "data", None)
    if box is None or data is None or not data.num_rows or not data.num_cols:
        return cursor
    top = page_height - box.b
    p = out.add_paragraph()
    p.paragraph_format.space_before = Pt(max(0, top - cursor))
    p.paragraph_format.space_after = Pt(0)
    table = out.add_table(rows=int(data.num_rows), cols=int(data.num_cols))
    table.style = "Table Grid"
    table.autofit = False
    seen = set()
    for cell in data.table_cells:
        sr, er = int(cell.start_row_offset_idx), int(cell.end_row_offset_idx)
        sc, ec = int(cell.start_col_offset_idx), int(cell.end_col_offset_idx)
        key = (sr, er, sc, ec)
        if key in seen or sr >= data.num_rows or sc >= data.num_cols:
            continue
        seen.add(key)
        target = table.cell(sr, sc)
        if er > sr or ec > sc:
            target = target.merge(table.cell(min(er, data.num_rows) - 1, min(ec, data.num_cols) - 1))
        target.text = getattr(cell, "text", "") or ""
        target.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
        _cell_margins(target)
        if getattr(cell, "column_header", False) or getattr(cell, "row_header", False):
            _shade(target)
            for paragraph in target.paragraphs:
                for run in paragraph.runs:
                    run.bold = True
    if table.rows:
        table.rows[0]._tr.get_or_add_trPr().append(OxmlElement("w:tblHeader"))
    return max(cursor, top + box.height)


def _render_picture(out, item, dl_doc, page_height, cursor, assets):
    box = _bbox(item)
    if box is None:
        return cursor
    image = item.get_image(dl_doc)
    if image is None:
        return cursor
    top = page_height - box.b
    p = out.add_paragraph()
    p.paragraph_format.space_before = Pt(max(0, top - cursor))
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.left_indent = Pt(max(0, box.l))
    path = assets / f"{len(list(assets.iterdir()))}.png"
    image.save(path)
    p.add_run().add_picture(str(path), width=Pt(max(1, box.width)), height=Pt(max(1, box.height)))
    return max(cursor, top + box.height)


def _items(doc):
    try:
        iterator = doc.iterate_items(
            traverse_pictures=False,
            included_content_layers={ContentLayer.BODY, ContentLayer.FURNITURE},
        )
    except TypeError:
        iterator = doc.iterate_items(traverse_pictures=False)
    result = []
    for item, _level in iterator:
        parent = getattr(item, "parent", None)
        cref = getattr(parent, "cref", "") if parent else ""
        if isinstance(cref, str) and cref.startswith("#/tables/"):
            continue
        result.append(item)
    return result


def main():
    if len(sys.argv) != 3:
        print("usage: docling_docx_bridge.py INPUT.pdf OUTPUT.docx", file=sys.stderr)
        return 2
    source, target = Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve()
    if not source.is_file():
        print(f"input PDF does not exist: {source}", file=sys.stderr)
        return 2
    target.parent.mkdir(parents=True, exist_ok=True)

    options = PdfPipelineOptions()
    options.accelerator_options = AcceleratorOptions(
        device=AcceleratorDevice.CPU,
        num_threads=max(1, int(_num("OFFICEBOX_DOCLING_THREADS", 4))),
    )
    options.do_ocr = True
    options.ocr_options = TesseractCliOcrOptions(lang=["eng", "chi_sim"])
    options.do_table_structure = True
    options.generate_picture_images = True
    options.generate_table_images = True
    options.images_scale = 2.0

    converter = DocumentConverter(format_options={
        InputFormat.PDF: PdfFormatOption(pipeline_options=options)
    })
    dl_doc = converter.convert(str(source)).document
    if dl_doc.num_pages() == 0:
        raise RuntimeError("Docling parsed zero PDF pages")

    out = Document()
    out.styles["Normal"].font.name = "Arial"
    out.styles["Normal"].font.size = Pt(10.5)
    assets = target.parent / (target.stem + ".assets")
    assets.mkdir(parents=True, exist_ok=True)
    items = _items(dl_doc)

    for page_no in range(1, dl_doc.num_pages() + 1):
        page = dl_doc.pages.get(page_no)
        if page is None or page.size is None:
            continue
        if page_no > 1:
            section = out.add_section(WD_SECTION.NEW_PAGE)
        else:
            section = out.sections[0]
        section.page_width = Pt(page.size.width)
        section.page_height = Pt(page.size.height)
        section.top_margin = section.bottom_margin = Pt(0)
        section.left_margin = section.right_margin = Pt(0)
        cursor = 0.0
        for item in [x for x in items if _page(x) == page_no]:
            label = str(getattr(item, "label", ""))
            try:
                if "table" in label:
                    cursor = _render_table(out, item, page.size.height, cursor)
                elif "picture" in label or item.__class__.__name__.lower().endswith("pictureitem"):
                    cursor = _render_picture(out, item, dl_doc, page.size.height, cursor, assets)
                elif hasattr(item, "text"):
                    cursor = _render_text(out, item, page.size.height, cursor)
            except Exception as exc:
                print(f"warning: {item.__class__.__name__}: {exc}", file=sys.stderr)

    out.save(target)
    if not target.is_file() or target.stat().st_size == 0:
        raise RuntimeError("Docling produced no DOCX output")
    print(f"Docling converted {source.name}: {dl_doc.num_pages()} pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
