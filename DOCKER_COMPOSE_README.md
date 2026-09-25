# ConfirmaSUS Local Development Environment (docker-compose)

Esta configuração permite executar todo o ambiente ConfirmaSUS localmente usando Docker Compose, facilitando desenvolvimento, testes e validação de deferred items.

## Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                     Docker Network (bridge)                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐   │
│  │  Gateway-Service │  │  Auth-Service    │  │  LocalStack  │   │
│  │      :8080       │  │     :8081        │  │    :4566     │   │
│  │                  │  └──────────────────┘  │  (SQS/SNS)   │   │
│  └────────┬─────────┘                        └──────────────┘   │
│           │                                                       │
│   ┌───────┴────────┬────────────┬────────────┬────────────┐    │
│   │                │            │            │            │    │
│   ▼                ▼            ▼            ▼            ▼    │
│ ┌──────────────┐ ┌──────────────────┐ ┌──────────────┐ ┌────┐ │
│ │   Triagem    │ │    Matching/     │ │ Agendamento  │ │Aud │ │
│ │    Score     │ │   Alocacao       │ │ Confirmacao  │ │    │ │
│ │   :8084      │ │    :8083         │ │   :8082      │ │ :8085
│ └──────────────┘ └──────────────────┘ └──────────────┘ └────┘ │
│        │                │                   │            │      │
│        └────────────────┴───────────────────┴────────────┘      │
│                         │                                        │
│                         ▼                                        │
│                    ┌──────────────┐                             │
│                    │  PostgreSQL  │                             │
│                    │    :5432     │                             │
│                    │              │                             │
│                    │ confirmasus  │                             │
│                    └──────────────┘                             │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Seed Adapter (runs once on startup)                       │ │
│  │  - Loads seed-data.json                                    │ │
│  │  - Populates Recursos → Agendamentos → ListaEspera        │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Pré-requisitos

- Docker Desktop (or Docker Engine + Docker Compose)
- Git
- ~8GB RAM available (para compilação Maven multi-módulo)

## Quickstart

### 1. Build das imagens (primeira execução)

```bash
cd /path/to/adjt-fase5-fila-justa
docker-compose build
```

**Nota:** Primeira build leva ~15-20 min (Maven compila todos os módulos)

### 2. Iniciar ambiente completo

```bash
docker-compose up
```

**Saída esperada (primeiros 30s):**
```
✓ postgres is healthy
✓ localstack is healthy
✓ auth-service is healthy
✓ gateway-service started
✓ triagem-score-service started
✓ matching-alocacao-service started
✓ agendamento-confirmacao-service started
✓ auditoria-service started
✓ seed-adapter loaded seed-data.json (5 recursos, 10 agendamentos, 5 lista-espera)
```

### 3. Validar que ambiente está pronto

```bash
# Gateway health
curl -s http://localhost:8080/actuator/health | jq .

# Database connection
psql -U postgres -h localhost -d confirmasus -c "SELECT COUNT(*) FROM recurso;" # Expected: 5
```

## Comandos Úteis

### Parar o ambiente

```bash
docker-compose down
```

### Parar e limpar volumes (reset completo)

```bash
docker-compose down -v
```

### Ver logs de um serviço específico

```bash
docker-compose logs -f auth-service
docker-compose logs -f gateway-service
docker-compose logs -f seed-adapter
```

### Executar comando em container

```bash
docker-compose exec postgres psql -U postgres -d confirmasus -c "SELECT * FROM recurso LIMIT 5;"
```

### Reconstruir uma imagem específica

```bash
docker-compose build --no-cache gateway-service
docker-compose up gateway-service
```

### Verificar status dos containers

```bash
docker-compose ps
```

## Acessando os Serviços

| Serviço | Porta | URL | Notas |
|---------|-------|-----|-------|
| Gateway | 8080 | http://localhost:8080 | Entry point (Spring Cloud Gateway) |
| Auth | 8081 | http://localhost:8081 | JWT issuer (health: /actuator/health) |
| Agendamento | 8082 | http://localhost:8082 | Agendamento service (health: :8091/actuator/health) |
| Matching | 8083 | http://localhost:8083 | Matching/Alocacao (health: :8092/actuator/health) |
| Triagem | 8084 | http://localhost:8084 | Triagem Score (health: :8093/actuator/health) |
| Auditoria | 8085 | http://localhost:8085 | Auditoria (health: :8094/actuator/health) |
| LocalStack | 4566 | http://localhost:4566 | SQS/SNS (AWS mock) |
| PostgreSQL | 5432 | localhost:5432 | Database |

## Deferred Items Validation

Agora que o ambiente está rodando, você pode executar as validações dos deferred items:

