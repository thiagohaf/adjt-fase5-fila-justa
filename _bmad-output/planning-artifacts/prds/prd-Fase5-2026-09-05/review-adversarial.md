---
title: Adversarial Review — PRD FilaJusta
reviewed: prd-Fase5-2026-09-05/prd.md
date: 2026-09-05
reviewer: bmad-prd Reviewer Gate (adversarial pass)
---

# Adversarial Review: PRD FilaJusta

## Overall Take

This PRD is unusually disciplined for a hackathon document — it tags several assumptions explicitly, ties FRs to UJs and SMs, and closes most open questions from prior rounds — but that discipline is uneven: several sections that *look* closed contain contradictions the author would not survive in front of a skeptical banca. The sharpest problems cluster around three things: (1) the document literally contradicts itself about whether any open question remains (§10 vs §11), (2) FR-5's own wording blurs the "suggest-then-confirm" model that FR-12 and the Glossário insist is the differentiator, and (3) the whole narrative assumes an ongoing lifecycle of Recursos becoming available/occupied/available-again, but no FR ever describes that lifecycle beyond the initial seed load — meaning the audit log, the system's entire raison d'être, cannot actually explain half of what happens to a Recurso over time. None of these are unfixable, but all are the kind of thing a reviewer finds in the first ten minutes.

## Findings

### [critical] "Nenhuma pendente" is contradicted by the very next section

**Location:** §10 Open Questions vs. §11 Assumptions Index (FR-7 bullet)

**Problem:** §10 states flatly: "Nenhuma pendente — todas as questões levantadas durante a elaboração deste PRD foram decididas." But §11's first bullet describes FR-7 as having an explicitly *unresolved* piece: "conversão... fica para `bmad-architecture`, quando a fórmula/escala definitiva do Score for fechada." That is a pending question by definition — the PRD itself says the numeric conversion (k, teto) is not decided yet. Two adjacent sections directly disagree about whether anything is still open. A banca reader who reads §10 and then §11 thirty seconds later will catch this immediately.

**Fix:** Reword §10 to something like: "Nenhuma pendência bloqueante para esta fase; ver §11 para decisões técnicas conscientemente deferidas para `bmad-architecture` (não bloqueiam o PRD, mas não estão fechadas)." Don't claim zero open items when the document's own index lists deferred ones.

---

### [critical] FR-5's body text contradicts the "suggest, don't auto-allocate" model

**Location:** §4.3 FR-5 ("O sistema sugere/aloca automaticamente...")

**Problem:** FR-5's heading is "Sugestão de Alocação" and the Glossário is explicit that an "Alocação" is a *distinct, later* thing from a Matching suggestion ("Distinta da sugestão em si, que ainda não consumiu o Recurso"). FR-12 further establishes that only a Regulador's explicit confirmation turns a suggestion into a definitive Alocação. But FR-5's own requirement text says the system "sugere/aloca automaticamente" — literally offering "or allocates" as an alternative reading, i.e., the FR's own words contradict the human-in-the-loop model that is supposed to be the whole point of FR-12 and the "no free manual override" Non-Goal. This is the single most quotable inconsistency in the doc — it directly undercuts the core designed safeguard (objective, human-confirmed allocation) that the author fought several rounds of decisions to establish.

**Fix:** Change FR-5's text to unambiguously say "sugere" only (never "aloca automaticamente"), and let FR-12 be the sole place where an Alocação is created. Do a find-and-replace check across the doc for every other place "aloca(r)/alocação" is used loosely as a synonym for "suggest."

---

### [critical] No FR describes the Recurso availability lifecycle beyond the initial seed

**Location:** §4.6 FR-10 (only covers initial seed load) vs. Vision (§1: "emparelhando cada paciente ao recurso disponível mais adequado **em tempo real**"), UJ-2 ("leito recém-liberado"), FR-5 ("um Recurso que fica disponível")

**Problem:** The narrative repeatedly assumes Recursos become available on an ongoing basis (a bed frees up, a specialist becomes free) and that this triggers new matching activity in real time. But the only FR that puts Recursos into the system is FR-10, which is scoped entirely to the one-time seed load at bootstrap. FR-12 removes a Recurso from the pool on confirmation, but no FR ever puts it *back* — there is no "patient discharged / treatment finished / resource released" event anywhere in the document. Concretely:
- If Recursos never become available again after being consumed, the system is a single-shot allocator over a fixed pool, not the continuously-operating queue the Vision and UJ-2 describe ("como uma bolsa de valores casa ordens... em tempo real").
- The audit log (FR-8/FR-9), the product's core differentiator, therefore has no mechanism to record or explain the *release* side of a Recurso's history at all — only its consumption.
- This is exactly the kind of "narrative implies it, no FR covers it" gap the reviewer brief called out by name.

