# Epic 5 Context: Carga de Dados Sintéticos (Camada Adaptadora)

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Refinar e estabilizar a orquestração completa de carga de dados sintéticos via camada adaptadora (`seed-adapter`), populando o sistema com um dataset de demonstração testável e idempotente (catálogo de Recurso → Agendamentos → Lista de Espera). Esta epic prepara datasets ponta-a-ponta para validação das jornadas de usuário (UJ-1: Paciente confirma presença; UJ-2: Paciente não responde, vaga é repassada; UJ-3: Gestor decide o repasse), garantindo que todo o ciclo de confirmação/liberação/repasse possa ser demonstrado sem intervenção manual e que a execução seja reprodutível sob deploy único (`cdk deploy`).

## Stories

- Story 5.1: Autenticação do seed-adapter e Upsert do Catálogo de Recurso
- Story 5.2: Carga Idempotente de Agendamentos Sintéticos
- Story 5.3: Carga Idempotente da Lista de Espera

## Requirements & Constraints

- **Idempotência total**: todo o pipeline de carga (Stories 5.1/5.2/5.3) deve suportar reexecução sem gerar duplicatas — upsert por identificador de negócio (`codigoRecurso` para Recurso, CPF+`recursoId`+`dataHora` para Agendamento, `pacienteId`+`recursoId` para Lista de Espera).

- **Autenticação centralizada**: o seed-adapter obtém um JWT de um usuário técnico pré-cadastrado e usa esse token em *todas* as chamadas ao gateway — nunca faz requisição direta a serviço de domínio.

- **Falha explícita sob indisponibilidade**: se o `auth-service`, `gateway-service` ou qualquer serviço de domínio (Stories 1.1/2.1 dependências) estiver indisponível, a execução do seed-adapter aborta com erro claro, sem prosseguir para a etapa seguinte.

- **Ordem de dependência rigorosa**: Recurso antes de Agendamentos; Agendamentos antes de Lista de Espera. Nenhuma entrada posterior pode ser criada se a anterior falhar.

- **Resolução de CPF via gRPC**: ao carregar entradas de Lista de Espera (Story 5.3), o seed-adapter chama `ResolverOuCriarPaciente` (gRPC interno, Story 1.1 dependência) sem retry — timeout curto. CPF com formato/checksum inválido rejeita a entrada.

- **Estados de demonstração simulados**: o dataset deve incluir deliberadamente cenários pós-ação (Agendamentos confirmados, recusados, não confirmados, liberados com Lista de Espera não vazia, repassos já confirmados) para que cada UJ seja demonstrável — o seed-adapter encadeia chamadas de API já entregues pelas epics 1 e 2 para alcançar esses estados, nunca inventa transições.

- **Reprodutibilidade sob deploy único**: todo o sistema (5 serviços + seed-adapter + banco + mensageria) deve subir com um único comando CDK, dataset pronto para demonstração, sem passos manuais pós-deploy.

## Technical Decisions

- **Serviço Lambda/Quarkus**: o seed-adapter é um job de ingestão, não um serviço de runtime — executa no deploy, sem ciclo de vida próprio. Quarkus é escolhido (não Spring) para otimizar cold-start (AD-12).

- **Propriedade do dado sintético**: o dataset é definido como configuração/seed-data local (não em banco remoto) — composto por listas de Recurso, Agendamento e Lista de Espera, cada entrada com os IDs e timestamps necessários. Recurso é imutável; Agendamento pode ter estados simulados (confirmado, recusado, etc.); Lista de Espera é baseada em timestamps de chegada distintos.

- **Transação por etapa, não global**: cada Story (5.1/5.2/5.3) opera como uma unidade transacional separada — falha numa delas não precisa reverter a anterior, exceto que a ordem de execução previne inconsistência (Recurso sempre antes de Agendamento). Cada chamada de API é idempotente por design (upsert, escrita condicional do domínio).

- **Padrão de erro de CPF**: CPF com formato ou checksum inválido (a validação já existe em Story 1.1) causa rejeição da entrada de Lista de Espera com erro `422` — nenhum registro é criado. Isso é reportado ao chamador sem abortar o pipeline inteiro (a entrada inválida é pulada; as demais continuam).

- **Integração via gateway**: o seed-adapter entra pelo gateway (`POST /v1/...`) usando JWT, nunca faz gRPC direto aos serviços de domínio (exceto gRPC *interno* do `liberacao-repasse-service` → `agendamento-confirmacao-service`, que é transparente ao seed-adapter — Story 2.1 cuida disso).

- **Versionamento de contrato implícito**: o seed-adapter consome a mesma API (`/v1/`) que qualquer cliente — não precisa de endpoint dedicado. Mudanças na API downstream (Stories 1.1/2.1) refletem imediatamente no seed-adapter.

## Cross-Story Dependencies

- **Story 5.1 depende de**: `auth-service` (preexistente, obter JWT), `gateway-service` (preexistente, endereço base).

- **Story 5.2 depende de**: Story 5.1 (Recurso já carregado), Story 1.1 (endpoint `POST /v1/.../agendamentos`, criar Agendamento com Paciente resolvido por CPF).

- **Story 5.3 depende de**: Story 5.1 (Recurso já carregado), Story 1.1 (gRPC `ResolverOuCriarPaciente` disponível), Story 2.1 (endpoint `POST /v1/.../lista-espera`, criar entrada de Lista de Espera).

- **Bloqueantes da fase anterior**: Epics 1 e 2 devem estar 100% em produção (Stories 1.1-1.5, 2.1-2.5 completas) antes de Epic 5 poder ser testada de ponta a ponta. Epic 5 não pode ser integrada até que os endpoints de escrita das Stories 1.1 e 2.1 estejam operacionais e testados.

- **Ciclo de demonstração**: Epic 5 habilita a demonstração do ciclo UJ-1/UJ-2/UJ-3/UJ-4 descrito no PRD — sem Epic 5, o dataset de demonstração não existe, impossibilitando a validação das jornadas.
