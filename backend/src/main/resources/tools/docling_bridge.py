#!/usr/bin/env python3
"""OfficeBox PDF -> HTML bridge powered by Docling.

Docling performs the PDF parsing, layout analysis, OCR and table reconstruction.
The Java side then uses LibreOffice to turn the structured HTML into editable
DOCX. HTML is used as the interchange format because Docling currently exposes
HTML/Markdown/JSON as its structured exports rather than a native DOCX writer.
"""
import sys
from pathlib import Path

from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions, TesseractCliOcrOptions
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling_core.types.doc import ImageRefMode


HTML_HEAD = """
<meta charset="utf-8">
<style>
  @page { size: A4; margin: 16mm 14mm 16mm 14mm; }
  body {
    font-family: Arial, "Noto Sans CJK SC", "Microsoft YaHei", sans-serif;
    font-size: 10.5pt;
    line-height: 1.35;
    color: #222;
  }
  h1, h2, h3, h4, h5, h6 { page-break-after: avoid; }
  p, li, table { page-break-inside: avoid; }
  table { width: 100%; border-collapse: collapse; margin: 6pt 0; }
  th, td { border: 0.5pt solid #999; padding: 3pt 5pt; vertical-align: top; }
  img { max-width: 100%; height: auto; }
  .page { page-break-after: always; }
</style>
"""


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
    pipeline_options.images_scale = 1.5

    converter = DocumentConverter(
        format_options={
            InputFormat.PDF: PdfFormatOption(pipeline_options=pipeline_options)
        }
    )

    result = converter.convert(str(source))
    if result.document.num_pages() == 0:
        raise RuntimeError("Docling parsed zero PDF pages")

    result.document.save_as_html(
        target,
        image_mode=ImageRefMode.EMBEDDED,
        split_page_view=True,
        html_head=HTML_HEAD,
    )

    if not target.is_file() or target.stat().st_size == 0:
        raise RuntimeError("Docling produced no HTML output")

    print(f"Docling converted {source.name}: {result.document.num_pages()} pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
