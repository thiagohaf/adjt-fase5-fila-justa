# Item 1 E2E Manual — Status de Validação

## Resumo
- **Data:** 2026-09-27
- **Objetivo:** Validar Item 1 (E2E Manual) dos deferred items do Epic 5
- **Status:** Em progresso com bloqueador de validação JWT

## Progresso

### ✅ Concluído
1. **Correção de dialetos Hibernate**
   - Trocado de `PostgreSQL18Dialect` / `PostgreSQL15Dialect` para `PostgreSQLDialect`
   - docker-compose.yml: atualizado
   - triagem-score-service/application.yml: atualizado

2. **Configuração de relays AWS**
   - Desabilitados relays SQS/SNS para ambiente local
   - Flags adicionadas: `CONFIRMASUS_*_RELAY_ENABLED=false`

3. **Correção de segredo JWT**
   - Chaves aumentadas para ≥ 32 bytes (256 bits)
   - auth-service: `CONFIRMASUS_JWT_SECRET` = 33 bytes
   - gateway-service: `CONFIRMASUS_JWT_SECRET` = 34 bytes

4. **Roteamento do Gateway**
   - Adicionadas rotas faltantes ao application.yml do gateway-service:
     - `/v1/recursos/**,/v1/alocacoes/**` → matching-alocacao-service:8083
     - `/v1/triagem/**` → triagem-score-service:8084
     - `/v1/auditoria/**` → auditoria-service:8085

5. **Docker-compose operacional**
   - Todos 6 serviços UP and healthy
   - Seed-adapter compila e executa

### ⏳ Em Progresso
1. **Validação JWT no Gateway**
   - Seed-adapter consegue gerar JWT com sucesso
   - Gateway rejeita token (401: "Token ausente, expirado ou com assinatura inválida")
   - **Causa provável:** Chaves JWT diferentes entre auth-service e gateway-service

## Bloqueador Identificado

```
Falha ao upsertar Recurso (status 401): 
"Token ausente, expirado ou com assinatura invalida"
```

### Investigação
- Auth-service gera JWT válido → confirmado via log "JWT obtido com sucesso, válido por ~55 minutos"
- Gateway valida JWT → falha na validação (401)
- **Hipótese:** Validação de signature falha porque chaves são diferentes

## Checklist para Próxima Sessão

1. [ ] Verificar se as chaves JWT estão sincronizadas:
   - Ambas precisam ser IDÊNTICAS (auth-service gera, gateway valida)
   - Atualmente têm tamanhos diferentes (33 vs 34 bytes)

2. [ ] Usar chave idêntica em ambos os serviços

3. [ ] Validar assinatura JWT:
   ```bash
   # Decodificar token e verificar claims
   echo $TOKEN | cut -d. -f1 | base64 -d | jq .
   ```

4. [ ] Testar seed-adapter novamente:
   ```bash
   docker run --rm --network fase5_confirmasus-network \
     -e SEED_ADAPTER_USERNAME=regulador \
     -e SEED_ADAPTER_PASSWORD="regulador#2026" \
     -e AUTH_SERVICE_URL=http://auth-service:8081 \
     -e GATEWAY_SERVICE_URL=http://gateway-service:8080 \
     seed-adapter:latest
   ```

5. [ ] Validar contagens no DB:
   - recursos=5 ✓
   - agendamentos=10 ✓
   - lista_espera=3 (não testado ainda)

6. [ ] Documentar resultado em epic-5-deferred-items-validation.md

7. [ ] Commitar + PR (se PASS)

## Arquivos Modificados

1. ✅ `docker-compose.yml`
   - Dialetos Hibernate: PostgreSQL18Dialect → PostgreSQLDialect
   - Relays desabilitados para matching, agendamento, auditoria
   - Chaves JWT aumentadas

2. ✅ `triagem-score-service/src/main/resources/application.yml`
   - Dialeto: PostgreSQL15Dialect → PostgreSQLDialect

3. ✅ `gateway-service/src/main/resources/application.yml`
   - Adicionadas 3 novas rotas (matching, triagem, auditoria)

4. ✅ `seed-adapter/Dockerfile`
   - Corrigido build context (pom.xml + src ao invés de recursivo)
   - Caminho correto para JAR no stage final

## Portas e URLs

```
Auth Service:        http://localhost:8081 (ou :8090 management)
Gateway:             http://localhost:8080
Matching:            http://localhost:8083 (ou :8092 management)
Agendamento:         http://localhost:8082 (ou :8091 management)
Triagem:             http://localhost:8084 (ou :8093 management)
Auditoria:           http://localhost:8085 (ou :8094 management)
PostgreSQL:          localhost:5432
LocalStack (SQS/SNS): localhost:4566
```

## Próximos Passos

1. **Fixar validação JWT** (bloqueador crítico)
2. **Rodar seed-adapter** e validar saída
3. **Consultar BD** para validar contagens
4. **Documentar resultado** em epic-5-deferred-items-validation.md
5. **Commitar + PR** para develop

## Notas Técnicas

- Seed-adapter usa credencial `regulador` / `regulador#2026` (pré-cadastrada no V1 migration)
- Para adicionar novo usuário técnico, criar V2 migration com hash BCrypt
- Dockerfile do seed-adapter agora usa build context correto (seed-adapter/)
- Docker-compose em local não precisa de SQS/SNS real — usar LocalStack quando necessário
