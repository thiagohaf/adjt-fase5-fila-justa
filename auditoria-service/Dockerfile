# Build context = raiz do repositorio
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY . .
RUN mvn -q -pl auditoria-service -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin filajusta
USER filajusta
WORKDIR /app
COPY --from=build /workspace/auditoria-service/target/auditoria-service.jar app.jar

EXPOSE 8085 8094
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
