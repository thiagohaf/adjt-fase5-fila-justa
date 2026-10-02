# Build context = seed-adapter directory
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin confirmasus
USER confirmasus
WORKDIR /app
COPY --from=build /workspace/target/seed-adapter*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
