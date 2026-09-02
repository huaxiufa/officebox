# syntax=docker/dockerfile:1.7

FROM node:22-bookworm-slim AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --prefer-offline
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY backend/pom.xml ./backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f backend/pom.xml dependency:go-offline -B
COPY backend/ ./backend/
COPY --from=frontend-build /app/frontend/dist/ ./backend/src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f backend/pom.xml package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && apt-get install -y --no-install-recommends libreoffice ghostscript poppler-utils curl python3 python3-venv \
        tesseract-ocr tesseract-ocr-chi-sim tesseract-ocr-eng \
    && python3 -m venv /opt/pdf2docx-venv \
    && /opt/pdf2docx-venv/bin/pip install --no-cache-dir --disable-pip-version-check pdf2docx \
    && python3 -m venv /opt/docling-venv \
    && /opt/docling-venv/bin/pip install --no-cache-dir --disable-pip-version-check \
        torch==2.6.0 torchvision==0.21.0 --index-url https://download.pytorch.org/whl/cpu \
    && /opt/docling-venv/bin/pip install --no-cache-dir --disable-pip-version-check docling==2.124.0 PyMuPDF python-docx \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=backend-build /app/backend/target/*.jar /app/officebox.jar
COPY --from=backend-build /app/backend/src/main/resources/tools/pdf2docx_bridge.py /app/pdf2docx_bridge.py
COPY --from=backend-build /app/backend/src/main/resources/tools/docling_bridge.py /app/docling_bridge.py
COPY --from=backend-build /app/backend/src/main/resources/tools/docling_docx_bridge.py /app/docling_docx_bridge.py
RUN mkdir -p /app/data /tmp/officebox \
    && useradd --create-home --uid 10001 officebox \
    && chown -R officebox:officebox /app /tmp/officebox
USER officebox
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0" \
    DOCLING_DEVICE=cpu
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/officebox.jar"]
