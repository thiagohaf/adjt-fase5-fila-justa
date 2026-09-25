# Build context = raiz do repositorio
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY . .
RUN mvn -q -pl seed-adapter -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin filajusta
USER filajusta
WORKDIR /app
COPY --from=build /workspace/seed-adapter/target/seed-adapter*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
