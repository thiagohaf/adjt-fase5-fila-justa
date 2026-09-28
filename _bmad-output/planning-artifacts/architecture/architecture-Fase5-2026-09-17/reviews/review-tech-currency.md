# Review — Tech Currency (Stack, ARCHITECTURE-SPINE.md)

**Reviewer:** Tech Currency lens (Reviewer Gate, `finalize_reviewers`)
**Data da revisão:** 2026-09-17
**Escopo:** seção `## Stack` (linhas 139–156) de `ARCHITECTURE-SPINE.md`, marcada `[ASSUMPTION]` herdada de verificação feita em 2026-09-06.
**Método:** busca na web (WebSearch) + checagem direta via `gh api`/`gh issue view` contra GitHub Releases dos projetos (spring-boot, spring-cloud-release, spring-grpc, hcoles/pitest) para datas e números de versão exatos, evitando depender de memória de treino.

## Veredito

**A tabela Stack está majoritariamente atual, mas não está mais correta como está escrita** — dois itens ficaram desatualizados desde 2026-09-06 (Spring Cloud e, marginalmente, Spring gRPC) e um terceiro contém uma entrada morta que precisa ser removida (o starter standalone `org.springframework.grpc` 1.0.x). Nenhuma tecnologia da lista está descontinuada ou é substituição inadequada; a preocupação de compatibilidade Boot↔Cloud citada no prompt de revisão é infundada (2025.1.x "Oakwood" é de fato o trem compatível com Boot 4.1.x) mas a *patch* citada está um passo atrás. O alerta de memória sobre `pitest-maven < 1.30.0` quebrado em Java 25 é impreciso na causa mas correto na direção — recomenda-se fixar `>= 1.30.0` mesmo assim.

## Achados por item

### Java 25 (LTS) — `[MANTÉM]`
- Confirmado: Java 25 é a LTS atual. GA em 16/09/2025; próxima LTS é Java 29 (prevista para set/2027).
- Java 27 (não-LTS) atingiu GA em **15/09/2026** — dois dias antes desta revisão — mas não é LTS e não deve substituir Java 25 num projeto que busca estabilidade (hackathon acadêmico, sem motivo para correr atrás de feature release de 6 meses).
- Sem ação necessária. Apenas atenção: ferramentas de cobertura/mutação (JaCoCo, PIT) precisam suportar o bytecode class-file 69 (Java 25) — ver achados abaixo, já cobertos.

### Spring Boot 4.1.1 — `[MANTÉM]`
- Confirmado via GitHub Releases (`spring-projects/spring-boot`): `v4.1.1` publicado em 2026-08-20, é a última **release estável** da linha 4.1. `v4.2.0-M1` (milestone, não GA) também saiu em 2026-08-20 — não é candidato para adoção agora.
- Suporte da 4.1.x vai até jul/2027 conforme HeroDevs/endoflife tracking.
- Sem ação necessária.

### Spring Cloud 2025.1.2+ ("Oakwood") — `[DESATUALIZADO — atualizar para 2025.1.3]`
- A tabela cita "2025.1.2+", então tecnicamente a redação já se protege com o "+", mas a versão nominal registrada (2025.1.2, jun/2026) não é mais a última patch.
- Confirmado via GitHub Releases (`spring-cloud/spring-cloud-release`): `v2025.1.3` publicado em **2026-08-20** — mesma data do Spring Boot 4.1.1, sugerindo release coordenado. Essa é a versão a fixar.
- Compatibilidade Boot↔Cloud: **confirmada correta**. O trem "Oakwood" (2025.1.x) é a linha compatível com Spring Boot 4.1.x (e também 4.0.x). Não há incompatibilidade entre Spring Boot 4.1.1 e Spring Cloud 2025.1.x — a preocupação levantada no prompt de revisão não se concretiza.
- **Ação:** trocar "2025.1.2+" por "2025.1.3" (ou manter a notação "+" mas atualizar o número-base para refletir a versão real recomendada hoje).

