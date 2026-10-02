# Epic 5 — Deferred Items: Plano de Testes e Validação

**Branch:** `feature/epic-5-deferred-items`  
**Data:** 2026-09-23  
**Status:** in-progress

---

## Deferred Item 1: E2E Manual com Gateway Real + Banco Real

### Descrição
Validação manual que seed-adapter orquestra dataset completo ponta-a-ponta contra serviços reais (não mocks de testes).

### Scope
- Verificar que todos os 3 clientes HTTP (RecursoClient, AgendamentoClient, ListaEsperaClient) fazem requisições reais
- Confirmar que dados persistem no banco (matching-alocacao-service, agendamento-confirmacao-service, liberacao-repasse-service)
- Validar que transições de estado ocorrem (Agendamento vai de AGUARDANDO_JANELA → CONFIRMADO/LIBERADO)
- Checar logs do seed-adapter mostrando progresso de cada fase

### Pré-requisitos
- [ ] 5 serviços rodando (auth-service, gateway-service, matching-alocacao-service, agendamento-confirmacao-service, liberacao-repasse-service)
- [ ] PostgreSQL em execução (database ConfirmaSUS)
- [ ] Env vars configuradas: GATEWAY_URL, TECH_USERNAME, TECH_PASSWORD
- [ ] seed-adapter JAR construído (`mvn clean package` no seed-adapter)

### Teste Prático
```bash
# 1. Limpar banco (opcional, para reexecução limpa)
# (Não fazer em prod; aqui é dev/test)
# TODO: Executar truncate em tabelas seed-adapter

# 2. Rodar seed-adapter primeira vez
export GATEWAY_URL=http://localhost:8080
export TECH_USERNAME=admin-tecnico
export TECH_PASSWORD=<valor-secreto>
docker run -e GATEWAY_URL -e TECH_USERNAME -e TECH_PASSWORD \
  -v $(pwd)/seed-data.json:/app/seed-data.json \
  seed-adapter:latest

# 3. Verificar logs — esperar:
# "Autenticação bem-sucedida: JWT obtido"
# "Recursos carregados: 5"
# "Agendamentos carregados: 10"
# "Lista de Espera carregada: 3"
# "Seed-adapter finalizado com sucesso"

# 4. Validar dados no banco
psql -U postgres -d confirmasus -c "SELECT COUNT(*) FROM recurso;"  # Expected: 5
psql -U postgres -d confirmasus -c "SELECT COUNT(*) FROM agendamentos;" # Expected: 10
psql -U postgres -d confirmasus -c "SELECT COUNT(*) FROM lista_espera;" # Expected: 3

# 5. Validar estados dos Agendamentos
psql -U postgres -d confirmasus -c \
  "SELECT status_agendamento, COUNT(*) FROM agendamentos GROUP BY status_agendamento;"
# Expected: Algumas linhas com CONFIRMADO, LIBERADO, etc. (mix de estados)
```

### Critério de Aceitar
- ✓ Logs mostram todas as 3 fases completadas sem erro
- ✓ COUNT(*) em banco == entradas esperadas
- ✓ Estados dos Agendamentos variam (não todos AGUARDANDO_JANELA)
- ✓ Nenhuma mensagem de erro em logs do seed-adapter

### Owner
Desenvolvedor / QA

### Deferred Status
Não bloqueante para merge em develop; recomendado antes de merge para master

---

## Deferred Item 2: Validação de Idempotência com Reexecução

### Descrição
Rodar seed-adapter 2x contra **mesmo banco** e validar que contagens não aumentam (idempotência funciona).

### Scope
- Confirmar que upsert de Recursos não duplica (reexecução retorna 200 OK, mesma recursoId)
- Confirmar que Agendamentos não duplicam (detecção local de duplicata pula POST)
- Confirmar que entradas de ListaEspera não duplicam (detecção local pula POST)

### Pré-requisitos
- [ ] Database ConfirmaSUS com seed-data carregada (de Item 1)
- [ ] Env vars configuradas como em Item 1
- [ ] Conseguir fazer reexecução sem truncate (idempotência é o ponto)

### Teste Prático
```bash
# 1. Verificar contagens PRÉ-reexecução (após Item 1)
psql -U postgres -d confirmasus -c "SELECT COUNT(*) FROM recurso AS recurso_count;"
psql -U postgres -d confirmasus -c "SELECT COUNT(*) FROM agendamentos AS agendamentos_count;"
psql -U postgres -d confirmasus -c "SELECT COUNT(*) FROM lista_espera AS lista_count;"
# Anotar números (ex: recurso=5, agendamentos=10, lista_espera=3)

# 2. Rodar seed-adapter segunda vez (EXATAMENTE mesma seed-data.json)
export GATEWAY_URL=http://localhost:8080
export TECH_USERNAME=admin-tecnico
export TECH_PASSWORD=<valor-secreto>
docker run -e GATEWAY_URL -e TECH_USERNAME -e TECH_PASSWORD \
  -v $(pwd)/seed-data.json:/app/seed-data.json \
  seed-adapter:latest

# 3. Esperar logs — devem mostrar:
# "Autenticação bem-sucedida: JWT obtido"
# "Recursos carregados: 5" (não "Recursos criados", mas "carregados" = idempotentes)
# "Agendamentos carregados: 10"
# "Lista de Espera carregada: 3"

# 4. Verificar contagens PÓS-reexecução
psql -U postgres -d confirmasus -c "SELECT COUNT(*) FROM recurso;"  # Expected: AINDA 5
psql -U postgres -d confirmasus -c "SELECT COUNT(*) FROM agendamentos;" # Expected: AINDA 10
psql -U postgres -d confirmasus -c "SELECT COUNT(*) FROM lista_espera;" # Expected: AINDA 3

# 5. Validar que IDs não mudaram (exemplo com Recursos)
psql -U postgres -d confirmasus -c \
  "SELECT codigo_recurso, recurso_id FROM recurso ORDER BY codigo_recurso LIMIT 1;"
# Anotar recurso_id (ex: "a1b2c3d4-e5f6-7890-..."), rodar novamente seed-adapter,
# verificar que mesmo codigo_recurso retorna MESMO recurso_id
```

