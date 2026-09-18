# Build context = raiz do repositorio (ver infra-cdk ContainerImage.fromAsset,
# que aponta directory=".." e file="agendamento-confirmacao-service/Dockerfile").
# Precisa do pom.xml pai (reactor multi-modulo) para resolver o BOM
# Spring Boot/Spring Cloud (AD-13) mesmo construindo so este modulo.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY auth-service/pom.xml auth-service/pom.xml
COPY infra-cdk/pom.xml infra-cdk/pom.xml
COPY matching-alocacao-service/pom.xml matching-alocacao-service/pom.xml
COPY agendamento-confirmacao-service/pom.xml agendamento-confirmacao-service/pom.xml
COPY agendamento-confirmacao-service/src agendamento-confirmacao-service/src

RUN mvn -q -pl agendamento-confirmacao-service -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --create-home --shell /usr/sbin/nologin filajusta
USER filajusta
WORKDIR /app
COPY --from=build /workspace/agendamento-confirmacao-service/target/agendamento-confirmacao-service.jar app.jar

EXPOSE 8082 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
