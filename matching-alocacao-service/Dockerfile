# Build context = raiz do repositorio
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy entire project (pom.xml + all modules)
COPY . .

# Build matching-alocacao-service and dependencies
RUN mvn -q -pl matching-alocacao-service -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin filajusta
USER filajusta
WORKDIR /app
COPY --from=build /workspace/matching-alocacao-service/target/matching-alocacao-service.jar app.jar

EXPOSE 8083 8092
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
