#!/bin/bash
# Bootstrap LocalStack (ready.d): topico SNS FIFO do agendamento-confirmacao-service
# + fila FIFO de VagaLiberada (e DLQ) do liberacao-repasse-service.
# Espelha infra-cdk (buildVagaLiberadaQueue): sem raw delivery, filtro de corpo
# eventType=VagaLiberada, maxReceiveCount=5, visibilityTimeout=60s. Idempotente.
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
ACCOUNT="000000000000"
TOPIC="agendamento-confirmacao-eventos.fifo"
QUEUE="vaga-liberada-liberacao-repasse.fifo"
DLQ="vaga-liberada-liberacao-repasse-dlq.fifo"

TOPIC_ARN=$(awslocal sns create-topic --name "$TOPIC" \
  --attributes FifoTopic=true,ContentBasedDeduplication=false \
  --query TopicArn --output text)

awslocal sqs create-queue --queue-name "$DLQ" \
  --attributes FifoQueue=true,VisibilityTimeout=60 >/dev/null

DLQ_ARN="arn:aws:sqs:${REGION}:${ACCOUNT}:${DLQ}"
QUEUE_ARN="arn:aws:sqs:${REGION}:${ACCOUNT}:${QUEUE}"

awslocal sqs create-queue --queue-name "$QUEUE" --attributes "{
  \"FifoQueue\":\"true\",
  \"VisibilityTimeout\":\"60\",
  \"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"
}" >/dev/null

awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs \
  --notification-endpoint "$QUEUE_ARN" \
  --attributes '{"FilterPolicyScope":"MessageBody","FilterPolicy":"{\"eventType\":[\"VagaLiberada\"]}"}' >/dev/null

echo "[init] ${TOPIC} -> ${QUEUE} (filtro VagaLiberada) pronto"