**Fix:** Either (a) add an explicit FR for Recurso release/re-availability (even a minimal synthetic trigger for demo purposes, e.g. "após N simulated hours, um Recurso alocado retorna ao pool"), or (b) if genuinely out of scope for the MVP, add it to §5 Non-Goals explicitly ("liberação de Recursos após alocação — o MVP demonstra uma única rodada de consumo por Recurso") so the Vision/UJ language stops implying an ongoing lifecycle that doesn't exist.

---

### [high] FR-8's "no audit gaps" guarantee is unverifiable given the async architecture, and directly tensions with the resiliency NFR

**Location:** §4.5 FR-8 ("Nenhuma mudança de Score... ocorre sem registro correspondente... sem 'gaps'") vs. §4.2 FR-3 (Score calc is synchronous; Matching/Auditoria consumption is async via domain event) vs. §7 NFR "Alta disponibilidade" ("tolerar falha parcial de um serviço sem interromper os demais")

**Problem:** FR-3 explicitly decouples Score calculation (synchronous, returned to the caller immediately) from the audit/matching consumption of that event (asynchronous, "sem impacto na latência percebida"). That's a reasonable architecture choice, but it means the audit record for a given Score is written *after* the fact, by a separate service consuming an event. The resiliency NFR then explicitly says the system should tolerate partial failure of one service without interrupting the others — meaning if Auditoria is down or the event is dropped, Score calculation and Matching keep running per design, and the corresponding audit record simply never gets written. That is precisely the "gap" FR-8 promises can never happen. No FR specifies event delivery guarantees, retries, a dead-letter/reconciliation path, or what "no gaps" even means operationally under partial failure — so the consequence reads as testable but isn't, without that missing precision.

**Fix:** Either add an FR/NFR for event delivery guarantees (at-least-once + idempotent audit writes + a reconciliation/backfill mechanism), or soften FR-8's claim to something bounded and testable, e.g., "toda decisão gera um evento de auditoria com garantia de entrega at-least-once; a ausência de um registro correspondente é detectável via reconciliação" instead of an absolute "sem gaps."

---

### [high] CPF privacy posture (§8) conflicts with the audit log's actual purpose (FR-9)

**Location:** §8 Constraints ("CPF... não deve aparecer em texto claro em logs além do estritamente necessário para depuração") vs. §4.5 FR-9 (Auditor queries decisions "para um Paciente... específico") and §4.1 FR-2 (CPF is the sole patient identifier)

**Problem:** CPF is the only patient identifier the system has (per Glossário and FR-2 — there is no other patient ID scheme). FR-9's entire value proposition is that an Auditor can look up and display a patient's full decision history on demand — which necessarily means querying and rendering the CPF-keyed record, in a legitimate product feature, not "debugging." The §8 constraint's carve-out ("além do estritamente necessário para depuração") doesn't cover this normal-use case at all, so as written, the privacy posture and the core audit feature are in tension and neither FR-9 nor §8 acknowledges it.

**Fix:** Either scope the CPF-masking constraint explicitly to logs/observability (not to audit API responses), or specify that FR-9 responses use a masked/partial CPF (e.g., last 3 digits) with full CPF available only via a separate, more privileged lookup. Make the carve-out cover the actual read path, not just "debugging."

---

### [high] "Um Regulador pode...", "Um Auditor pode..." role language is unenforceable given FR-11

**Location:** Throughout §4 (FR-1, FR-6, FR-9, FR-12 all phrased as "Um <role> pode...") vs. §4.7 FR-11 ("Não há distinção de papéis... qualquer token válido acessa qualquer endpoint")

**Problem:** Every FR is written as if a specific role gates a specific action ("Um Regulador pode confirmar...", "Um Auditor pode consultar..."), which reads as an authorization rule. But FR-11 explicitly states there is no RBAC in this MVP — any valid token can call any endpoint. So nothing actually stops a "Profissional de Triagem" token from confirming/rejecting allocations (FR-12) or a random authenticated caller from pulling the full audit trail (FR-9). The FR phrasing overstates what the system enforces; a careful reviewer will ask "so who actually can do this, mechanically?" and the honest answer contradicts the FR wording.