### Critério de Aceitar
- ✓ Contagens permanecem idênticas após reexecução
- ✓ Logs mostram "carregados" (não "criados")
- ✓ IDs das entidades permanecem idênticos

### Owner
Desenvolvedor / QA

### Deferred Status
Recomendado antes de merge para master; valida suposição de idempotência

---

## Deferred Item 3: Teste de Resiliência com Gateway Indisponível

### Descrição
Simular gateway offline e validar que seed-adapter falha com mensagem clara (não ambígua, não silent failure).

### Scope
- Confirmar que seed-adapter detecta gateway indisponível
- Confirmar que falha aborta com `IllegalStateException` contendo "Gateway indisponível"
- Confirmar que logs descrevem o erro claramente

### Pré-requisitos
- [ ] seed-adapter em execução ou pronto para rodar
- [ ] Capacidade de bloquear/desligar gateway (firewall rule, stop container, etc.)

### Teste Prático
```bash
# 1. Verificar que gateway ESTÁ online (baseline)
curl -s http://localhost:8080/actuator/health | jq .status
# Expected: "UP"

# 2. Parar o gateway (se em Docker)
docker stop gateway-service

# 3. Tentar rodar seed-adapter
export GATEWAY_URL=http://localhost:8080
export TECH_USERNAME=admin-tecnico
export TECH_PASSWORD=<valor-secreto>
docker run -e GATEWAY_URL -e TECH_USERNAME -e TECH_PASSWORD \
  -v $(pwd)/seed-data.json:/app/seed-data.json \
  seed-adapter:latest 2>&1 | tee seed-adapter-offline.log

# 4. Verificar logs — devem conter:
# "java.lang.IllegalStateException: Gateway indisponível"
# OU
# "Connection refused" com mensagem clara (não timeout silencioso)
# OU
# "Failed to authenticate: auth-service unreachable"

# 5. Verificar exit code
echo $?
# Expected: non-zero (1, 127, etc.)

# 6. Reiniciar gateway
docker start gateway-service
```

### Critério de Aceitar
- ✓ Seed-adapter falha com exit code != 0
- ✓ Log contém "Gateway indisponível" ou "Connection refused" ou similar
- ✓ Não há ambiguidade (ex: não é timeout silencioso de 30s)
- ✓ Mensagem é útil para debugging (não apenas "Error")

### Owner
Desenvolvedor / SRE

### Deferred Status
Desejável, não crítico; melhora resiliência operacional

---

## Plano de Execução

| Item | Prioridade | Bloqueante? | Owner | Timeline |
|------|-----------|------------|-------|----------|
| 1. E2E Manual | Alta | Não (recomendado antes master) | Dev/QA | ASAP (próxima sessão) |
| 2. Idempotência Reexecução | Média | Não (valida suposição) | Dev/QA | ASAP (próxima sessão) |
| 3. Resiliência Gateway Offline | Baixa | Não (desejável) | Dev/SRE | Opcional (nice-to-have) |

---

## Próximas Sessões

**Sessão 1 (próxima):**
1. Executar Item 1 (E2E Manual)
2. Se passar: documentar resultado em `epic-5-deferred-items-validation.md`
3. Commitar resultado

**Sessão 2 (se necessário):**
1. Executar Item 2 (Idempotência Reexecução)
2. Se passar: documentar resultado

**Sessão 3 (se necessário):**
1. Executar Item 3 (Resiliência Gateway Offline)
2. Se passar: considerar Item concluído

---

## Documentação de Resultados

Após cada teste, criar `epic-5-deferred-items-validation.md` com:
- Data/hora da execução
- Comandos exatos rodados
- Output dos testes (logs, contagens)
- Resultado (PASS/FAIL)
- Observações

Exemplo:
```markdown
## Item 1: E2E Manual — 2026-09-24

**Data:** 2026-09-24 14:00 UTC  
**Executor:** Thiago Ferreira  
**Status:** ✅ PASS

### Comandos
\`\`\`bash
docker run -e GATEWAY_URL=http://localhost:8080 ...
\`\`\`

### Resultados
- Recursos: 5 ✓
- Agendamentos: 10 ✓
- ListaEspera: 3 ✓

### Logs
(excerpt dos logs relevantes)

### Notas
(observações, próximas ações)
```
