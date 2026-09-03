#!/usr/bin/env python3
"""OfficeBox PDF -> editable HTML bridge powered by Docling.

Docling performs PDF understanding. LibreOffice converts the structured HTML
into the final editable DOCX. A tiny PyMuPDF text-recovery fallback is used
only when Docling's serializer/OCR loses text (common with legacy PDF font
encodings); Docling remains the primary conversion engine.
"""
import base64
import html as html_lib
import re
import sys
from pathlib import Path

from docling.datamodel.accelerator_options import AcceleratorDevice, AcceleratorOptions
from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions, TesseractCliOcrOptions
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling_core.types.doc import ImageRefMode

HTML_HEAD = r"""
<meta charset="utf-8">
<style>
@page { size: A4; margin: 8mm 14mm 10mm 14mm; }
html, body { margin: 0; padding: 0; }
body { font-family: "Noto Sans", "Noto Sans CJK SC", "Microsoft YaHei", Arial, sans-serif; font-size: 9.5pt; line-height: 1.18; color: #222; overflow-wrap: break-word; }
h1 { font-size: 18pt; line-height: 1.08; margin: 0 0 5pt; }
h2 { font-size: 12pt; line-height: 1.12; margin: 8pt 0 4pt; }
h3 { font-size: 10.5pt; line-height: 1.12; margin: 6pt 0 3pt; }
h4, h5, h6 { margin: 5pt 0 2pt; }
h1, h2, h3, h4, h5, h6 { page-break-after: avoid; break-after: avoid; }
p { margin: 0 0 3pt; }
ul, ol { margin: 1pt 0 3pt; padding-left: 17pt; }
li { margin: 0; padding: 0; }
table { width: 100%; border-collapse: collapse; margin: 3pt 0 5pt; font-size: 9pt; page-break-inside: auto; }
th, td { border: 0.5pt solid #999; padding: 2pt 4pt; vertical-align: top; }
thead { display: table-header-group; }
tr { page-break-inside: avoid; break-inside: avoid; }
img { max-width: 100%; height: auto; }
.profile-photo { float: left; width: 27mm; height: 27mm; margin: 1mm 5mm 2mm 0; border-radius: 50%; }
.europass-logo { float: right; width: 51mm; height: auto; margin: -2mm 0 1mm 4mm; }
.officebox-clear { clear: both; height: 0; margin: 0; padding: 0; }
.docling-list { margin-left: 0; }
</style>
"""


def _data_uri(png: bytes) -> str:
    return "data:image/png;base64," + base64.b64encode(png).decode("ascii")


def _extract_header_images(pdf_path: Path):
    try:
        import fitz
        pdf = fitz.open(pdf_path)
        if not pdf.page_count:
            return None, None
        photo = logo = None
        for item in pdf[0].get_images(full=True):
            xref, smask, width, height = item[0], item[1], item[2], item[3]
            if width <= 400 and height <= 400 and abs(width - height) <= max(width, height) * 0.08:
                photo = fitz.Pixmap(pdf, xref).tobytes("png")
            elif width >= 1000 and height >= 600:
                pix = fitz.Pixmap(pdf, xref)
                if smask:
                    mask = fitz.Pixmap(pdf, smask)
                    try:
                        pix = fitz.Pixmap(pix, mask)
                    finally:
                        mask = None
                logo = pix.tobytes("png")
        pdf.close()
        return photo, logo
    except Exception:
        return None, None


def _raw_pdf_text(pdf_path: Path) -> str:
    """Recover text from the PDF only when Docling loses legacy-encoded glyphs."""
    try:
        import fitz
        pdf = fitz.open(pdf_path)
        text = "\n".join(page.get_text("text") for page in pdf)
        pdf.close()
        return text.strip()
    except Exception:
        return ""


def _text_quality(value: str) -> int:
    return len(re.sub(r"\s+", "", value or ""))


def _fix_list_markup(source: str) -> str:
    pattern = re.compile(r"<p([^>]*)>\s*•\s*(.*?)\s*</p>", re.I | re.S)
    return pattern.sub(r'<ul class="docling-list"><li\1>\2</li></ul>', source)


def _visible_text(source: str) -> str:
    body = re.sub(r"<style\b[^>]*>.*?</style>", " ", source, flags=re.I | re.S)
    body = re.sub(r"<script\b[^>]*>.*?</script>", " ", body, flags=re.I | re.S)
    body = re.sub(r"<[^>]+>", " ", body)
    return re.sub(r"\s+", " ", html_lib.unescape(body)).strip()


