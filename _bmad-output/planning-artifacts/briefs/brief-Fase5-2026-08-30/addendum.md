# Addendum — Motor de Priorização e Alocação Inteligente (SUS)

Conteúdo técnico e contextual que não pertence ao corpo do brief (que foca em capacidades e narrativa de produto), mas é insumo direto para `bmad-architecture` e para o PRD.

## Restrições e preferências técnicas (fornecidas pelo usuário, 2026-08-30)

- **Linguagem/runtime:** Java, com Spring Boot e/ou Quarkus (pode misturar conforme o serviço, como no precedente da Fase 4 do mesmo aluno, que usou Quarkus tanto na API quanto nas Lambdas).
- **Estilo arquitetural:** microsserviços, com Clean Architecture dentro de cada serviço (separação domínio / aplicação / infraestrutura, domínio sem dependência de framework).
- **Comunicação entre serviços:** Spring Cloud (service discovery, config, gateway conforme necessário); gRPC permitido/desejado onde fizer sentido (ex.: comunicação síncrona de baixa latência entre serviços internos).
- **Modelagem/design:** Event Storming como técnica de descoberta de domínio (a ser conduzido na fase de arquitetura); CQRS como padrão de leitura/escrita, especialmente relevante dado que o domínio já separa comandos (triagem, matching, alocação) de consultas (posição na fila, status, auditoria).
- **Disponibilidade:** alta disponibilidade é requisito não-funcional explícito — decisões de arquitetura devem considerar redundância e resiliência a falhas parciais, sem exigir complexidade desproporcional ao escopo do hackathon.
- **Serverless:** aberto a usar funções serverless (ex. AWS Lambda) pontualmente para integrações (ex.: adaptador de ingestão simulando SISREG/DATASUS, notificações), seguindo o precedente de sucesso da Fase 4 (Lambdas SRP para efeitos assíncronos).
- **Cloud:** AWS.
- **Restrição de custo (crítica):** o projeto é pago do próprio bolso do aluno (não é custeado pela FIAP nem por empresa) — as decisões de infraestrutura precisam privilegiar **baixo custo e fácil gerenciamento/desligamento** (ex.: Free Tier onde possível, recursos que possam ser pausados/destruídos fora das janelas de uso/demo, evitar serviços gerenciados caros como MSK, RDS multi-AZ permanente, NAT Gateway 24/7, etc., a menos que estritamente necessário). O precedente da Fase 4 já usou esse padrão (scripts de `pause`/`destroy` para cortar custo fora da demo) — deve ser replicado ou evoluído aqui.
- **Padrões de mercado:** seguir práticas correntes de mercado para microsserviços Java em produção (não deve ser tratado como um projeto de brinquedo, mesmo sendo acadêmico) — ex.: contratos versionados, observabilidade mínima, segurança de segredos fora do código, testes automatizados com cobertura mínima definida na arquitetura.

## Precedente relevante (Fase 4 do mesmo aluno)

O aluno já entregou um Tech Challenge anterior (Fase 4) usando metodologia BMAD (Brief → PRD → Architecture Spine), com monólito modular Quarkus e 2 Lambdas SRP na AWS, JWT, JaCoCo ≥90%, e ciclo de vida de custo via scripts `deploy/pause/destroy`. Ele quer manter a mesma disciplina de método (BMAD) nesta Fase 5, mas evoluindo a arquitetura para microsserviços "de verdade" com Spring Cloud, CQRS e Event Storming — ou seja, um salto de complexidade arquitetural deliberado em relação à fase anterior, não uma repetição do monólito modular.

## Notas para bmad-architecture

- Event Storming deve ser conduzido explicitamente como atividade de descoberta antes de fechar os limites dos microsserviços (bounded contexts prováveis: Triagem/Score, Matching/Alocação, Auditoria/Log de Fila, Ingestão/Adaptador de dados simulados SISREG-DATASUS).
- CQRS deve ser avaliado por serviço — nem todos precisarão de CQRS pleno (com store de leitura separado); pode ser CQRS lógico (métodos de comando vs. consulta separados) onde o overhead de um read-model dedicado não se justificar no prazo do hackathon.
- Reduzir custo é um critério central em toda decisão arquitetural — cada componente de infraestrutura proposto deve justificar seu custo e ter um caminho de "desligar quando não estiver em uso".
