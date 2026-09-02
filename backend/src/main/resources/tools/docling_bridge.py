#!/usr/bin/env python3
"""OfficeBox PDF -> editable HTML bridge powered by Docling."""
import base64
import re
import sys
from pathlib import Path

from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions, TesseractCliOcrOptions
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling_core.types.doc import ImageRefMode


HTML_HEAD = r"""
<meta charset="utf-8">
<style>
  @page { size: A4; margin: 8mm 14mm 10mm 14mm; }
  html, body { margin: 0; padding: 0; }
  body {
    font-family: "Noto Sans", "Noto Sans CJK SC", "Microsoft YaHei", Arial, sans-serif;
    font-size: 9.5pt;
    line-height: 1.18;
    color: #222;
    overflow-wrap: break-word;
  }
  h1 { font-size: 18pt; line-height: 1.08; margin: 0 0 5pt; }
  h2 { font-size: 12pt; line-height: 1.12; margin: 8pt 0 4pt; }
  h3 { font-size: 10.5pt; line-height: 1.12; margin: 6pt 0 3pt; }
  h4, h5, h6 { margin: 5pt 0 2pt; }
  h1, h2, h3, h4, h5, h6 { page-break-after: avoid; break-after: avoid; }
  h2 { clear: both; }
  p { margin: 0 0 3pt; }
  ul, ol { margin: 1pt 0 3pt; padding-left: 17pt; }
  li { margin: 0; padding: 0; }
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 3pt 0 5pt;
    font-size: 9pt;
    page-break-inside: auto;
  }
  th, td {
    border: 0.5pt solid #999;
    padding: 2pt 4pt;
    vertical-align: top;
  }
  thead { display: table-header-group; }
  tr { page-break-inside: avoid; break-inside: avoid; }
  img { max-width: 100%; height: auto; }
  a { color: inherit; text-decoration: none; }
  .profile-photo { float: left; width: 27mm; height: 27mm; margin: 1mm 5mm 2mm 0; border-radius: 50%; }
  .europass-logo { float: right; width: 51mm; height: auto; margin: -2mm 0 1mm 4mm; }
  .officebox-clear { clear: both; height: 0; margin: 0; padding: 0; }
  .docling-list { margin-left: 0; }
</style>
"""


def _data_uri(png: bytes) -> str:
    return "data:image/png;base64," + base64.b64encode(png).decode("ascii")


def _extract_header_images(pdf_path: Path):
    """Extract the Europass logo and profile photo when they exist as PDF images.

    The profile photo is identified as the small square image on page 1. The
    larger image is the Europass logo and may carry a separate PDF soft mask.
    """
    try:
        import fitz  # PyMuPDF, already pulled by Docling's PDF stack in practice.
    except Exception:
        return None, None

    try:
        pdf = fitz.open(pdf_path)
        if not pdf.page_count:
            return None, None
        page = pdf[0]
        photo = None
        logo = None
        for item in page.get_images(full=True):
            xref, smask, width, height = item[0], item[1], item[2], item[3]
            if width <= 400 and height <= 400 and abs(width - height) <= max(width, height) * 0.08:
                pix = fitz.Pixmap(pdf, xref)
                photo = pix.tobytes("png")
                pix = None
            elif width >= 1000 and height >= 600:
                pix = fitz.Pixmap(pdf, xref)
                if smask:
                    mask = fitz.Pixmap(pdf, smask)
                    try:
                        pix = fitz.Pixmap(pix, mask)
                    finally:
                        mask = None
                logo = pix.tobytes("png")
                pix = None
        pdf.close()
        return photo, logo
    except Exception:
        return None, None


def _fix_list_markup(html: str) -> str:
    """Normalize Docling's bullet paragraphs for LibreOffice's HTML importer."""
    # Docling can emit the bullet as text when a PDF used a visual bullet rather
    # than a semantic list. Convert those paragraphs into real list items.
    pattern = re.compile(r"<p([^>]*)>\s*•\s*(.*?)\s*</p>", re.I | re.S)
    html = pattern.sub(r'<ul class="docling-list"><li\1>\2</li></ul>', html)
    # A few PDFs contain several bullets in one text block. Split only on a
    # bullet that starts after whitespace/punctuation, preserving normal prose.
    html = re.sub(
        r"(<p[^>]*>[^<]*?)(?:\s*•\s+)([^<]*</p>)",
        lambda m: m.group(0),
        html,
        flags=re.I | re.S,
    )
    return html


def _decorate_header(html: str, source: Path) -> str:
    photo, logo = _extract_header_images(source)
    if not photo and not logo:
        return html

    assets = []
    if photo:
        assets.append(f'<img class="profile-photo" alt="Profile photo" src="{_data_uri(photo)}">')
    if logo:
        assets.append(f'<img class="europass-logo" alt="Europass" src="{_data_uri(logo)}">')
    if not assets:
        return html

    marker = "<div class=\"officebox-clear\"></div>"
    # Put the images before the first heading/title. Floating images naturally
    # recreate the two-column Europass header while keeping all text editable.
    match = re.search(r"<(?:h1|h2|p)\b", html, re.I)
    if match:
        html = html[:match.start()] + "".join(assets) + html[match.start():]
    else:
        html = "".join(assets) + html
    # Clear the float before the first major section heading.
    html = re.sub(r"(<h2\b[^>]*>)", marker + r"\1", html, count=1, flags=re.I)
    return html


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
    pipeline_options.do_ocr = True
    pipeline_options.ocr_options = TesseractCliOcrOptions(lang=["eng", "chi_sim"])
    pipeline_options.do_table_structure = True
    pipeline_options.generate_picture_images = True
    pipeline_options.generate_table_images = True
    pipeline_options.images_scale = 2.0

    converter = DocumentConverter(
        format_options={
            InputFormat.PDF: PdfFormatOption(pipeline_options=pipeline_options)
        }
    )

    result = converter.convert(str(source))
    if result.document.num_pages() == 0:
        raise RuntimeError("Docling parsed zero PDF pages")

    # Do NOT ask Docling for split-page HTML here. LibreOffice should paginate
    # the editable flow itself; split-page HTML was causing mostly blank pages
    # and turning a 3-page Europass CV into 5 pages.
    result.document.save_as_html(
        target,
        image_mode=ImageRefMode.EMBEDDED,
        split_page_view=False,
        html_head=HTML_HEAD,
    )

    html = target.read_text(encoding="utf-8")
    html = _fix_list_markup(html)
    html = _decorate_header(html, source)
    target.write_text(html, encoding="utf-8")

    if not target.is_file() or target.stat().st_size == 0:
        raise RuntimeError("Docling produced no HTML output")

    print(f"Docling converted {source.name}: {result.document.num_pages()} pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
