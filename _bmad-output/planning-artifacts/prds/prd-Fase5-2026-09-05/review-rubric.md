# PRD Quality Review — FilaJusta

## Overall verdict

This PRD has a real thesis (objective priority + explainability-by-design, not the matching mechanism itself) that is carried consistently through Vision → UJs → FRs → SMs, and the core flow (FR-1–FR-9, FR-12) is unusually well specified for a hackathon PRD — concrete tie-break rules (FR-5), a numeric aging formula with explicit bounds (FR-7), and a sized synthetic seed (FR-10). The risks are mechanical rather than strategic: a few unbound adjectives survive in FR-1/FR-3 and two NFR bullets, the Glossary's own definitions of "Matching" and "Alocação" reference each other circularly and the doc uses three surface names for the same pre-confirmation state, and two Assumptions Index entries have no matching inline tag. None of this blocks `bmad-architecture`, but it's worth a tightening pass first.

## Decision-readiness — strong

Decisions are stated as decisions, not hedged as considerations, and several carry an explicit "what was given up." FR-12's Out of Scope note ("Seleção manual livre... isso quebraria o critério objetivo que é o diferencial central do produto", §4.3) names the trade-off rather than just picking a side. FR-11's Out of Scope note does the same for RBAC/OAuth. §10 Open Questions reports none pending — and this is earned, not rhetorical: the ambiguities that would normally sit there (FR-7's k/teto conversion, FR-11's role-distinction gap) were converted into tagged `[ASSUMPTION]`s with a live index (§11) rather than silently closed or left as unresolved rhetorical questions with the answer already in the next sentence.

