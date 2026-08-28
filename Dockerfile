FROM node:22-bookworm-slim AS frontend-build
WORKDIR /app/frontend
COPY frontend/package.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY backend/pom.xml ./backend/pom.xml
RUN mvn -f backend/pom.xml dependency:go-offline -B
COPY backend/ ./backend/
COPY --from=frontend-build /app/frontend/dist/ ./backend/src/main/resources/static/
RUN mvn -f backend/pom.xml package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends libreoffice \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=backend-build /app/backend/target/*.jar /app/officebox.jar
RUN mkdir -p /app/data /tmp/officebox \
    && useradd --create-home --uid 10001 officebox \
    && chown -R officebox:officebox /app /tmp/officebox
USER officebox
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/officebox.jar"]
