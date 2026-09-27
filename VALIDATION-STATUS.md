# Item 1 E2E Manual — Status de Validação

## Resumo
- **Data:** 2026-09-27
- **Objetivo:** Validar Item 1 (E2E Manual) dos deferred items do Epic 5
- **Status:** ✅ JWT sincronizado e validado com sucesso

## Progresso

### ✅ Concluído
1. **Correção de dialetos Hibernate**
   - Trocado de `PostgreSQL18Dialect` / `PostgreSQL15Dialect` para `PostgreSQLDialect`
   - docker-compose.yml: atualizado
   - triagem-score-service/application.yml: atualizado

2. **Configuração de relays AWS**
   - Desabilitados relays SQS/SNS para ambiente local
   - Flags adicionadas: `CONFIRMASUS_*_RELAY_ENABLED=false`

3. **Correção de segredo JWT** ✅ FIXADO
   - Chaves sincronizadas e aumentadas para 32 bytes (256 bits)
   - Ambos serviços agora usam: `supersecretkey123456789012345678`
   - Testado: seed-adapter conseguiu autenticar com sucesso

4. **Roteamento do Gateway**
   - Adicionadas rotas faltantes ao application.yml do gateway-service:
     - `/v1/recursos/**,/v1/alocacoes/**` → matching-alocacao-service:8083
     - `/v1/triagem/**` → triagem-score-service:8084
     - `/v1/auditoria/**` → auditoria-service:8085

5. **Docker-compose operacional**
   - Todos 6 serviços UP and healthy
   - Seed-adapter compila e executa

### ✅ Teste E2E com Seed-Adapter (2026-09-27 02:09)
1. **Validação JWT no Gateway** ✅ PASS
   - Seed-adapter obteve JWT com sucesso
   - Gateway validou token corretamente (chaves sincronizadas)
   - Token válido por ~55 minutos
   
2. **Carregamento de Recursos** ✅ 5/5 PASS
   - codigo=01 (Cardiologia, Hospital Central) → UUID: 60676e7c-f8d9-4f31-802d-aee2ad366716
   - codigo=02 (Cirurgia Geral, Hospital Central) → UUID: 512a8866-b667-429c-a644-6d0b05c12a1e
   - codigo=03 (Radiologia, UBS Zona Leste) → UUID: a45cd950-a8b2-4ee2-a0b2-d9673be24e44
   - codigo=04 (Oftalmologia, Clínica Privada) → UUID: d8ffc2eb-631c-4a98-b286-148a9fa18504
   - codigo=05 (Pediatria, Hospital Central) → UUID: 6557bec9-cfc0-45ba-8169-0df24ceb537b
   
3. **Carregamento de Agendamentos** ⚠️ ERRO DE PARSING
   - Falha ao processar resposta do agendamento-service
   - Erro: "Invalid UUID string: 1"
   - Causa: agendamento-service retorna ID como inteiro (1) em vez de UUID
   - Status: Bloqueador secundário (afeta Item 1 apenas)

## Bloqueador Secundário Identificado

```
Falha ao carregar agendamentos: 
"Invalid UUID string: 1"
```

### Investigação
- agendamento-service retorna ID como inteiro (1, 2, ...) na resposta 201 Created
- seed-adapter espera UUID (UUID v4) na resposta
- **Causa:** Falta de migração ou mapeamento no agendamento-service para retornar UUID em vez de ID

### Impacto
- Item 1 (E2E Manual): ⚠️ Parcialmente validado (recursos OK, agendamentos bloqueados)
- Item 2 (Idempotência): Não afetado (não depende de agendamentos)
- Item 3 (Resiliência): Não afetado (não depende de agendamentos)

## Checklist Completado

1. ✅ Sincronizou chaves JWT (ambas = 32 bytes)
2. ✅ Testou seed-adapter com novo JWT
3. ✅ Validou carregamento de 5 recursos (100% sucesso)
4. ✅ Identificou erro de parsing em agendamentos
5. ✅ Documentou resultado neste arquivo

## Próximas Ações Recomendadas

1. **Fixar agendamento-service** (investigação):
   - Verificar resposta HTTP 201 do endpoint POST /agendamentos
   - Garantir que retorna UUID em vez de ID
   - Testar agendamento com ferramenta manual (curl ou Postman)

2. **Reexecutar seed-adapter** após fix

3. **Validar contagens no DB:**
   ```bash
   # PostgreSQL
   psql -h localhost -U postgres -d confirmasus -c "SELECT COUNT(*) FROM recursos;"
   psql -h localhost -U postgres -d confirmasus -c "SELECT COUNT(*) FROM agendamentos;"
   psql -h localhost -U postgres -d confirmasus -c "SELECT COUNT(*) FROM lista_espera;"
   ```

4. **Commitar + PR** quando recursos e agendamentos forem carregados com sucesso

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
