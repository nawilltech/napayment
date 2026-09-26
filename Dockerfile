# Multi-stage build: compile the full Maven reactor, ship only the
# resulting executable jar (app/pom.xml's spring-boot-maven-plugin
# `exec` classifier - see app/target/nawill-pay-app-exec.jar).
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Copy just the POMs first so dependency resolution is its own Docker
# layer, cached across builds whenever only source (not a POM) changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common-core/pom.xml common-core/pom.xml
COPY reference-data/pom.xml reference-data/pom.xml
COPY payments/pom.xml payments/pom.xml
COPY onboarding-auth-rbac/pom.xml onboarding-auth-rbac/pom.xml
COPY app/pom.xml app/pom.xml
RUN ./mvnw -B -q dependency:go-offline || true

COPY common-core/src common-core/src
COPY reference-data/src reference-data/src
COPY payments/src payments/src
COPY onboarding-auth-rbac/src onboarding-auth-rbac/src
COPY app/src app/src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN useradd --system --create-home --shell /usr/sbin/nologin appuser
WORKDIR /app
COPY --from=build /workspace/app/target/nawill-pay-app-exec.jar app.jar
# /app is otherwise root-owned (WORKDIR + COPY both ran as root above) -
# appuser needs write access here itself since LocalFileStorageGateway
# creates .data/uploads/<ownerId> at runtime, not at build time.
RUN mkdir -p /app/.data/uploads && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