### Item 1: E2E Manual com Gateway Real + Banco Real

```bash
# Verificar contagens de dados carregados
psql -U postgres -h localhost -d confirmasus -c \
  "SELECT 
     (SELECT COUNT(*) FROM recurso) as recursos,
     (SELECT COUNT(*) FROM agendamentos) as agendamentos,
     (SELECT COUNT(*) FROM lista_espera) as lista_espera;"

# Expected output:
#  recursos | agendamentos | lista_espera
# ----------+--------------+--------------
#         5 |           10 |            5
```

### Item 2: Validação de Idempotência com Reexecução

```bash
# 1. Verificar contagens antes (já feito acima)

# 2. Parar seed-adapter, modificar e reiniciar (simular reexecução)
docker-compose down
docker-compose up seed-adapter -d

# 3. Verificar contagens novamente (devem ser idênticas)
psql -U postgres -h localhost -d confirmasus -c \
  "SELECT 
     (SELECT COUNT(*) FROM recurso) as recursos,
     (SELECT COUNT(*) FROM agendamentos) as agendamentos,
     (SELECT COUNT(*) FROM lista_espera) as lista_espera;"

# Expected: Mesmas contagens (idempotência garantida)
```

### Item 3: Teste de Resiliência com Gateway Indisponível

```bash
# 1. Parar gateway
docker-compose stop gateway-service

# 2. Tentar rodar seed-adapter (deve falhar com erro claro)
docker-compose run --rm seed-adapter 2>&1 | grep -i "gateway"

# Expected: Error message "Gateway indisponível"

# 3. Reiniciar gateway
docker-compose start gateway-service

# 4. Executar seed-adapter novamente (deve suceder)
docker-compose run --rm seed-adapter
```

## Troubleshooting

### "Connection refused" ao conectar ao banco

```bash
# Verificar se postgres está saudável
docker-compose ps postgres

# Se não está healthy, verifique logs
docker-compose logs postgres

# Aguarde alguns segundos e tente novamente (startup demora)
docker-compose logs -f postgres | grep "ready"
```

### "Address already in use" (porta ocupada)

```bash
# Identificar qual processo está usando a porta
lsof -i :8080

# Opção 1: Matar o processo
kill -9 <PID>

# Opção 2: Mudar porta em docker-compose.yml
# Mudar "8080:8080" para "8081:8080" (porta host diferente)
```

### Maven build falha no docker build

```bash
# Tentar rebuild forçando re-download de dependências
docker-compose build --no-cache --progress=plain auth-service

# Se ainda falhar, verificar logs Maven
docker-compose build auth-service 2>&1 | tail -100
```

### Serviço não inicia (CrashLoopBackOff)

```bash
# Ver logs do container
docker-compose logs -f <service-name>

# Verificar variáveis de ambiente
docker-compose config | grep -A 30 "<service-name>"

# Tente iniciar manualmente para debug
docker-compose run --rm <service-name> bash
```

## Environment Variables

Personalize comportamento editando `.env`:

```bash
cp .env.example .env
# Editar .env conforme necessário
docker-compose up
```

**Variáveis principais:**
- `POSTGRES_PASSWORD` — Senha do banco
- `JWT_SECRET` — Chave secreta JWT (mínimo 32 bytes)
- `TECH_USERNAME` / `TECH_PASSWORD` — Credenciais técnicas do seed-adapter
- Portas (se precisar mudar)

## Performance e Debugging

### Aumentar limite de memória JVM

Se serviços estão lentos ou crashando:

```yaml
# Em docker-compose.yml, adicionar ao serviço:
environment:
  JAVA_OPTS: "-Xms512m -Xmx1024m"
```

### Verificar recursos disponíveis

```bash
# No Docker Desktop, Preferences > Resources
# Recomendado: 6-8 CPU cores, 6-8 GB RAM
```

### Monitorar performance

```bash
# Ver stats em tempo real
docker stats

# Verificar latência de rede entre containers
docker exec confirmasus-gateway-service ping postgres
```

## Cleanup

### Remover tudo (reset completo)

```bash
docker-compose down -v
docker system prune -a
```

### Remover apenas imagens

```bash
docker-compose down
docker rmi $(docker images -q 'confirmasus*')
```

## Próximos Passos

1. **Validar deferred items:** Execute as validações acima
2. **Mergear feature/epic-5-deferred-items** em develop
3. **Mergear develop em master** com aprovação de QA
4. **Considerar:** Adicionar docker-compose ao CI/CD

## Suporte

- Dúvidas sobre docker-compose: `docker-compose help`
- Verificar versão: `docker-compose --version` (requer v2.0+)
- Docs: https://docs.docker.com/compose/