### Spring gRPC 1.1.0 (suporte nativo Spring Boot 4.1) — `[QUASE ATUAL — patch disponível; entrada alternativa está morta]`
- Confirmado: Spring Boot 4.1 (GA 10/06/2026) trouxe suporte gRPC nativo via três módulos (`spring-boot-starter-grpc-server`, `spring-boot-starter-grpc-client`, `spring-boot-grpc-test`), apoiados em **Spring gRPC 1.1.0** e `grpc-java` 1.80.0. A associação Boot 4.1 ↔ Spring gRPC 1.1.0 citada na espinha está correta.
- Porém, via GitHub Releases (`spring-projects/spring-grpc`): existe um patch mais novo, **v1.1.1, publicado em 2026-08-21**. Não muda a recomendação estrutural, mas a versão exata citada (1.1.0) não é mais a última patch da própria série 1.1.x.
- **Achado mais importante deste item:** a alternativa citada na espinha — "starter standalone `org.springframework.grpc` 1.0.x" — está **obsoleta/morta para este projeto**. Confirmado via busca: os artefatos `org.springframework.grpc:spring-grpc-spring-boot-starter` na série 1.0.x continuam publicados no Maven Central, mas são mantidos **apenas para projetos presos ao Spring Boot 4.0**; para Boot 4.1+ (como é o caso de todos os serviços Spring Boot deste projeto), a starter correta e recomendada é a nativa do Boot (`spring-boot-starter-grpc-server`/`-client`), não mais a coordenada standalone `org.springframework.grpc`. Como a espinha já decidiu Spring Boot 4.1.1 para todos os serviços de domínio, a "alternativa 1.0.x" nunca vai ser exercida e é uma opção morta — deveria ser removida da tabela em vez de listada como alternativa viável, para não sugerir a um implementador que ele pode escolher entre as duas coordenadas livremente.
- **Ação:** atualizar para "Spring gRPC 1.1.1 (patch mais recente da série 1.1.x integrada ao Spring Boot 4.1)"; remover a menção a `org.springframework.grpc` 1.0.x como alternativa (é para consumidores presos em Boot 4.0, não se aplica aqui).

### PostgreSQL 18 — `[MANTÉM]`
- Confirmado: PostgreSQL 18 GA em set/2025; última patch em 2026-09-17 é a linha 18.x (18.6 lançada 2026-08-13). PostgreSQL 19 está em **beta** (Beta 3 em meados de ago/2026), GA prevista para set/out 2026 — ainda não lançado nesta data, então **não é uma opção madura hoje**.
- PostgreSQL 18 continua a escolha correta para começar um projeto agora. Sem ação necessária (a espinha não fixa patch específica, o que está OK).

### AWS SNS + SQS FIFO — `[MANTÉM]`
- Serviços gerenciados AWS sem versão de software para checar; padrão outbox + SNS FIFO fan-out para SQS FIFO por consumidor é um padrão estabelecido e correntemente recomendado pela AWS, sem sinal de descontinuação ou substituição. Sem ação necessária.

### AWS ECS Fargate — `[MANTÉM]`
- Confirmado: o padrão "subnet pública + `assignPublicIp=ENABLED` sem NAT Gateway" descrito em AD-11 é uma configuração válida e documentada pela AWS — tasks em subnet pública com IP público alcançam a internet via Internet Gateway diretamente, sem precisar de NAT Gateway (que só é obrigatório para tasks em subnet privada). Trade-off de custo vs. exposição já é reconhecido explicitamente no próprio AD-11. Sem ação necessária.

### AWS Lambda (job `seed-adapter`, runtime Quarkus) — `[MANTÉM]`
- Padrão Quarkus-on-Lambda para cold start otimizado continua uma prática corrente e recomendada para jobs one-shot em Lambda. Sem sinal de descontinuação. Sem ação necessária.

### Cucumber-JVM — `[MANTÉM]`
- Projeto ativo, com releases recorrentes (última em meados de ago/2026). Nenhum sinal de descontinuação ou de incompatibilidade com Java 25. A espinha não fixa número de versão — recomenda-se manter assim (pegar a última da série no momento do build) ou, se quiser fixar, usar a versão publicada mais recente no Maven Central em `io.cucumber:cucumber-java`/`cucumber-junit-platform-engine` no momento do primeiro build.

### JaCoCo — `[ATENÇÃO — fixar versão mínima explicitamente]`
- A espinha cita "JaCoCo + PIT" sem números de versão. **Achado importante:** suporte oficial ao class-file do Java 25 (major version 69) só chegou na **JaCoCo 0.8.14**; a 0.8.13 tinha suporte apenas experimental, e versões anteriores (0.8.11 e anteriores) não suportam Java 25 de forma alguma.
- **Ação:** a espinha deveria fixar explicitamente **JaCoCo ≥ 0.8.14** como piso de versão, não deixar implícito — do contrário há risco real de alguém puxar uma versão default mais antiga (ex.: a que vem em um plugin Maven/Gradle desatualizado) e ter builds de cobertura quebrando ou reportando errado em Java 25.