**Fix:** Either rephrase FRs to describe *intended actors* without implying enforcement ("A API expõe um endpoint de confirmação de Alocação, tipicamente usado por um Regulador; nenhuma restrição de papel é aplicada nesta fase — ver FR-11"), or add a one-line caveat near FR-11 clarifying that the role names throughout §4 describe intended usage, not enforced authorization.

---

### [high] FR-5's "specificidade" tie-break sounds testable but has no defined ordering

**Location:** §4.3 FR-5, "Regras de desempate — Entre Recursos" ("prefere o Recurso mais específico ao caso")

**Problem:** The consequence "o sistema nunca aloca o Recurso específico se o genérico resolve o caso" reads as a crisp, testable rule, but there is no taxonomy or ordering defined anywhere for what makes one Recurso "more specific" than another (is a "leito de UTI" more specific than a "leito comum"? Is a cardiologist more specific than a general specialist for a given symptom set?). Without that ordering, no test oracle can be written — this is exactly the "Consequences that sound testable but aren't actually verifiable without more precision" trap the review brief warned about.

**Fix:** Either define a minimal specificity taxonomy in the PRD (even just "tipo de Recurso" as an enum with an explicit partial order, e.g., leito comum < leito UTI < leito UTI especializado), or explicitly defer the ordering to `bmad-architecture` with an `[ASSUMPTION]` tag the way FR-7 defers its numeric constants — right now it's silently assumed.

---

### [medium] "Alocação sugerida" contradicts the Glossário's own distinction

**Location:** §3 Glossário ("Alocação... Distinta da sugestão em si, que ainda não consumiu o Recurso") vs. §4.5 FR-8 ("uma Alocação é sugerida"), §6.1 ("Matching/sugestão de alocação"), §9 SM-2 ("decisões de alocação (sugestão, confirmação e recusa)")

**Problem:** The Glossário goes out of its way to distinguish a Matching *suggestion* from a confirmed *Alocação* — precisely to support the FR-12 confirm/reject model. But the rest of the document repeatedly uses "Alocação sugerida" / "decisão de alocação (sugestão...)" as if a suggestion were a kind of Alocação, which is exactly the conflation the Glossário was written to prevent. It's a minor wording issue individually, but it recurs enough times (FR-8, §6.1, SM-2) to look like the author doesn't consistently apply their own terminology — a bad look for a document whose stated differentiator is precision/explicability.

**Fix:** Reserve "Alocação" strictly for the confirmed/definitive state everywhere. Use "sugestão de Matching" or "sugestão de alocação candidata" consistently for the pre-confirmation state, and sweep all four locations for the fix.

---

### [medium] No FR addresses concurrent suggestions for the same top-priority Patient across multiple Recursos

**Location:** §4.3 FR-5 / FR-6 / FR-12

**Problem:** FR-5 says that when a Patient is eligible for more than one available Recurso simultaneously, the system picks between the Recursos by specificity/idleness — implying the system resolves this to a single pairing. But FR-6 lets a Regulador query the suggestion *per Recurso independently*, and FR-12 only removes the *Recurso* (not the Patient) from the pool upon confirmation. Nothing in the document says the system reserves/locks a Patient once suggested for one Recurso, so it's unclear whether querying two different Recursos in quick succession could return the *same* top-priority Patient as the suggestion for both — a double-booking that only surfaces when both Reguladores try to confirm. This is a silent gap in the concurrency/consistency story around the matching suggestion.

**Fix:** Add a consequence to FR-5 or FR-6 clarifying whether a Patient can appear as the suggested match for more than one Recurso at once, and if not, what happens to the second confirmation attempt (presumably a fresh FR-12 recompute/re-suggestion, but say so explicitly).

---

### [medium] Assumptions Index conflates real open assumptions with already-closed decisions

**Location:** §11 Assumptions Index (all six bullets)

**Problem:** Three bullets are genuinely open/unvalidated assumptions (FR-7's k/teto conversion, FR-11's no-RBAC posture, the CPF sensitive-data posture in §8) — appropriately so. But the other three (FR-10 seed composition, FR-12 confirm/reject model, §9 SM values) describe decisions that were already made and confirmed in earlier rounds, restated here as if they were still assumptions ("definida", "resolvendo a ambiguidade" — past tense, closed). Labeling closed decisions inside an "Assumptions Index" dilutes the section's purpose and is the direct cause of the §10/§11 contradiction flagged above (finding #1) — a reader can no longer tell, from this index alone, what's actually still open.

