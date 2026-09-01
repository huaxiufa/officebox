#!/usr/bin/env python3
"""Open-source PDF -> DOCX bridge using Artifex pdf2docx.

OfficeBox keeps the Java API stable while delegating layout reconstruction to
pdf2docx. The dependency is MIT licensed and installed in the Docker image.
"""
import sys
from pathlib import Path

from pdf2docx import Converter


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

    converter = Converter(str(source))
    try:
        converter.convert(str(target), start=0, end=None, multi_processing=False)
    finally:
        converter.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
