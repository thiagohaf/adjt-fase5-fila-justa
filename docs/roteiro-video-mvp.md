# Roteiro — Vídeo do MVP Funcionando (máx. 8 min)

Objetivo do edital: demonstração prática, com exemplos reais de uso, destacando os pontos-chave e explicando o que não ficou pronto.

Ferramentas: **Swagger UI** (http://localhost:8088, lê `docs/api/openapi.yaml`), a **coleção Postman** (`docs/api/confirmasus.postman_collection.json`) e o **frontend** (http://localhost:3000) como visão do gestor.

## Preparação (fazer antes de gravar)

1. Subir a stack: `docker-compose up` (detalhes em `DOCKER_COMPOSE_README.md`).
2. Popular os dados de demonstração: `python3 scripts/seed-demo.py`. O script zera os dados de negócio e recria 12 recursos com nome legível, 15 agendamentos e cenários de repasse (2 repassadas, 1 recusa com próximo da fila, 1 fila esgotada, 1 sem fila, 2 pendentes).
3. Importar no Postman a coleção `docs/api/confirmasus.postman_collection.json` (o login guarda o token). Ela contém:
   - `POST http://localhost:8080/v1/auth/login`
   - `GET /v1/agendamentos` e `GET /v1/agendamentos/{id}`
   - `POST /v1/agendamentos/{id}/confirmacao`
   - `POST /v1/agendamentos/{id}/recusa`
   - `GET /v1/recursos/{id}/sugestao`
   - `POST /v1/sugestoes-repasse/{id}/confirmacao`
   - `POST /v1/sugestoes-repasse/{id}/recusa`
   - `GET /v1/auditoria/agendamento/{id}`
4. Login de demonstração: `admin-tecnico` / `senha-tecnica-segura` (no frontend, mesmo usuário).
5. Depois do `seed-demo.py` ficam prontos para a gravação: **3 agendamentos em `AGUARDANDO_CONFIRMACAO`** (para confirmar/recusar ao vivo), 2 em `AGUARDANDO_JANELA` e **2 recursos com sugestão `PENDENTE`** (para o gestor decidir). Confira os IDs no `GET /v1/agendamentos` antes de gravar. Se você rodou a coleção Postman antes (ela cria um recurso e um agendamento extras), rode o `seed-demo.py` de novo para limpar.
6. Fechar abas e notificações, aumentar a fonte, e ensaiar uma vez.

## Roteiro por minuto

### 0:00 a 0:45 — Contexto e arquitetura

**Tela:** diagrama do relatório (seção 5).

> "Este é o ConfirmaSUS rodando localmente: gateway, auth, agendamento-confirmação, liberação-repasse e auditoria, com Postgres e SNS/SQS emulados. Vou mostrar o ciclo completo: confirmar ou recusar uma consulta, a vaga liberada, o repasse pelo gestor e a auditoria."

### 0:45 a 1:30 — Autenticação (FR-14)

**Postman:** `POST /v1/auth/login` e mostrar o JWT. Em seguida chamar `GET /v1/agendamentos` **sem** token (401) e **com** token (200).

> "Todo acesso passa pelo gateway, que valida o JWT."

### 1:30 a 3:00 — Confirmar e recusar (FR-3 a FR-5)

**Postman:** `GET /v1/agendamentos`, apontando um item `AGUARDANDO_CONFIRMACAO`.
- `POST …/confirmacao` → status `CONFIRMADO`. Repetir a chamada para mostrar que é idempotente.
- Em outro agendamento, `POST …/recusa` → status `LIBERADO` com motivo `RECUSA`.

> "A confirmação é idempotente. A recusa libera a vaga imediatamente."

Mostrar também um agendamento `LIBERADO` por `NAO_CONFIRMADO` (expiração automática pelo poller), explicando que ninguém precisou agir.

### 3:00 a 5:00 — Sugestão e decisão humana (FR-8 a FR-11)

**Postman:** `GET /v1/recursos/{id}/sugestao` → mostra o próximo paciente da lista, com `situacao: PENDENTE`.
**Frontend:** abrir o Dashboard e a tela de Repasse do recurso.

> "A vaga liberada gerou uma sugestão automática: o primeiro da lista de espera, por ordem de chegada. O sistema só sugere."

- `POST /v1/sugestoes-repasse/{id}/recusa` com motivo → mostrar que a próxima sugestão já aponta para o segundo da fila.
- `POST /v1/sugestoes-repasse/{id}/confirmacao` → repasse confirmado; no Dashboard aparece **"Vaga repassada"**.
- Repetir a confirmação para mostrar o `409` (conflito) em sugestão já resolvida.

### 5:00 a 6:30 — Auditoria (FR-12, FR-13)

**Postman ou frontend (tela Auditoria):** `GET /v1/auditoria/agendamento/{id}` do agendamento que percorreu o ciclo inteiro.

> "Aqui está a resposta à pergunta 'por que esta vaga foi para este paciente': notificação, recusa, liberação, sugestão, decisão do gestor, cada uma com horário e motivo, em ordem cronológica. A auditoria é alimentada por eventos assíncronos, então não bloqueia o fluxo principal."

### 6:30 a 7:30 — Pontos-chave técnicos (rápido)

**Tela:** código ou relatório, um destaque por vez.
- Escrita condicional (`UPDATE … WHERE status = …`) evita corrida entre confirmação e expiração.
- Outbox + SNS/SQS FIFO com deduplicação por `eventId`.
- Eventos e auditoria carregam só `pacienteId`, nunca o CPF.

### 7:30 a 8:00 — O que não ficou pronto (o edital pede honestidade)

> "Fora do MVP: notificação real por WhatsApp ou SMS (hoje é simulada), integração com SISREG e DATASUS (hoje há dados sintéticos), perfis de acesso por papel, e o deploy AWS completo, que hoje cobre gateway, auth e agendamento; liberação-repasse e auditoria rodam via docker-compose. O caminho para cada um está no relatório."

## Fallbacks se algo falhar na gravação

- **Serviço fora do ar:** `curl http://localhost:8080/actuator/health` e `docker compose ps`; rode `seed-demo.py` de novo.
- **Sugestão não aparece:** aguardar alguns segundos (o consumo é assíncrono via SQS) e repetir o `GET`.
- **Frontend com dado estranho:** repetir o seed-demo e recarregar.

## Checklist antes de gravar

- [ ] Stack no ar e seed-demo executado; IDs dos itens de demonstração anotados.
- [ ] Coleção Postman com token válido (o JWT expira).
- [ ] Nada sensível na tela (variáveis de ambiente, credenciais AWS).
- [ ] Cronometrar: máximo de 8:00.
- [ ] Validar visualmente Dashboard, Auditoria, Detalhes e Repasse no navegador antes de gravar (essas telas foram validadas só pela API até agora).
