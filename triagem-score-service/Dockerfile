# Build context = raiz do repositorio
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY infra-cdk/pom.xml infra-cdk/pom.xml
COPY triagem-score-service/pom.xml triagem-score-service/pom.xml
COPY triagem-score-service/src triagem-score-service/src

RUN mvn -q -pl triagem-score-service -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin filajusta
USER filajusta
WORKDIR /app
COPY --from=build /workspace/triagem-score-service/target/triagem-score-service.jar app.jar

EXPOSE 8084 8093
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
