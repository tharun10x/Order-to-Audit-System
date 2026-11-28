# Backend image: build the jar with Maven on JDK 21, then run it on a Java 21 JRE as a non-root user.
# Tests are skipped here because CI runs all of them (they need Docker for Testcontainers).

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies first, so a code change doesn't download them again
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- run ----------
FROM eclipse-temurin:21-jre
# Fonts for Excel column sizing come from the JRE image (fontconfig); the PDF font is inside the jar
RUN groupadd --system ota && useradd --system --gid ota --no-create-home ota
WORKDIR /app
COPY --from=build /build/target/backend-*.jar app.jar
USER ota
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
