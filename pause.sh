#!/usr/bin/env bash
# Escala todas as tasks ECS do cluster FilaJusta a 0, sem destruir dados
# (volume EFS do Postgres persiste) -- NFR-7, spec 1.1. Nao usa CDK: chama a
# AWS CLI direto, para nao recriar/destruir a stack.
#
# Acao real na conta AWS -- confirmar com o usuario antes de rodar
# (Ask First da spec 1.1).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUTS_FILE="${ROOT_DIR}/infra-cdk/cdk-outputs.json"

if [[ ! -f "${OUTPUTS_FILE}" ]]; then
  echo "ERRO: ${OUTPUTS_FILE} nao encontrado. Rode ./deploy.sh primeiro (ele gera esse arquivo)." >&2
  exit 1
fi

CLUSTER_NAME=$(jq -r '.FilaJustaStack.ClusterName' "${OUTPUTS_FILE}")

echo "==> Listando services do cluster ${CLUSTER_NAME}..."
SERVICE_ARNS=$(aws ecs list-services --cluster "${CLUSTER_NAME}" --query 'serviceArns[]' --output text)

if [[ -z "${SERVICE_ARNS}" ]]; then
  echo "Nenhum service encontrado no cluster ${CLUSTER_NAME}."
  exit 0
fi

FAILED_SERVICES=()

for SERVICE_ARN in ${SERVICE_ARNS}; do
  echo "==> Escalando ${SERVICE_ARN} a 0..."
  # Nao usar set -e aqui: se um service falhar, os demais ainda precisam ser
  # escalados a 0 -- do contrario um erro no meio do loop deixaria service(s)
  # rodando e cobrando fora da janela de demo (NFR-7).
  if ! aws ecs update-service --cluster "${CLUSTER_NAME}" --service "${SERVICE_ARN}" --desired-count 0 >/dev/null; then
    echo "    FALHOU: ${SERVICE_ARN}" >&2
    FAILED_SERVICES+=("${SERVICE_ARN}")
  fi
done

echo ""
if [[ ${#FAILED_SERVICES[@]} -gt 0 ]]; then
  echo "ATENCAO: ${#FAILED_SERVICES[@]} service(s) NAO foram escalados a 0 (continuam cobrando):" >&2
  printf '  %s\n' "${FAILED_SERVICES[@]}" >&2
  echo "Rode este script novamente para tentar de novo." >&2
  exit 1
fi

echo "Todas as tasks foram escaladas a 0. Dados do Postgres preservados no volume EFS."
echo "Para retomar, rode ./deploy.sh novamente (reaplica os desired-count originais da stack)."
