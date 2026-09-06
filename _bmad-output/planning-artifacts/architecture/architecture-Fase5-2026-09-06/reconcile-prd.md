# Reconciliation — ARCHITECTURE-SPINE.md vs. prd.md (Fase5-2026-09-05)

**Verdict:** Not clean — 5 gaps found (1 medium-high, 2 medium, 2 low). No FR is silently dropped and no Non-Goal is structurally violated, but two Non-Goal boundaries are left *implicit* where the spine's own artifacts (source tree, event model) could let a compliant-looking build cross them, and two PRD-mandated details already decided elsewhere (memlog) didn't make it into the spine text itself.

## Findings

### 1. [Medium-High] `LiberarRecurso` command not marked as internal-only — risks re-enabling the FR-13 Non-Goal

- **PRD says:** FR-13 Out of Scope: "Liberação manual antecipada de um Recurso pelo Regulador... a Liberação é sempre automática." `[NON-GOAL for MVP]`.
- **Spine says:** AD-6's Rule correctly describes the SQS-delay auto-release mechanism, but the Structural Seed source tree lists `LiberarRecurso` under `matching-alocacao-service/application/command/` with no annotation that it is triggered *only* by the internal SQS delay message, never by a REST/API call. A builder reading only the source tree (not cross-referencing AD-6 prose) could reasonably expose it as a `POST /recursos/{id}/liberar` endpoint for the Regulador — exactly the forbidden manual-early-release path.
- **Fix:** Add one clause to AD-6's Rule: "`LiberarRecurso` não é exposto via API pública — é acionado exclusivamente pelo consumo da própria mensagem SQS de delay que o serviço publicou na confirmação (FR-12); nenhum endpoint REST/gRPC aciona esse comando diretamente."

### 2. [Medium] AD-5 drops the second Resource tie-break rule from FR-5

- **PRD says:** "Entre Recursos igualmente elegíveis... em empate residual (mesmo tipo/especificidade), prefere o Recurso ocioso há mais tempo."
- **Spine says:** AD-5's Rule only states "menor rank suficiente" (best-fit by specificity) and the Patient-side price-time tie-break. The Resource-side residual tie-break (same `especificidadeRank` → prefer longest-idle) is missing entirely — two builders could each pick a different (and both AD-5-compliant) rule for same-rank Resources (e.g., one picks by insertion order, another randomly).
- **Fix:** Append to AD-5's Rule: "Entre Recursos de mesmo `especificidadeRank` elegíveis para o mesmo Paciente, prefere o ocioso há mais tempo (maior tempo desde que voltou ao pool disponível)."

### 3. [Medium] Ambiguous trigger for the "Sugestão de Matching gerada" audit event, given FR-6's "recalculada a cada consulta"

- **PRD says:** FR-8 requires a Log Auditável entry "toda vez que... uma Sugestão de Matching é gerada." FR-6 says the suggestion "é recalculada a cada consulta... e não reserva o Paciente."
- **Spine says:** AD-3 assumes `matching-alocacao-service` publishes discrete domain events per decision, and the event-flow diagram shows one `eventos-matching` topic feeding auditoria, but nothing in the spine resolves the tension: if every `GET /recursos/{id}/sugestao` recomputes and re-logs a `SugestaoGerada` event, a read-only query call has a write side-effect and can flood the audit log (violates the spirit of CQRS in AD-2 and could blow past a small hackathon dataset's log volume); if instead suggestions are never logged as they change, FR-8's literal requirement ("toda vez que... uma Sugestão é gerada") goes unmet.
- **Fix:** Add a sentence to AD-3 or AD-10 disambiguating this — e.g., "`SugestaoGerada` só é publicado quando o Recurso entra no pool disponível e recebe sua primeira sugestão, ou quando a sugestão *muda* de Paciente (novo Paciente elegível de maior prioridade); consultas repetidas (FR-6) que recomputam a mesma sugestão não geram novo evento." This is a real open call, not obviously implied by anything already in the spine — flag it to the user rather than silently picking one reading.

### 4. [Low] §7 NFR "health-check por serviço" not carried into the spine as a convention/rule

- **PRD says:** §7 Cross-Cutting NFRs: "Observabilidade mínima: logs estruturados e health-check por serviço."
- **Spine says:** Consistency Conventions mentions structured JSON logging, and AD-8 mentions "health-check é público" only as an aside about gateway auth bypass — there's no standing rule that *every* service (not just what the gateway happens to bypass) must expose one.
- **Fix:** Add a Consistency Conventions row (or extend AD-8) — e.g., "Health-check: todo serviço expõe `/actuator/health` (Spring Boot Actuator), roteado publicamente pelo gateway sem exigir token (AD-8)."

### 5. [Low] Concrete physiological-range values (already decided in the run's memlog) didn't make it into the spine's Deferred bullet

- **PRD says:** FR-1 flagged "faixas fisiológicas plausíveis concretas... ficam para definição em bmad-architecture" as an item this phase should close.
- **Spine says:** The Deferred section's bullet just says "faixas fisiológicas exatas" as if still open, but the run's memlog already recorded concrete `[ASSUMPTION]` values (FC 40–200bpm, PAS 60–260mmHg, PAD 30–150mmHg, SpO2 50–100%, FR 5–60irpm, Temp 30–42°C). Since this is single-service domain validation logic (not a cross-service invariant), it's fine that it isn't a full AD — but leaving it vague in Deferred loses a decision the run already made, forcing epics/stories to re-derive it from the memlog instead of reading it off the spine.
- **Fix:** Either inline the six concrete ranges into the Deferred bullet, or add them as a short table under Consistency Conventions (e.g., a "Validação de sinais vitais (FR-1)" row) so the spine is self-contained.

## Not a problem (checked, clean)

- All FR-1..FR-13 map to a service in the Capability → Architecture Map; none silently dropped.
- All §7 NFRs except health-check (finding 4) are covered (continuity via AD-3, versioned contracts via conventions, test coverage via Stack table, secrets via conventions).
- §8 LGPD/CPF handling matches AD-7 closely: CPF confined to ingestion, masked display via dedicated gRPC calls, no full-compliance claim (Deferred bullet 2 explicitly disclaims it) — consistent with the PRD's "honest scope" framing.
- No Non-Goal is structurally enabled except the implicit risk in finding 1.
- All 8 items in §11 "Assumptions Abertas" are addressed by an AD except FR-1 (finding 5, low) and FR-5's second tie-break (finding 2, medium).
