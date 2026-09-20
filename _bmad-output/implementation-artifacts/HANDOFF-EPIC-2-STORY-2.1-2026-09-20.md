# Handoff — Próxima Sessão (Story 2.2)

**Data:** 2026-09-20  
**Sessão:** Story 2.1 (Registrar Triagem e Calcular Score) — Concluída  
**Próximo:** Story 2.2 (Consulta de Triagem com Score e Fatores Contribuintes)

---

## Resumo do PR #56

### O que foi entregue

**Story 2.1** — Novo serviço `triagem-score-service` (Clean Architecture, Spring Boot)

- Endpoint `POST /v1/triagens` validando CPF, sinais vitais, calculando score determinístico e persistindo com outbox
- **Value objects:**
  - `Cpf` (checksum validado)
  - `SinaisVitais` (faixas fisiológicas: PAS/PAD, FC, SO2, Glicemia)
  - `Score` v1 (média ponderada 0-100 com subnota por vital + peso gravidade)
  - `FatorContribuinte` (identificação de qual sinal mais impactou o score)
- **Idempotência de Paciente:** CPF resolve existente ou cria novo implicitamente na mesma transação
- **Testes:** 9 unit tests (CpfTest, CalculadorDeScoreTest) — todos passando, 96,6% cobertura linha domínio
- **PR:** [#56](https://github.com/thiagohaf/adjt-fase5-fila-justa/pull/56)
- **Branch:** `feature/2-1-triagem-score-service` (pode deletar)

---

## Estado Atual

- **Branch:** `develop` (atualizada com `origin/develop`)
- **Working tree:** limpa (sem mudanças não-commitadas)
- **Última merge:** 41 arquivos (2128 linhas) — Story 2.1 integrada

---

## Próximos Passos Sugeridos

1. **Story 2.2** (Consulta de Triagem com Score e Fatores) — roadmap Epic 2
2. Remover branch local `feature/2-1-triagem-score-service` se desejar (`git branch -d`)
3. Integration tests com Testcontainers para Story 2.1 (requer `spring-boot-starter-webmvc` em test scope)
4. Revisar próximas stories do Epic 2 (Fila + Score — Stories 2.3+)

---

## Contexto Técnico

### Arquivos-chave

- `triagem-score-service/src/main/java/com/filajusta/triagem/domain/CalculadorDeScore.java`  
  Algoritmo v1: média ponderada sinais vitais (normalização à faixa) + fator gravidade (LEVE=0, MODERADA=0.33, GRAVE=0.66, CRITICA=1)

- `triagem-score-service/src/main/java/com/filajusta/triagem/application/command/RegistrarTriagem.java`  
  Orquestração completa: validação → resolução paciente → cálculo → persistência + outbox

- `triagem-score-service/src/main/resources/db/migration/V1__create_triagem_schema.sql`  
  Schema `triagem_score` com tabelas `pacientes`, `triagens` (JSONB para valores vitais), `eventos_outbox`

- `pom.xml` — novo módulo `triagem-score-service` adicionado ao reactor

### Decisões Tomadas

- **Faixas fisiológicas** (AD-11): centralizadas em `application.yml` (`filajusta.triagem.limites.*`)
- **Score:** subnota 0..1 por vital (distância normalizada à faixa) + peso gravidade  
- **Idempotência:** SELECT + INSERT com constraint UNIQUE(cpf) como rede de segurança contra corridas
- **Outbox:** gravado, não publicado ainda — relay/SNS fica para quando houver consumidor real (Epic 3+)

### Pendências / Riscos

- Integration tests com Testcontainers não implementados (spec pede 6 cenários I/O Matrix)
- Deploy ao vivo (rota no gateway + CDK) ainda não feito — adiado em `deferred-work.md`
- Teste de performance com volume alto (1000+ itens/min) não executado
- Logs sem campos estruturados — vale revisitar em contexto de stack real

---

## Prompt para Próxima Sessão

**Objetivo:** Implementar Story 2.2 (Consulta de Triagem com Score e Fatores Contribuintes)

**Contexto:** Epic 2 iniciou com Story 2.1 (registro + cálculo). Agora precisamos expor endpoint de consulta para que frontend/sistemas downstream recuperem triagens registradas com scores e fatores detalhados. Story 2.2 é leitura pura (sem efeitos colaterais).

**Comece por:**

1. Revisar spec de Story 2.2 em `_bmad-output/implementation-artifacts/spec-2-2-*.md`
2. Criar branch `feature/2-2-consulta-triagem-score`
3. Implementar query handler + endpoint `GET /v1/triagens/{id}` retornando `TriagemComScoreResponse`
4. Testes (unit + integração se dependências estiverem resolvidas)
5. Commit + PR para `develop`

**Branch base:** `develop`

---

## Observações Finais

- Epic 1 retrospective concluída em 2026-09-20 com 3 achados críticos em transações (FIX-1, FIX-2, FIX-3) — registrados em sprint-status.yaml como open
- Epic 2 iniciou com sucesso; próximo passo é consolidar leitura antes de avançar para Fila FIFO
- Documentação gerada e arquivos de handoff anteriores deletados conforme solicitado
