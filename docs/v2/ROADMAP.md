# OfficeBox V2 Roadmap

## Product direction

OfficeBox V2 is an open-source, self-hostable online office platform. V2 builds on the verified V1 feature baseline instead of duplicating V1 tools.

## V2.1 Platform Foundation

- [x] Unified API response
- [x] Unified exception handling
- [x] Storage configuration
- [x] Task status model
- [x] Task service foundation
- [x] Task API foundation
- [x] Temporary-file cleanup foundation
- [x] Verified V1 baseline
- [x] Persistent task state
- [x] Bounded async task executor
- [x] Task lifecycle runner
- [x] Result download API
- [ ] Task progress reporting
- [ ] File lifecycle integration tests
- [ ] CI quality gates

## V2.2 PDF Conversion & Document Tools

- [ ] PDF → Word
- [ ] PDF → Excel
- [ ] PDF → PowerPoint
- [ ] PDF digital signature
- [ ] PDF watermark
- [ ] PDF repair
- [ ] PDF/A conversion
- [ ] Batch PDF processing

V1 PDF operations such as merge, split, compression, rotation, encryption and decryption remain V1 engines and should be migrated to the V2 task pipeline rather than rewritten.

## V2.3 Image Engine

- [ ] WebP output
- [ ] AVIF support
- [ ] HEIC support
- [ ] Crop
- [ ] EXIF inspection/removal
- [ ] Batch image processing
- [ ] Background removal / AI cutout

## V2.4 Office Conversion

- [ ] PDF → Office formats
- [ ] Office format cross-conversion
- [ ] Batch conversion jobs
- [ ] Conversion presets

## V2.5 OCR & Document Intelligence

- [ ] Layout analysis
- [ ] Table recognition
- [ ] Structured JSON extraction
- [ ] Document classification
- [ ] OCR confidence and field-level results

## V2.6 AI Office

- [ ] AI PDF summary
- [ ] Document translation
- [ ] Document Q&A
- [ ] Contract analysis
- [ ] Invoice extraction
- [ ] AI-assisted document workflows

## V2.7 Platform

- [ ] Task history
- [ ] Workspace
- [ ] Batch jobs dashboard
- [ ] Authentication
- [ ] Admin console
- [ ] Public API / API keys
- [ ] Plugin / extension model

## Development rules

1. `main` remains the stable V1 line.
2. `develop` is the V2 integration line.
3. Do not reimplement verified V1 tools merely to move them to V2.
4. New file-processing features should use the Task + Storage architecture.
5. A feature is considered product-complete only when frontend entry, API, backend implementation and tests are aligned.
