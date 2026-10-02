#!/usr/bin/env python3
"""Carga de demonstracao para o docker-compose local (ConfirmaSUS).

Zera os dados de negocio (auth e flyway ficam) e recria, via API do gateway,
um conjunto variado e consistente: recursos com nome legivel, nenhum paciente
repetido no mesmo recurso, agendamentos em todos os status e sugestoes de
repasse pendentes nos recursos liberados.

Uso: python3 scripts/seed-demo.py   (stack ja no ar: docker compose up -d)
"""
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GATEWAY = "http://localhost:8080"

RECURSOS = [  # (codigoRecurso, rank, especialidade, unidade)
    ("Consulta Cardiologia", 1, "Cardiologia", "UBS Vila Nova"),
    ("Consulta Oftalmologia", 2, "Oftalmologia", "Hospital Central"),
    ("Ultrassonografia Abdominal", 2, "Radiologia", "Policlínica Norte"),
    ("Consulta Ortopedia", 1, "Ortopedia", "Hospital Central"),
    ("Consulta Dermatologia", 2, "Dermatologia", "UBS Jardim das Flores"),
    ("Ressonância Magnética", 3, "Radiologia", "Hospital Central"),
    ("Consulta Pediatria", 1, "Pediatria", "UBS Vila Nova"),
    ("Consulta Neurologia", 3, "Neurologia", "Policlínica Norte"),
    ("Eletrocardiograma", 1, "Cardiologia", "UBS Jardim das Flores"),
    ("Consulta Urologia", 2, "Urologia", "Policlínica Norte"),
    ("Consulta Endocrinologia", 1, "Endocrinologia", "UBS Vila Nova"),
    ("Raio-X de Tórax", 1, "Radiologia", "UBS Jardim das Flores"),
]

PACIENTES = [
    "Maria das Graças Silva", "João Batista Oliveira", "Ana Paula Souza",
    "Carlos Eduardo Lima", "Fernanda Costa Ribeiro", "José Ricardo Alves",
    "Luciana Martins", "Pedro Henrique Rocha", "Beatriz Nogueira",
    "Antônio Carlos Pereira", "Juliana Ferreira", "Marcos Vinícius Dias",
    "Patrícia Gomes", "Rafael Teixeira",
]

# (paciente, recurso, destino) -- destino: JANELA | ABERTO | CONFIRMADO | RECUSA | EXPIRA
AGENDAMENTOS = [
    (0, 0, "CONFIRMADO"), (1, 1, "CONFIRMADO"), (2, 2, "CONFIRMADO"),
    (3, 3, "RECUSA"), (4, 4, "RECUSA"), (5, 5, "RECUSA"), (6, 6, "RECUSA"),
    (7, 7, "EXPIRA"), (8, 8, "EXPIRA"), (12, 10, "RECUSA"),
    (9, 9, "ABERTO"), (10, 0, "ABERTO"), (11, 1, "ABERTO"),
    (13, 2, "JANELA"), (13, 11, "JANELA"),
]

# recurso liberado -> pacientes na fila de espera (ordem de chegada)
FILA = {3: [10, 11], 4: [0, 1], 5: [2, 3], 6: [7, 9], 7: [4, 10], 10: [8]}
# o recurso 8 fica sem fila -> sugestao ESGOTADA ("sem candidatos")

# Desfechos de repasse depois que as sugestoes existem:
REPASSAR = [4, 5]        # confirma a sugestao -> "Vaga repassada"
RECUSAR_UMA = [6]        # recusa 1x -> proximo da fila vira sugestao pendente
RECUSAR_TODOS = [3]      # recusa ate acabar a fila -> "Sem candidatos"


def cpf_valido(n: int) -> str:
    base = [int(c) for c in str(100000000 + (n * 7654321) % 899999999).zfill(9)]
    if len(set(base)) == 1:
        base[0] = (base[0] + 1) % 10
    for tam in (9, 10):
        soma = sum(d * (tam + 1 - i) for i, d in enumerate(base))
        base.append((soma * 10 % 11) % 10)
    return "".join(map(str, base))


def http(method, path, body=None, token=None, ok=(200, 201)):
    req = urllib.request.Request(
        GATEWAY + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json",
                 **({"Authorization": f"Bearer {token}"} if token else {})})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            data = r.read()
            return json.loads(data) if data else None
    except urllib.error.HTTPError as e:
        detalhe = e.read().decode()[:300]
        sys.exit(f"ERRO {method} {path} -> {e.code}: {detalhe}")


def sql(comando: str) -> str:
    return subprocess.run(
        ["docker", "compose", "exec", "-T", "postgres", "psql", "-U", "postgres",
         "-d", "confirmasus", "-t", "-A", "-c", comando],
        cwd=ROOT, check=True, capture_output=True, text=True).stdout.strip()


def aguardar(descricao, condicao, tentativas=40, intervalo=3):
    for _ in range(tentativas):
        if condicao():
            return
        time.sleep(intervalo)
    sys.exit(f"Timeout aguardando: {descricao}")