**Fix:** Split §11 into two lists: "Decisions Log" (closed, for traceability) and "Open Assumptions" (genuinely unvalidated, carried forward to `bmad-architecture`). Only the latter should make §10 say anything other than "none pending."

---

### [medium] FR-1's "faixa fisiológica plausível" is asserted as testable but no ranges are ever defined, and isn't flagged as deferred

**Location:** §4.1 FR-1 ("Campos de sinais vitais obrigatórios ausentes ou fora de faixa fisiológica plausível causam `400`")

**Problem:** This consequence is stated as a hard, testable rule, but the PRD never defines what the plausible physiological ranges actually are (e.g., heart rate, blood pressure bounds), nor does it tag this as deferred the way FR-7 explicitly and appropriately defers its numeric constants to `bmad-architecture` with an `[ASSUMPTION]` tag. The inconsistency is in rigor, not intent: FR-7 is honest about what's undefined; FR-1 quietly isn't.

**Fix:** Either specify a minimal set of plausible ranges here (even placeholder values), or add the same `[ASSUMPTION: faixas fisiológicas concretas definidas em bmad-architecture]` tag used elsewhere, and add it to §11.

---

### [medium] SM-2's "100% explicáveis" claim is scoped only to hand-picked demo cases

**Location:** §9 SM-2 ("100% das decisões... são explicáveis... em todos os casos simulados na demo")

**Problem:** A "100%" claim reads as a strong system guarantee, but the metric is explicitly scoped to "casos simulados na demo" — i.e., the specific scenarios the author chooses to seed and demonstrate. This is a soft, cherry-pickable claim dressed in absolute language; a skeptical reviewer will note that "100% of the cases I selected work" is a much weaker statement than it appears at first read, especially sitting next to SM-1/SM-3 which read as more general system properties.

**Fix:** Either state the metric honestly as scoped ("100% dos casos do dataset de demonstração, por desenho, incluindo os cenários de empate e escassez do FR-10"), or strengthen it to a property provable by the ≥90% JaCoCo + mutation + integration test suite (§7 NFR) rather than only "the demo."

---

### [low] UJ-2 doesn't cite FR-12 even though FR-12 claims to realize UJ-2

**Location:** §2.3 UJ-2 ("Realiza FR-5, FR-6") vs. §4.3 FR-12 ("Realiza UJ-2")

**Problem:** UJ-2's own path explicitly describes the confirm/audit step ("confirma (ou audita) a sugestão do sistema") which is FR-12's function, and FR-12 itself declares "Realiza UJ-2." But UJ-2's "Realiza" line only lists FR-5 and FR-6, omitting FR-12. One-directional cross-reference — minor, but the kind of thing that looks like the doc wasn't fully reconciled after FR-12 was added late (per the stated decision history).

**Fix:** Add FR-12 to UJ-2's "Realiza" line.

---

### [low] Vision's "replaces arrival order" claim is undercut by its own tie-break rule

**Location:** §1 Vision ("substituindo a ordem de chegada por um score de prioridade clínica") vs. §4.3 FR-5 tie-break ("vence quem tem a Triagem mais antiga")

**Problem:** The Vision's rhetorical hook is that FilaJusta replaces first-come-first-served with an objective clinical score. The tie-break rule for patients with equal effective priority is... arrival order (oldest Triagem wins). This is defensible as a secondary tie-break, not the primary ranking criterion, but it's an easy rhetorical gotcha for a banca member looking for a "gotcha" moment ("you said you got rid of FCFS, and then you used FCFS to break ties").

**Fix:** No functional change needed — just preempt it in the Vision or FR-5 text with a one-line acknowledgment: "em caso de empate de prioridade clínica, o critério objetivo remanescente é o tempo de espera — não uma regressão a FCFS, mas o mesmo princípio de justiça aplicado como desempate."

---

### [low] "Audita" used as a verb for the Regulador blurs the distinct Auditor role

**Location:** §2.3 UJ-2 ("confirma (ou audita) a sugestão do sistema")

**Problem:** The Glossário defines "Auditor" as a distinct actor from "Regulador." Using "audita" as a casual verb for the Regulador reviewing a suggestion before confirming risks blurring that role boundary for a reader skimming quickly, especially combined with finding #6 above (no RBAC actually separates these roles at the API level).

**Fix:** Replace "audita" with a neutral verb like "revisa" or "avalia" in UJ-2 to keep "auditar" exclusively tied to the Auditor role and FR-9.

## Summary

- Critical: 3
- High: 4
- Medium: 5
- Low: 3
- **Total: 15**