### JaCoCo + PIT (mutation testing) — checagem específica do alerta de memória — `[CONFIRMADO PARCIALMENTE — recomendação: fixar `pitest-maven >= 1.30.0`]`
- O alerta de uma sessão anterior ("pitest-maven < 1.30.0 é conhecido quebrado em Java 25") é **impreciso na causa exata**, mas a direção está certa e a ação recomendada (usar 1.30.0+) é a correta:
  - O bug específico de Java 25 documentado no changelog do projeto (`hcoles/pitest`) é o **#1485 "Fix BigDecimal and BigInteger mutators for java 25"**, corrigido na release **1.25.8** (2026-07-20) — ou seja, o fix real de Java 25 já estava disponível antes da 1.30.0.
  - A questão de compatibilidade geral com bytecode Java 25 foi respondida publicamente pelo mantenedor no issue #1439 (fechado em 2026-02-02): "Pitest support java bytecode versions up to and including Java 26" — ou seja, PIT já suportava o bytecode do Java 25 desde bem antes de 1.30.0 (a resposta é de fev/2026, quando a versão corrente era 1.22.1).
  - `1.30.0` (2026-08-27) é hoje a **última release** da série, mas seu changelog não menciona nada específico de Java 25 — é apenas um bump de versão (motivado por um mislabelling de uma release anterior como "1.29.10"), não uma correção de compatibilidade.
  - **Conclusão prática:** a memória anterior capturou o sintoma certo (build quebrando com pitest antigo em Java 25 — provável o bug dos mutators BigDecimal/BigInteger, presente antes da 1.25.8) mas atribuiu a correção à versão errada. O piso real e específico para o fix conhecido é **`pitest-maven >= 1.25.8`**; **recomenda-se fixar `>= 1.30.0` mesmo assim** por ser a última release estável disponível hoje (2026-08-27), sem razão para usar algo mais antigo.
- **Ação:** a espinha deveria fixar explicitamente **`pitest-maven` (e `pitest`) ≥ 1.30.0** na tabela Stack ou no companion de cobertura, não deixar "JaCoCo + PIT" sem número — mesmo problema de risco de version drift descrito acima para JaCoCo.

## Resumo de ações recomendadas para a tabela Stack

| Linha | De | Para |
| --- | --- | --- |
| Spring Cloud | `2025.1.2+ ("Oakwood"...)` | `2025.1.3 ("Oakwood", compatível com Spring Boot 4.1.x)` |
| gRPC | `Spring gRPC 1.1.0 integrado; starter standalone org.springframework.grpc 1.0.x como alternativa` | `Spring gRPC 1.1.1 (última patch da série 1.1.x, integrada ao Spring Boot 4.1 nativamente via spring-boot-starter-grpc-server/-client); coordenada standalone org.springframework.grpc não se aplica — 1.0.x é só para quem está preso ao Boot 4.0` |
| JaCoCo + PIT | (sem versão) | `JaCoCo ≥ 0.8.14 (suporte oficial a class-file Java 25/major 69) + PIT (pitest-maven) ≥ 1.30.0 (fix de mutators BigDecimal/BigInteger para Java 25 já presente desde 1.25.8; 1.30.0 é a última release estável em 2026-09-17)` |

Todos os demais itens (Java 25, Spring Boot 4.1.1, PostgreSQL 18, AWS SNS+SQS FIFO, AWS ECS Fargate, AWS Lambda, Cucumber-JVM) permanecem corretos como registrados e não precisam de alteração.

Nenhuma tecnologia da lista está descontinuada, substituída, ou apresenta incompatibilidade estrutural entre si — a base da decisão (Spring Boot 4.1.x + Spring Cloud 2025.1.x "Oakwood" + Spring gRPC 1.1.x + Java 25 LTS + PostgreSQL 18) é sólida e coerente para um build iniciado em 2026-09-17; os gaps encontrados são todos de **número de patch/versão desatualizado**, não de escolha arquitetural errada.
