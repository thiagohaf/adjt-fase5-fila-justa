#!/usr/bin/env bash
# Sobe o ambiente completo do FilaJusta (VPC, cluster ECS Fargate, Postgres
# 18 containerizado, gateway-service e auth-service) com um unico comando,
# sem passo manual adicional (NFR-3, spec 1.1).
#
# Acao real na conta AWS -- confirmar com o usuario antes de rodar
# (Ask First da spec 1.1).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="${ROOT_DIR}/infra-cdk"

echo "==> [1/3] Compilando o reactor Maven (falha rapido antes do deploy)..."
mvn -q -f "${ROOT_DIR}/pom.xml" -pl gateway-service,auth-service,infra-cdk -am compile

echo "==> [2/3] cdk deploy (provisiona VPC, ECS Fargate, Postgres, gateway-service, auth-service)..."
(cd "${INFRA_DIR}" && cdk deploy --require-approval never --outputs-file cdk-outputs.json "$@")

echo "==> [3/3] Resolvendo o endpoint publico do gateway-service..."
OUTPUTS_FILE="${INFRA_DIR}/cdk-outputs.json"
CLUSTER_NAME=$(jq -r '.FilaJustaStack.ClusterName' "${OUTPUTS_FILE}")
GATEWAY_SERVICE_NAME=$(jq -r '.FilaJustaStack.GatewayServiceName' "${OUTPUTS_FILE}")

TASK_ARN=$(aws ecs list-tasks --cluster "${CLUSTER_NAME}" --service-name "${GATEWAY_SERVICE_NAME}" \
  --desired-status RUNNING --query 'taskArns[0]' --output text)

if [[ -z "${TASK_ARN}" || "${TASK_ARN}" == "None" ]]; then
  echo "AVISO: nenhuma task RUNNING encontrada ainda para ${GATEWAY_SERVICE_NAME}. Aguarde e rode:"
  echo "  aws ecs list-tasks --cluster ${CLUSTER_NAME} --service-name ${GATEWAY_SERVICE_NAME}"
  exit 0
fi

ENI_ID=$(aws ecs describe-tasks --cluster "${CLUSTER_NAME}" --tasks "${TASK_ARN}" \
  --query 'tasks[0].attachments[0].details[?name==`networkInterfaceId`].value' --output text)

if [[ -z "${ENI_ID}" || "${ENI_ID}" == "None" ]]; then
  echo "AVISO: task RUNNING mas a interface de rede ainda nao esta anexada. Aguarde e rode:"
  echo "  aws ecs describe-tasks --cluster ${CLUSTER_NAME} --tasks ${TASK_ARN}"
  exit 0
fi

PUBLIC_IP=$(aws ec2 describe-network-interfaces --network-interface-ids "${ENI_ID}" \
  --query 'NetworkInterfaces[0].Association.PublicIp' --output text)

if [[ -z "${PUBLIC_IP}" || "${PUBLIC_IP}" == "None" ]]; then
  echo "AVISO: IP publico ainda nao atribuido a ${ENI_ID}. Aguarde e rode:"
  echo "  aws ec2 describe-network-interfaces --network-interface-ids ${ENI_ID}"
  exit 0
fi

echo ""
echo "Ambiente no ar. Health-check publico do gateway-service:"
echo "  curl -sS -o /dev/null -w '%{http_code}\n' http://${PUBLIC_IP}:8080/actuator/health"