def _markdown_fallback(markdown: str) -> str:
    """Create conservative valid HTML from Docling or recovered text."""
    out = []
    in_ul = in_ol = False

    def close_lists():
        nonlocal in_ul, in_ol
        if in_ul:
            out.append("</ul>")
            in_ul = False
        if in_ol:
            out.append("</ol>")
            in_ol = False

    for raw in markdown.splitlines():
        line = raw.strip()
        if not line:
            close_lists()
            continue
        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            close_lists()
            level = len(m.group(1))
            out.append(f"<h{level}>{html_lib.escape(m.group(2))}</h{level}>")
            continue
        m = re.match(r"^[-*•]\s+(.*)$", line)
        if m:
            if in_ol:
                out.append("</ol>")
                in_ol = False
            if not in_ul:
                out.append('<ul class="docling-list">')
                in_ul = True
            out.append(f"<li>{html_lib.escape(m.group(1))}</li>")
            continue
        m = re.match(r"^\d+[.)]\s+(.*)$", line)
        if m:
            if in_ul:
                out.append("</ul>")
                in_ul = False
            if not in_ol:
                out.append("<ol>")
                in_ol = True
            out.append(f"<li>{html_lib.escape(m.group(1))}</li>")
            continue
        close_lists()
        text = html_lib.escape(line)
        text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
        text = re.sub(r"__(.+?)__", r"<strong>\1</strong>", text)
        text = re.sub(r"(?<!\*)\*([^*]+)\*(?!\*)", r"<em>\1</em>", text)
        out.append(f"<p>{text}</p>")
    close_lists()
    return "<body>" + "\n".join(out) + "</body>"


def _decorate_header(source_html: str, source: Path) -> str:
    photo, logo = _extract_header_images(source)
    assets = []
    if photo:
        assets.append(f'<img class="profile-photo" alt="Profile photo" src="{_data_uri(photo)}">')
    if logo:
        assets.append(f'<img class="europass-logo" alt="Europass" src="{_data_uri(logo)}">')
    if not assets:
        return source_html
    match = re.search(r"<(?:h1|h2|p|table|ul|ol)\b", source_html, re.I)
    if match:
        source_html = source_html[:match.start()] + "".join(assets) + source_html[match.start():]
    else:
        source_html = "".join(assets) + source_html
    return re.sub(r"(<h2\b[^>]*>)", r'<div class="officebox-clear"></div>\1', source_html, count=1, flags=re.I)


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: docling_bridge.py INPUT.pdf OUTPUT.html", file=sys.stderr)
        return 2
    source = Path(sys.argv[1]).resolve()
    target = Path(sys.argv[2]).resolve()
    if not source.is_file():
        print(f"input PDF does not exist: {source}", file=sys.stderr)
        return 2
    target.parent.mkdir(parents=True, exist_ok=True)

    pipeline_options = PdfPipelineOptions()
    pipeline_options.accelerator_options = AcceleratorOptions(device=AcceleratorDevice.CPU, num_threads=4)
    pipeline_options.do_ocr = True
    pipeline_options.ocr_options = TesseractCliOcrOptions(lang=["eng", "chi_sim"])
    pipeline_options.do_table_structure = True
    pipeline_options.generate_picture_images = True
    pipeline_options.generate_table_images = True
    pipeline_options.images_scale = 2.0

    converter = DocumentConverter(
        allowed_formats=[InputFormat.PDF],
        format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=pipeline_options)},
    )
    result = converter.convert(str(source))
    document = result.document
    if document.num_pages() == 0:
        raise RuntimeError("Docling parsed zero PDF pages")

    extracted_text = document.export_to_text(traverse_pictures=True).strip()
    raw_text = _raw_pdf_text(source)
    document.save_as_html(target, image_mode=ImageRefMode.EMBEDDED, split_page_view=False, html_head=HTML_HEAD)
    generated = _fix_list_markup(target.read_text(encoding="utf-8") if target.exists() else "")

    visible = _visible_text(generated)
    # First fallback: Docling's own structured markdown. This keeps Docling
    # responsible for reading order/tables/layout whenever possible.
    if not visible or (extracted_text and _text_quality(visible) < max(8, _text_quality(extracted_text) // 4)):
        markdown = document.export_to_markdown(traverse_pictures=True)
        generated = HTML_HEAD + _markdown_fallback(markdown)
        visible = _visible_text(generated)
        print("Docling HTML serializer produced insufficient editable text; using Docling markdown fallback", file=sys.stderr)

    # Second fallback: only for PDFs whose legacy font encoding makes Docling
    # lose actual glyphs. Keep the Docling result unless raw PDF text is
    # materially richer; this is not a pdf2docx fallback and does not replace
    # Docling's document understanding for normal PDFs.
    if raw_text and _text_quality(raw_text) > max(12, _text_quality(visible) * 2):
        generated = HTML_HEAD + _markdown_fallback(raw_text)
        print("Docling text recovery used PyMuPDF for legacy PDF glyph encoding", file=sys.stderr)

    generated = _decorate_header(generated, source)
    target.write_text(generated, encoding="utf-8")
    final_visible = _visible_text(generated)
    if not target.is_file() or target.stat().st_size == 0:
        raise RuntimeError("Docling produced no HTML output")
    if not final_visible:
        raise RuntimeError("Docling produced empty editable content")

    print(f"Docling converted {source.name}: {document.num_pages()} pages; Docling text={len(extracted_text)} chars; final editable text={len(final_visible)} chars")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
