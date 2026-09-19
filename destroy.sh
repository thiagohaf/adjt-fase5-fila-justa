#!/usr/bin/env bash
# Remove todos os recursos da stack FilaJusta, sem deixar recursos orfaos
# cobrando fora da janela de demo (NFR-7, spec 1.1).
#
# Acao real e DESTRUTIVA na conta AWS -- confirmar com o usuario antes de
# rodar (Ask First da spec 1.1).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="${ROOT_DIR}/infra-cdk"

echo "==> cdk destroy (remove VPC, ECS Fargate, Postgres/EFS, gateway-service, auth-service)..."
(cd "${INFRA_DIR}" && cdk destroy --force "$@")

rm -f "${INFRA_DIR}/cdk-outputs.json"

echo ""
echo "Stack destruida. Verifique manualmente se nao sobrou recurso orfao:"
echo "  aws cloudformation describe-stacks --stack-name FilaJustaStack"
echo "  aws ecs list-clusters"
