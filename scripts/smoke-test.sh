#!/usr/bin/env bash
# Smoke test da spec 1.1 (I/O & Edge-Case Matrix): valida GET
# /actuator/health publico via gateway-service e o bypass ao auth-service
# recusado pelo security group. Roda contra um ambiente ja no ar
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
  eni_id=$(aws ecs describe-tasks --cluster "${cluster}" --tasks "${task_arn}" \
    --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)
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

echo "==> [1/2] GET http://${GATEWAY_IP}:8080/actuator/health (esperado: 200)"
GATEWAY_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 5 "http://${GATEWAY_IP}:8080/actuator/health" || echo "000")
if [[ "${GATEWAY_STATUS}" == "200" ]]; then
  echo "    OK (${GATEWAY_STATUS})"
else
  echo "    FALHOU: recebido ${GATEWAY_STATUS}, esperado 200"
  FAILED=1
fi

echo "==> [2/2] Bypass direto ao auth-service em http://${AUTH_IP}:8081 (esperado: conexao recusada)"
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

if [[ "${FAILED}" -eq 0 ]]; then
  echo ""
  echo "Smoke test OK: health-check publico + bypass negado conforme spec 1.1."
else
  echo ""
  echo "Smoke test FALHOU -- ver mensagens acima."
  exit 1
fi
