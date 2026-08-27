# Image Tools

OfficeBox image tools roadmap and behavior contract.

## Current capabilities

- Resize images while preserving aspect ratio.
- Convert JPG, PNG and WebP.
- Rotate images by 90, 180 or 270 degrees.
- Set output quality for lossy formats.
- Process a single image through the unified image endpoint.

## Next batch

- Batch processing with one request and per-file results.
- Compression presets: Web, Email, Document, High quality.
- Before/after size comparison.
- Crop and flip.
- EXIF removal.
- Download all results as ZIP.

The frontend should keep the operation model consistent: upload -> configure -> process -> preview -> download.
