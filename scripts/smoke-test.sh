#!/usr/bin/env bash
# Smoke test das specs 1.1 e 1.2 (I/O & Edge-Case Matrix): valida GET
# /actuator/health publico via gateway-service, o bypass ao auth-service
# recusado pelo security group, e POST /v1/auth/login (credenciais
# corretas -> 200+JWT; erradas -> 401). Roda contra um ambiente ja no ar
# (./deploy.sh) -- nao provisiona nem destroi nada.
#
# Uso: ./scripts/smoke-test.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUTS_FILE="${ROOT_DIR}/infra-cdk/cdk-outputs.json"

if [[ ! -f "${OUTPUTS_FILE}" ]]; then
  echo "ERRO: ${OUTPUTS_FILE} nao encontrado. Rode ./deploy.sh primeiro." >&2
  exit 1
fi

CLUSTER_NAME=$(jq -r '.FilaJustaStack.ClusterName' "${OUTPUTS_FILE}")
GATEWAY_SERVICE_NAME=$(jq -r '.FilaJustaStack.GatewayServiceName' "${OUTPUTS_FILE}")
AUTH_SERVICE_NAME=$(jq -r '.FilaJustaStack.AuthServiceName' "${OUTPUTS_FILE}")

resolve_public_ip() {
  local cluster="$1" service="$2"
  local task_arn eni_id
  task_arn=$(aws ecs list-tasks --cluster "${cluster}" --service-name "${service}" \
    --desired-status RUNNING --query 'taskArns[0]' --output text)
  if [[ -z "${task_arn}" || "${task_arn}" == "None" ]]; then
    echo "ERRO: nenhuma task RUNNING para ${service}." >&2
    exit 1
  fi
  # Filtra por type==ElasticNetworkInterface em vez de assumir attachments[0]:
  # desde a Story 1.2, servicos com Service Connect (auth-service) ganham um
  # attachment "ServiceConnect" ANTES do ENI no array -- bug real encontrado
  # na verificacao ao vivo (attachments[0] apontava pro attachment errado).
  eni_id=$(aws ecs describe-tasks --cluster "${cluster}" --tasks "${task_arn}" \
    --query 'tasks[0].attachments[?type==`ElasticNetworkInterface`][].details[] | [?name==`networkInterfaceId`].value | [0]' --output text)
  if [[ -z "${eni_id}" || "${eni_id}" == "None" ]]; then
    echo "ERRO: task RUNNING para ${service} mas sem interface de rede anexada ainda." >&2
    exit 1
  fi
  aws ec2 describe-network-interfaces --network-interface-ids "${eni_id}" \
    --query 'NetworkInterfaces[0].Association.PublicIp' --output text
}

GATEWAY_IP=$(resolve_public_ip "${CLUSTER_NAME}" "${GATEWAY_SERVICE_NAME}")
AUTH_IP=$(resolve_public_ip "${CLUSTER_NAME}" "${AUTH_SERVICE_NAME}")

FAILED=0

echo "==> [1/4] GET http://${GATEWAY_IP}:8080/actuator/health (esperado: 200)"
GATEWAY_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 "http://${GATEWAY_IP}:8080/actuator/health" || echo "000")
if [[ "${GATEWAY_STATUS}" == "200" ]]; then
  echo "    OK (${GATEWAY_STATUS})"
else
  echo "    FALHOU: recebido ${GATEWAY_STATUS}, esperado 200"
  FAILED=1
fi

echo "==> [2/4] Bypass direto ao auth-service em http://${AUTH_IP}:8081 (esperado: conexao recusada)"
set +e
curl -sS -o /dev/null --max-time 5 "http://${AUTH_IP}:8081/actuator/health"
CURL_EXIT=$?
set -e
# curl 7 = "Failed to connect" (conexao recusada); 28 = timeout (SG dropando pacote silenciosamente
# tambem conta como bypass negado, a depender do modo de negacao do SG/ACL).
if [[ "${CURL_EXIT}" == "7" || "${CURL_EXIT}" == "28" ]]; then
  echo "    OK (curl exit ${CURL_EXIT} -- conexao recusada/bloqueada, como esperado)"
else
  echo "    FALHOU: curl exit ${CURL_EXIT} -- esperava conexao recusada (bypass do security group nao esta bloqueando)"
  FAILED=1
fi

echo "==> [3/4] POST http://${GATEWAY_IP}:8080/v1/auth/login com credenciais corretas (esperado: 200 + JWT)"
# Uma unica chamada (corpo + status juntos, separados por '\n') -- duas
# chamadas separadas para o mesmo request e desperdicio e arrisca uma
# divergencia entre as duas se o timeout for justo.
LOGIN_OK_RESPONSE=$(curl -sS --max-time 10 -w '\n%{http_code}' -X POST "http://${GATEWAY_IP}:8080/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"regulador","password":"regulador#2026"}' || echo -e '{}\n000')
LOGIN_OK_STATUS=$(echo "${LOGIN_OK_RESPONSE}" | tail -1)
LOGIN_OK_BODY=$(echo "${LOGIN_OK_RESPONSE}" | sed '$d')
TOKEN=$(echo "${LOGIN_OK_BODY}" | jq -r '.token // empty')
if [[ "${LOGIN_OK_STATUS}" == "200" && -n "${TOKEN}" ]]; then
  echo "    OK (${LOGIN_OK_STATUS}, JWT com $(echo "${TOKEN}" | tr -cd '.' | wc -c | tr -d ' ') pontos)"
else
  echo "    FALHOU: recebido ${LOGIN_OK_STATUS}, esperado 200 com campo 'token' no corpo"
  FAILED=1
fi

echo "==> [4/4] POST http://${GATEWAY_IP}:8080/v1/auth/login com senha errada (esperado: 401)"
LOGIN_BAD_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 -X POST "http://${GATEWAY_IP}:8080/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"regulador","password":"senha-errada"}' || echo "000")
if [[ "${LOGIN_BAD_STATUS}" == "401" ]]; then
  echo "    OK (${LOGIN_BAD_STATUS})"
else
  echo "    FALHOU: recebido ${LOGIN_BAD_STATUS}, esperado 401"
  FAILED=1
fi

if [[ "${FAILED}" -eq 0 ]]; then
  echo ""
  echo "Smoke test OK: health-check publico + bypass negado (spec 1.1) + login (spec 1.2)."
else
  echo ""
  echo "Smoke test FALHOU -- ver mensagens acima."
  exit 1
fi
