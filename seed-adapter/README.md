# seed-adapter

Job de ingestão de dados sintéticos (Epic 5, Story 5.1+) para o ConfirmaSUS.

Autentica-se com JWT de usuário técnico via `auth-service` e popula catálogo de Recurso (Story 5.1), Agendamentos (Story 5.2) e Lista de Espera (Story 5.3) via gateway-service.

## Execução

### Variáveis de Ambiente Obrigatórias

```bash
export SEED_ADAPTER_USERNAME=seed-user        # Credencial técnica
export SEED_ADAPTER_PASSWORD=seed-password    # Credencial técnica
export AUTH_SERVICE_URL=http://auth-service:8080      # URL base do auth-service
export GATEWAY_SERVICE_URL=http://gateway-service:8080 # URL base do gateway-service
```

### Build

```bash
mvn clean package
```

### Execução Standalone

```bash
java -jar target/seed-adapter-0.1.0-SNAPSHOT.jar
```

### Saída Esperada

```
18:51:12.791 [main] INFO  c.c.seedadapter.SeedDataLoader - Iniciando carga de seed-data.json
18:51:12.792 [main] INFO  c.c.seedadapter.SeedDataLoader - Seed-data carregada com sucesso: 5 recursos
18:51:12.792 [main] INFO  c.c.seedadapter.SeedDataLoader - Upsertando 5 recursos
18:51:12.792 [main] INFO  c.c.seedadapter.RecursoClient - Upsertando Recurso: codigo=01, especialidade=Cardiologia, unidade=Hospital Central, rank=1, disponivel=true
18:51:13.100 [main] INFO  c.c.seedadapter.RecursoClient - Recurso criado com sucesso: recursoId=<uuid>, codigo=01
...
INFO  c.c.seedadapter.SeedAdapterMain - Seed-adapter executado com sucesso
```

## Testes

```bash
mvn test
# Resultados esperados: todos os testes devem passar com cobertura >= 90%
```

## Características

- **Autenticação centralizada**: obtém JWT via `auth-service` e reutiliza em todas as requisições
- **Idempotência**: execução repetida não duplica dados (upsert por chave de negócio)
- **Falha explícita**: aborta com erro claro se `auth-service` ou `gateway-service` indisponível
- **Logging estruturado**: todos os eventos registrados via SLF4J/Logback

## Seed-Data

Arquivo `src/main/resources/seed-data.json` contém dataset de demonstração:

```json
{
  "recursos": [
    {
      "codigoRecurso": "01",
      "especialidade": "Cardiologia",
      "unidade": "Hospital Central",
      "especificidadeRank": 1,
      "disponivel": true
    },
    ...
  ]
}
```

Adicionar/modificar entrada para incluir novos recursos no dataset.

## Dependências

- Java 25+
- Apache HttpClient 5.2.1
- Jackson 2.16.1 (JSON parsing)
- SLF4J 2.0.11 + Logback 1.4.14
- JUnit 5 (testes)
