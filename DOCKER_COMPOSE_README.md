# ConfirmaSUS Local Development Environment (docker-compose)

Esta configuração permite executar o backend do ConfirmaSUS localmente usando Docker Compose, facilitando desenvolvimento e testes manuais sem depender da AWS.

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
│   ┌───────┴────────┬────────────────────────┐                   │
│   │                │                         │                   │
│   ▼                ▼                         ▼                   │
│ ┌──────────────────┐ ┌──────────────┐ ┌────────────┐             │
│ │    Matching/     │ │ Agendamento  │ │ Auditoria  │             │
│ │   Alocacao       │ │ Confirmacao  │ │            │             │
│ │    :8083         │ │   :8082      │ │   :8085    │             │
│ └──────────────────┘ └──────────────┘ └────────────┘             │
│        │                   │                │                    │
│        └───────────────────┴────────────────┘                    │
│                         │                                        │
│                         ▼                                        │
│                    ┌──────────────┐                             │
│                    │  PostgreSQL  │                             │
│                    │    :5432     │                             │
│                    │              │                             │
│                    │ confirmasus  │                             │
│                    └──────────────┘                             │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

`liberacao-repasse-service` (renomeado de `matching-alocacao-service`) é o dono de `Recurso`/`Lista de Espera`/`Alocacao` — Sugestão de Repasse FIFO pura por ordem de chegada (AD-6). O schema Postgres continua `matching_alocacao` (nome legado mantido; ver `_bmad-output/planning-artifacts/architecture/architecture-Fase5-2026-09-17/ARCHITECTURE-SPINE.md`). O antigo `triagem-score-service` (Score de Prioridade Clínica) foi decomissionado por restrição legal — não existe mais no repositório.

## Pré-requisitos

- Docker Desktop (or Docker Engine + Docker Compose)
- Git
- ~8GB RAM available (para compilação Maven multi-módulo)

## Quickstart

### 1. Build das imagens (primeira execução)

```bash
cd /path/to/adjt-fase5-confirmasus
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
✓ liberacao-repasse-service started
✓ agendamento-confirmacao-service started
✓ auditoria-service started
```

O `seed-adapter` **não** sobe junto com `docker-compose up` — o serviço está comentado em `docker-compose.yml` (é um CLI Java standalone, não um serviço de longa duração). Para carregar dados de seed, construa e rode manualmente:

```bash
cd seed-adapter && mvn clean package
docker build -f seed-adapter/Dockerfile -t seed-adapter:latest .
docker run --network confirmasus-network \
  -e GATEWAY_URL=http://gateway-service:8080 \
  -e TECH_USERNAME=admin-tecnico -e TECH_PASSWORD=senha-tecnica-segura \
  seed-adapter:latest
```

**Importante:** por padrão, os relays de outbox/SNS/SQS estão **desabilitados** neste ambiente (`CONFIRMASUS_*_RELAY_ENABLED=false` em `docker-compose.yml`) — mesmo com o `localstack` (SQS/SNS mock) rodando, nenhum evento de domínio é de fato publicado/consumido entre serviços aqui. Para validar o fluxo assíncrono completo (outbox → SNS FIFO → SQS FIFO), habilite as flags de relay antes de subir o ambiente.

### 3. Validar que ambiente está pronto

```bash
# Gateway health
curl -s http://localhost:8080/actuator/health | jq .

# Database connection (tabelas são qualificadas por schema, um por serviço — AD-10)
psql -U postgres -h localhost -d confirmasus -c "SELECT COUNT(*) FROM matching_alocacao.recurso;"
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
```

### Executar comando em container

```bash
docker-compose exec postgres psql -U postgres -d confirmasus -c "SELECT * FROM matching_alocacao.recurso LIMIT 5;"
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
| Agendamento | 8082 | http://localhost:8082 | Agendamento/Confirmação (health: :8091/actuator/health) |
| Liberação/Repasse | 8083 | http://localhost:8083 | Recursos/Lista de Espera/Repasse (health: :8092/actuator/health) |
| Auditoria | 8085 | http://localhost:8085 | Auditoria (health: :8094/actuator/health) |
| LocalStack | 4566 | http://localhost:4566 | SQS/SNS (AWS mock) |
| PostgreSQL | 5432 | localhost:5432 | Database |

## Validando uma carga de seed

Depois de rodar o `seed-adapter` manualmente (passo 2 acima), confira as contagens (schemas reais, AD-10 — sem prefixo de schema a query falha):

```bash
psql -U postgres -h localhost -d confirmasus -c \
  "SELECT
     (SELECT COUNT(*) FROM matching_alocacao.recurso) as recursos,
     (SELECT COUNT(*) FROM agendamento_confirmacao.agendamentos) as agendamentos,
     (SELECT COUNT(*) FROM matching_alocacao.lista_espera_entrada) as lista_espera;"
```

O `seed-adapter` faz upsert idempotente (por `codigoRecurso`/CPF) — reexecutá-lo não deve alterar as contagens numa segunda carga com o mesmo `seed-data.json`.

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

## Suporte

- Dúvidas sobre docker-compose: `docker-compose help`
- Verificar versão: `docker-compose --version` (requer v2.0+)
- Docs: https://docs.docker.com/compose/
