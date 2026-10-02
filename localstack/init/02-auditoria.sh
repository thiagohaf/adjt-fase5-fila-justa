#!/bin/bash
# Bootstrap LocalStack (ready.d): fila FIFO do auditoria-service assinando os dois
# topicos de eventos (agendamento-confirmacao e matching-alocacao), com raw delivery
# (o consumidor le o envelope direto do corpo). Idempotente.
set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-us-east-1}"
ACCOUNT="000000000000"
QUEUE="auditoria-decisoes.fifo"
QUEUE_ARN="arn:aws:sqs:${REGION}:${ACCOUNT}:${QUEUE}"

awslocal sqs create-queue --queue-name "$QUEUE" \
  --attributes FifoQueue=true,VisibilityTimeout=60 >/dev/null

for TOPIC in agendamento-confirmacao-eventos.fifo matching-alocacao-eventos.fifo; do
  TOPIC_ARN=$(awslocal sns create-topic --name "$TOPIC" \
    --attributes FifoTopic=true,ContentBasedDeduplication=false \
    --query TopicArn --output text)
  awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs \
    --notification-endpoint "$QUEUE_ARN" \
    --attributes RawMessageDelivery=true >/dev/null
done

echo "[init] topicos de eventos -> ${QUEUE} (raw delivery) pronto"
