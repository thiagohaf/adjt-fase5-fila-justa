# Build context = raiz do repositorio (ver infra-cdk ContainerImage.fromAsset,
# que aponta directory=".." e file="gateway-service/Dockerfile").
# Precisa do pom.xml pai (reactor multi-modulo) para resolver o BOM
# Spring Boot/Spring Cloud (AD-13) mesmo construindo so este modulo.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY infra-cdk/pom.xml infra-cdk/pom.xml
COPY gateway-service/src gateway-service/src

RUN mvn -q -pl gateway-service -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin filajusta
USER filajusta
WORKDIR /app
COPY --from=build /workspace/gateway-service/target/gateway-service.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
