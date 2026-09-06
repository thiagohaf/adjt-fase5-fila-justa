# Reconciliation: PRD vs. addendum.md

**Source input:** `_bmad-output/planning-artifacts/briefs/brief-Fase5-2026-08-30/addendum.md`
**Target:** `_bmad-output/planning-artifacts/prds/prd-Fase5-2026-09-05/prd.md`

**Nature of this check:** the addendum is architecture-facing input (stack, Spring Cloud, gRPC, Event Storming, CQRS, AWS cost constraints) that should *not* be duplicated in the PRD. The question is not completeness of transfer, but (1) discipline — did detail leak the wrong direction across the capability/implementation boundary — and (2) consistency — does any PRD decision contradict a hard constraint the addendum flags.

## Summary verdict

The PRD is disciplined almost everywhere: stack, cloud, Spring Cloud/gRPC, and Event Storming are correctly omitted, and the cost constraint is correctly imported into §8 Constraints in capability-appropriate language. There are, however, two real boundary violations, both in the same direction (PRD over-committing to an implementation choice the addendum reserved for architecture), plus one internal inconsistency in how deferred decisions are flagged.

## Finding 1 (material) — FR-3 forecloses the addendum's "CQRS lógico" allowance

**PRD text (§4.2, FR-3):**
> "A conclusão do cálculo publica um evento de domínio (ex.: `ScoreCalculado`) consumido de forma assíncrona pelos serviços de Matching (FR-5) e Auditoria (FR-8/FR-9), sem impacto na latência percebida por quem registrou a Triagem."

**Addendum text (Notas para bmad-architecture):**
> "CQRS deve ser avaliado por serviço — nem todos precisarão de CQRS pleno... pode ser CQRS lógico (métodos de comando vs. consulta separados) onde o overhead de um read-model dedicado não se justificar no prazo do hackathon."

The addendum explicitly leaves open, as an architecture-phase decision per bounded context, whether Score→Matching→Auditoria integration is a lightweight synchronous/logical-CQRS design or a full asynchronous event-driven one. FR-3 already decides this for the PRD: it names a concrete domain event (`ScoreCalculado`) and mandates asynchronous cross-service consumption. That is an implementation-level architectural commitment (event-driven integration, implicitly requiring some message-passing mechanism) sitting inside a Functional Requirement, not a capability statement.

This is also a premature bounded-context commitment: the addendum's Triagem/Score, Matching/Alocação, and Auditoria/Log groupings are explicitly flagged as "prováveis," pending Event Storming — yet FR-3 already treats Matching and Auditoria as separate downstream "serviços" reacting to an event, which presumes both the service split and the integration style before Event Storming has run.

**What the PRD needed instead:** a capability-level phrasing of the actual non-negotiable behavior — e.g., "the Triagem response includes the Score without requiring a second/polling call, and Matching/Auditoria observe the Score without added latency to the Triagem caller" — leaving *how* (sync call, in-process, or async domain event) to `bmad-architecture`, consistent with the CQRS-lógico allowance.

**Recommendation:** Reword FR-3's mechanism clause to a testable outcome only (no polling required; no latency impact), strike the specific event name and "de forma assíncrona pelos serviços," and add an `[ASSUMPTION]`/architecture-deferred note analogous to the one already used for FR-7's k/teto conversion.

## Finding 2 (moderate) — §7 NFR locks a coverage number + tool the addendum assigns to architecture

**PRD text (§7 Cross-Cutting NFRs):**
> "Testes automatizados: cobertura de linha ≥90% (JaCoCo) na camada de domínio de cada microsserviço... Complementada por: teste de mutação (ex.: PIT)..."

**Addendum text (Padrões de mercado):**
> "...testes automatizados com cobertura mínima definida na arquitetura."

The addendum's own market-practices bullet defers the *minimum coverage number* to the architecture phase (it only cites JaCoCo ≥90% + PIT as *Fase 4 precedent*, in the "Precedente relevante" section, not as a Fase 5 mandate). The PRD instead states the 90% figure and names the mutation-testing tool (PIT) as a firm NFR, with no `[ASSUMPTION]` tag — inconsistent with how the PRD handles every other addendum-adjacent deferred decision (FR-7's k/teto, FR-11's RBAC omission, and FR-10's dataset composition are all explicitly marked `[ASSUMPTION]` or logged in §11). This is implementation-level detail (specific tool, specific numeric threshold, applied "per microsserviço") that leaked into the PRD as if already decided, when the addendum's own text says it isn't yet.

**Recommendation:** Either (a) move the concrete number/tool to a note that architecture confirms or overrides (mirroring the FR-7 treatment), or (b) if the PM intends to hard-carry the Fase 4 precedent forward as a firm decision, add an explicit `[ASSUMPTION: carried forward from Fase 4 precedent, confirm in bmad-architecture]` tag and log it in §11 Assumptions Index for consistency.

## Finding 3 (minor) — scattered "serviço/microsserviço" language reinforces the same premature commitment

Beyond FR-3, §7 also says "medida por serviço" and "comunicação entre serviços usa contratos versionados." Individually these are reasonable (microservices is a settled addendum preference, not a leak by itself), but combined with Finding 1 they read as the PRD already assuming the final service boundaries rather than treating them as pending Event Storming output. No separate action needed beyond fixing Finding 1 — flagged here only so the pattern is visible as one issue, not three.

## Checked and found consistent (no gap)

- **Cost constraint**: correctly imported into §8 Constraints ("baixo custo e fácil desligamento," avoid costly always-on managed services, keep deploy/pause/destroy pattern) without over-specifying which AWS services — appropriate capability-level abstraction of a hard constraint.
- **High availability**: §7 NFR states the requirement and explicitly defers detailing (redundancy/resilience mechanics) to `bmad-architecture`, matching the addendum's own "sem exigir complexidade desproporcional" caveat.
- **Contratos versionados / segredos fora do código**: both correctly cited as inherited from the addendum, stated at capability level, no stack detail leaked.
- **Stack/cloud/Spring Cloud/gRPC/AWS/serverless**: correctly absent from the PRD entirely, as intended.
- **Bounded-context hints vs. PRD Features (§4)**: PRD's feature grouping (Triagem, Score, Matching, Aging, Log Auditável, Camada Adaptadora, Auth) tracks the addendum's "prováveis" bounded contexts reasonably, at capability level — this alignment is fine on its own; the issue is only that FR-3 treats it as already-decided infrastructure (see Finding 1), not that the grouping itself is wrong.
- **No cost-constraint contradiction found**: no FR mandates a specifically expensive managed service (no explicit RDS multi-AZ, MSK, or NAT-Gateway-24/7 requirement). Finding 1's mandatory async eventing is a soft risk (any event-passing mechanism has some cost/complexity footprint architecture must fit within the cost constraint) rather than a direct contradiction — noted as context for Finding 1, not a separate gap.

## Net gaps to act on before/at architecture handoff

1. Reword FR-3 to drop the named domain event and "assíncrono pelos serviços" language; state the latency/no-polling outcome only, and defer integration style to architecture (ties to CQRS-lógico allowance).
2. Tag the §7 test-coverage figure (90% JaCoCo + PIT) as an assumption/precedent-carried-forward, or explicitly confirm it's a firm Fase 5 requirement and note why, for consistency with how other deferred addendum-adjacent decisions are flagged elsewhere in the PRD.