### Findings
- **low** No `[NOTE FOR PM]` callouts anywhere in the document, despite real deferred tensions (FR-7 k/teto numeric conversion, §4.7 FR-11's no-RBAC posture) — the PRD routes these through `[ASSUMPTION]` tags instead. Acceptable given the solo-author context (PM = architect = dev = same person), but worth a conscious choice rather than a gap. *Fix:* none required; note in `bmad-architecture` handoff that these two ASSUMPTIONs are the ones needing an explicit "yes, still true" check before scoring numbers are fixed.

## Substance over theater — adequate

No persona theater (all four JTBDs in §2.1 drive at least one UJ and are cited by FR/SM IDs) and no innovation theater — the Vision (§1) explicitly disclaims novelty of the matching mechanism itself and locates the actual bet in objective-score-plus-explainability, which the doc then defends concretely rather than asserting. The stock-exchange analogy is reused with teeth (FR-5's price-time-priority tie-break), not just decorative language. One NFR bullet reads as boilerplate.

### Findings
- **medium** "Alta disponibilidade" NFR (§7) — "deve tolerar falha parcial de um serviço sem interromper os demais, com redundância e resiliência proporcionais ao escopo do hackathon" is the one NFR-theater smell in an otherwise lean list: no concrete bound (no RTO, no "what counts as tolerable," no service-count/dependency spec), and "alta disponibilidade" as a category is an odd fit for a system whose actual demo is a seeded video run, not a live production service. The self-qualifier ("proporcionais ao escopo do hackathon") shows awareness but doesn't convert to a testable bound. *Fix:* either drop the "Alta disponibilidade" framing in favor of "a demo do vídeo não deve falhar se um serviço reiniciar" (which §7's own "Reprodutibilidade" bullet already covers), or give it one concrete bound (e.g., "o fluxo de Triagem continua aceitando registros mesmo se o serviço de Matching estiver fora do ar, processando o Matching quando ele voltar").

## Strategic coherence — strong

The thesis is explicit and load-bearing: §1's "o diferencial não é o mecanismo de matching... mas a combinação de priorização objetiva com explicabilidade" is not decoration — FR-8/FR-9 (audit log), UJ-3/UJ-4 (auditor and patient journeys), and SM-2 (100% of decisions explainable) all point back at it. Feature order (§4.1→4.7) follows the user-journey arc (triage → score → match → aging → log → adapter → auth), not an easiest-first backlog. SM-3 (aging prevents starvation) is paired with a named counter-metric SM-C1 (aging must not invert real severity gaps) — exactly the counterbalance the rubric asks for, not just an activity metric.

### Findings
None — this dimension needs no correction.

## Done-ness clarity — adequate

Most FRs are unforgivingly specific: FR-5's tie-break rules, FR-7's numeric aging formula (`score + min(k × tempo, teto)`, teto ≤ 20% of Score range, reached in 12–24h), and FR-10's seed composition (~12–15 patients across ≥3 severity levels, ~6–8 resources across ≥2 types, at least one tied pair, at least one generic/specific overlap) are exactly the kind of testable consequence downstream story-writing needs. Two spots still lean on unbound adjectives.

### Findings
- **medium** FR-1 (§4.1) — "fora de faixa fisiológica plausível" gives no actual range (e.g., heart rate, blood pressure bounds), unlike FR-7's aging rule which nails its bounds precisely. A story-writer cannot derive a validation test from "plausível" alone. *Fix:* either state the ranges here or add an explicit `[ASSUMPTION: ranges deferred to bmad-architecture]` tag so the gap is visible rather than implied.
- **medium** FR-3 (§4.2) — "sem impacto na latência percebida por quem registrou a Triagem" is a "reasonable performance" phrase with no number attached (contrast with FR-7's explicit 12–24h). *Fix:* give a concrete bound (e.g., "resposta do POST de Triagem em <Xms mesmo com o evento `ScoreCalculado` publicado de forma assíncrona") or defer explicitly to architecture with a tag.
- **low** "Observabilidade mínima" (§7) — "logs estruturados e health-check... suficientes para diagnosticar falhas durante a demo" is adjective-bound ("suficientes"), not spec-bound (no mention of what a health-check endpoint must report, no log fields required). Lower stakes than the two above since it's explicitly framed as deferred detail, but still worth a bound if `bmad-architecture` is expected to inherit this as-is.

## Scope honesty — strong

§5 Non-Goals is substantive (7 explicit bullets, not filler) and reinforced by inline `[NON-GOAL for MVP]` tags at the two places where silent scope-narrowing would otherwise be tempting: FR-4's Notes (clinical validation of severity is out of scope) and FR-12's Out of Scope (no manual override). De-scoping is argued, not just declared — FR-12's note explains *why* the omission protects the product's core differentiator rather than listing it as an afterthought. Open-items density (0 Open Questions, 6 Assumptions, 0 NOTE FOR PM) is proportionate to the stated stakes (graded academic MVP, not a full green-light-to-build enterprise PRD).

### Findings
- **medium** Two of six Assumptions Index entries (§11: FR-10 seed composition, FR-12 confirm/refuse-as-explicit-action) have no matching inline `[ASSUMPTION]` tag at their source location — see Mechanical notes for detail. This means a reader hitting §4.6 or §4.3 cold would not know those are inferences rather than confirmed decisions.

## Downstream usability — adequate

Glossary (§3) is present and reasonably comprehensive (12 terms covering all actors and domain nouns), and most FRs cross-reference by ID rather than "see above" prose. Two gaps would cost a `bmad-architecture` or story-writing pass real time.

### Findings
- **medium** Glossary circularity + naming drift for the pre-confirmation state (§3, plus FR-5/FR-6/FR-12 headings). The Glossary defines "Matching" as "...expresso como uma sugestão de Alocação" and then defines "Alocação" as "...resultado da confirmação... de uma sugestão de Matching" — each definition explains itself in terms of "a suggestion of" the other term, which is parseable on a careful read but circular on a first one. The document then uses three different surface phrases for what should be one canonical state name: FR-5 is titled "Sugestão de Alocação", FR-6 says "a alocação sugerida", FR-12 is titled "Confirmação ou Recusa da Alocação Sugerida" — versus "Matching" itself, which the Glossary defines but which no FR heading actually uses. A domain model built from this PRD needs one name for the pre-confirmation entity/event; right now it has three. *Fix:* pick one canonical term (e.g., "Sugestão de Matching" throughout, reserving "Alocação"/"Alocação Sugerida" only for post-confirmation), and rewrite the two Glossary entries so each is defined independently rather than via the other's "suggestion of" phrasing.
- **medium** "Prioridade efetiva" (Score + Aging, defined operationally inside FR-7's formula) is used as a load-bearing term across FR-5, FR-6, FR-7, and implicitly FR-9/SM-1, but has no Glossary entry of its own — only "Urgência Acumulada" (the aging component) is indexed, not the composite. *Fix:* add a Glossary line: "Prioridade Efetiva — Score ajustado pela Urgência Acumulada; usado para ordenar a fila e decidir Matching."
- **low** FR-12 sits numerically after FR-11 but is placed in §4.3 (Matching), physically between FR-6 and §4.4 — the ID sequence in reading order is 1,2,3,4,5,6,12,7,8,9,10,11. Harmless for resolution (all IDs are unique and referenced correctly elsewhere) but likely to read as a typo on a fast pass. Relatedly, UJ-2's Resolution line ("Realiza FR-5, FR-6", §2.3) omits FR-12 even though UJ-2's Path text ("confirma... a sugestão do sistema") is exactly FR-12's function. *Fix:* renumber FR-12 to fit its section (e.g., promote it to FR-6b/FR-7 and shift the rest), or at minimum add FR-12 to UJ-2's "Realiza" line.

For a chain-top PRD (feeds `bmad-architecture` → epics/stories per §0), these two dents are worth fixing before the next phase reads this document as source of truth for entity/event naming.

## Shape fit — strong

The rigor level matches the stated stakes well: this is a backend-only, multi-role-but-single-organization tool (regulator / triage professional / auditor), and role-based UJ protagonists (§2.3) with persona+context lines are the right amount of formalism — not padded into consumer-style named personas, not skipped either. The Testes Automatizados NFR (§7: 90% JaCoCo on domain layer, PIT mutation testing, integration + e2e) is unusually rigorous for a solo hackathon PRD but correctly calibrated: it's specific, bounded, and directly serves the jury's "Documentação"/"Funcionalidade do MVP" criteria named in §0 and §9. The Privacy/Dados constraint (§8) is honestly scoped — explicitly distinguishing "boa prática demonstrável" from "requisito de compliance formal" via its own `[ASSUMPTION]` tag, rather than overclaiming LGPD compliance the project doesn't actually need to prove. The one formalism that slightly overshoots the stated stakes is the "Alta disponibilidade" NFR bullet (§7) — see the Substance-over-theater finding above; it reads as inherited enterprise boilerplate in an otherwise well-calibrated NFR list.

### Findings
None beyond the cross-referenced NFR-theater finding above.

## Mechanical notes

- **Assumptions Index roundtrip (partial gap):** All four inline `[ASSUMPTION]` tags (FR-7 §4.4, FR-11 §4.7, Constraints §8, Success Metrics §9) are correctly indexed in §11. However, two §11 entries — "§4.6 FR-10 — Composição-alvo do seed..." and "§4.3 FR-12 — Confirmação/recusa de Alocação definida como ação humana explícita..." — have no corresponding inline `[ASSUMPTION]` tag at FR-10 or FR-12 in the body text. The index is more complete than the inline markup; a reader relying on inline tags alone (rather than reading §11) would miss these two.
- **Glossary casing drift (minor):** UJ-1's Resolution line (§2.3, "O paciente entra na fila priorizada corretamente...") uses lowercase "paciente" — the only instance found that breaks the otherwise consistent capitalized-as-domain-term convention ("Paciente") used everywhere else, including the rest of §2.3's own UJs.
- **Glossary term-naming drift:** see the Downstream-usability finding above (Matching / Alocação Sugerida / sugestão de Alocação used as three surface forms for one state).
- **ID continuity:** FR-1–FR-12 all present, unique, no gaps or duplicates; only irregularity is FR-12's placement in reading order (see Downstream-usability finding). UJ-1–UJ-4 and SM-1–SM-3/SM-C1 are contiguous with no gaps.
- **UJ protagonist naming:** all four UJs (§2.3) carry a role-based protagonist with inline context (e.g., "Enfermeira de plantão em uma UPA sobrecarregada"); none are floating. Appropriate for this product's shape (see Shape fit).
- **Required sections:** all sections the rubric and the doc's own §0 contract expect are present (Vision, Target User, Glossário, Features/FRs, Non-Goals, MVP Scope, NFRs, Constraints, Success Metrics, Open Questions, Assumptions Index).
