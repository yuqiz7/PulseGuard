# AI-generated: Claude (Anthropic)
# Container image for Cloud Run. Build stage: package the jar with the Maven Wrapper
# (tests and style checks run in CI). Run stage: JRE only, non-root user.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN chmod +x mvnw && ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /app/target/pulseguard-*.jar app.jar
USER app
# Use most of the container's memory for the heap (the JVM default is 25%).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Cloud Run sets PORT (8080 by default); application.properties reads it.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
