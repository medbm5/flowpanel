# syntax=docker/dockerfile:1

# ---- build: JDK + Maven wrapper, dependency layer cached separately from sources
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline
COPY src src
RUN ./mvnw -B -ntp -q package -DskipTests && cp target/flowpanel-backend.jar /app.jar

# ---- runtime: JRE only, non-root, sized for a 512 MB container
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 flowpanel
WORKDIR /app
COPY --from=build /app.jar app.jar
USER flowpanel
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# server.port reads $PORT (Render injects it).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
