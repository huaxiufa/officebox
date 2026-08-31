# OfficeBox V2 Architecture

## Goal

V2 evolves OfficeBox from a collection of utilities into an open-source, self-hostable document workspace while preserving V1 behavior.

## Branching

- `main`: stable releases only.
- `develop`: V2 integration branch.
- `feature/*`: isolated feature work, merged into `develop` through PRs.

## Backend boundaries

```text
com.officebox
├── common
│   ├── api          # shared response/error contracts
│   ├── config       # cross-cutting Spring configuration
│   ├── exception    # shared exception handling
│   └── storage      # temporary/result file lifecycle
├── controller       # HTTP adapters
├── service          # business orchestration
├── repository       # persistence when introduced
└── module
    ├── pdf
    ├── image
    ├── office
    ├── ocr
    └── ai
```

New V2 modules should keep HTTP concerns in controllers and reusable processing logic in services. Existing V1 implementations should be migrated incrementally rather than moved in one risky change.

## File processing model

V2 processing should converge on:

```text
upload -> validate -> create task -> process -> persist result -> cleanup
```

Long-running operations such as OCR, Office conversion and AI processing should be task-oriented. Synchronous V1 endpoints remain compatible until their V2 replacements are proven.

## Storage

Shared storage configuration is introduced under `officebox.storage`. The default root is `./data`; deployments can override it with `OFFICEBOX_STORAGE_ROOT`. Default retention is 24 hours and will be enforced by the V2 cleanup service in a later milestone.

## API contract

New V2 endpoints should use `ApiResponse<T>` for successful responses and the shared exception handler for failures. Existing V1 response formats are not changed during the foundation phase.

## Frontend direction

The frontend should converge on:

```text
src/
├── components/      # reusable UI
├── layouts/         # application shells
├── modules/         # pdf/image/office/ocr/ai
├── pages/           # route-level pages
├── services/        # API clients
├── composables/     # shared Vue logic
└── types/           # API/domain types
```

## Delivery order

1. Foundation and CI
2. Shared file/task infrastructure
3. PDF V2
4. Image V2
5. Office V2
6. OCR V2
7. AI workspace
