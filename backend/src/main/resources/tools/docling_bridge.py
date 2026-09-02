#!/usr/bin/env python3
"""OfficeBox PDF -> structured HTML bridge powered by Docling."""
import sys
from pathlib import Path

from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions, TesseractCliOcrOptions
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling_core.types.doc import ImageRefMode


HTML_HEAD = """
<meta charset="utf-8">
<style>
  @page { margin: 12mm 14mm 14mm 14mm; }
  html, body { margin: 0; padding: 0; }
  body {
    font-family: "Noto Sans CJK SC", "Microsoft YaHei", Arial, sans-serif;
    font-size: 10.5pt;
    line-height: 1.25;
    color: #222;
    overflow-wrap: break-word;
  }
  h1 { font-size: 20pt; line-height: 1.15; margin: 0 0 8pt; }
  h2 { font-size: 15pt; line-height: 1.2; margin: 10pt 0 5pt; }
  h3 { font-size: 12pt; line-height: 1.2; margin: 8pt 0 4pt; }
  h4, h5, h6 { margin: 6pt 0 3pt; }
  h1, h2, h3, h4, h5, h6 { page-break-after: avoid; break-after: avoid; }
  p { margin: 0 0 4pt; }
  ul, ol { margin-top: 2pt; margin-bottom: 4pt; padding-left: 18pt; }
  li { margin: 0 0 2pt; }
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 5pt 0 7pt;
    font-size: 10pt;
  }
  th, td {
    border: 0.5pt solid #888;
    padding: 3pt 5pt;
    vertical-align: top;
  }
  thead { display: table-header-group; }
  tr { break-inside: avoid; }
  img { max-width: 100%; height: auto; }
  a { color: inherit; text-decoration: none; }
  .page { break-after: page; }
  .page:last-child { break-after: auto; }
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
    pipeline_options.images_scale = 2.0

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