def main():
    print("1/7 zerando dados de negocio")
    for fila in ("auditoria-decisoes.fifo", "vaga-liberada-liberacao-repasse.fifo"):
        subprocess.run(
            ["docker", "compose", "exec", "-T", "localstack", "awslocal", "sqs", "purge-queue",
             "--queue-url", f"http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/{fila}"],
            cwd=ROOT, check=False, capture_output=True)
    sql("TRUNCATE agendamento_confirmacao.agendamentos, agendamento_confirmacao.eventos_outbox,"
        " agendamento_confirmacao.pacientes, matching_alocacao.recurso,"
        " matching_alocacao.lista_espera_entrada, matching_alocacao.sugestao_repasse,"
        " matching_alocacao.sugestao_recusada, matching_alocacao.alocacao,"
        " matching_alocacao.eventos_outbox, matching_alocacao.paciente,"
        " auditoria.decisao_auditoria RESTART IDENTITY CASCADE")

    token = http("POST", "/v1/auth/login",
                 {"username": "admin-tecnico", "password": "senha-tecnica-segura"})["token"]

    print("2/7 recursos")
    recurso_ids = []
    for codigo, rank, esp, unidade in RECURSOS:
        r = http("POST", "/v1/recursos", {"codigoRecurso": codigo, "especificidadeRank": rank,
                                          "disponivel": True, "especialidade": esp,
                                          "unidade": unidade}, token)
        recurso_ids.append(r["recursoId"])

    cpfs = [cpf_valido(i + 3) for i in range(len(PACIENTES))]
    agora = datetime.now(timezone.utc)

    print("3/7 agendamentos e fila de espera")
    ag_ids = []
    for i, (p, r, _) in enumerate(AGENDAMENTOS):
        quando = (agora + timedelta(days=3 + i, hours=8 + i % 6)).replace(minute=0, second=0, microsecond=0)
        http("POST", "/v1/agendamentos", {"cpf": cpfs[p], "recursoId": recurso_ids[r],
                                          "dataHoraAgendamento": quando.isoformat().replace("+00:00", "Z")}, token)
    for r, pacientes in FILA.items():
        for ordem, p in enumerate(pacientes):
            solicitado = (agora - timedelta(days=10 - ordem)).isoformat().replace("+00:00", "Z")
            http("POST", "/v1/lista-espera", {"cpf": cpfs[p], "recursoId": recurso_ids[r],
                                              "dataSolicitacao": solicitado}, token)
    for p, nome in enumerate(PACIENTES):
        sql(f"UPDATE agendamento_confirmacao.pacientes SET nome='{nome}' WHERE cpf='{cpfs[p]}'")
    ag_ids = [int(x) for x in sql("SELECT id FROM agendamento_confirmacao.agendamentos ORDER BY id").split()]

    print("4/7 abrindo janelas de confirmacao")
    abrir = [ag_ids[i] for i, (_, _, d) in enumerate(AGENDAMENTOS) if d != "JANELA"]
    lista = ",".join(map(str, abrir))
    sql(f"UPDATE agendamento_confirmacao.agendamentos SET janela_abre_em = now() - interval '2 hours'"
        f" WHERE id IN ({lista})")
    aguardar("janelas abertas", lambda: sql(
        f"SELECT count(*) FROM agendamento_confirmacao.agendamentos WHERE id IN ({lista})"
        f" AND status='AGUARDANDO_CONFIRMACAO'") == str(len(abrir)))

    print("5/7 confirmando, recusando e expirando")
    expirar = []
    for i, (_, _, destino) in enumerate(AGENDAMENTOS):
        ag = ag_ids[i]
        if destino == "CONFIRMADO":
            http("POST", f"/v1/agendamentos/{ag}/confirmacao", token=token)
        elif destino == "RECUSA":
            http("POST", f"/v1/agendamentos/{ag}/recusa", token=token)
        elif destino == "EXPIRA":
            expirar.append(ag)
    if expirar:
        sql(f"UPDATE agendamento_confirmacao.agendamentos SET janela_expira_em = now() - interval '1 minute'"
            f" WHERE id IN ({','.join(map(str, expirar))})")
    liberados = sum(1 for _, _, d in AGENDAMENTOS if d in ("RECUSA", "EXPIRA"))
    aguardar("vagas liberadas", lambda: sql(
        "SELECT count(*) FROM agendamento_confirmacao.agendamentos WHERE status='LIBERADO'") == str(liberados))

    print("6/7 aguardando sugestoes e auditoria")
    aguardar("sugestoes pendentes", lambda: sql(
        "SELECT count(*) FROM matching_alocacao.sugestao_repasse") == str(liberados), tentativas=40)
    print("7/7 desfechos de repasse")
    for r in REPASSAR:
        sug = http("GET", f"/v1/recursos/{recurso_ids[r]}/sugestao", token=token)
        http("POST", f"/v1/sugestoes-repasse/{sug['sugestaoId']}/confirmacao", token=token)
    for r in RECUSAR_UMA:
        sug = http("GET", f"/v1/recursos/{recurso_ids[r]}/sugestao", token=token)
        http("POST", f"/v1/sugestoes-repasse/{sug['sugestaoId']}/recusa",
             {"motivo": "Paciente informou indisponibilidade na data"}, token)
    for r in RECUSAR_TODOS:
        for _ in range(len(FILA[r])):
            sug = http("GET", f"/v1/recursos/{recurso_ids[r]}/sugestao", token=token)
            if not sug["sugestaoId"]:
                break
            http("POST", f"/v1/sugestoes-repasse/{sug['sugestaoId']}/recusa",
                 {"motivo": "Paciente já foi atendido em outra unidade"}, token)
    aguardar("eventos na auditoria", lambda: int(sql("SELECT count(*) FROM auditoria.decisao_auditoria")) >= 20,
             tentativas=40)
    print("OK -- http://localhost:3000 (admin-tecnico / senha-tecnica-segura)")


if __name__ == "__main__":
    main()
