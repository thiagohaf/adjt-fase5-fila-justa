package com.confirmasus.infra;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de sintese da stack CDK -- cobrem os invariantes de infraestrutura
 * da spec 1.1 (VPC sem NAT, security groups de bypass, ECS Fargate publico).
 * Nao tocam a conta AWS real (`cdk synth`/testes rodam so contra o
 * CloudFormation template gerado localmente).
 */
class ConfirmaSusStackTest {

    private static Template template;

    @BeforeAll
    static void synthesize() {
        App app = new App();
        ConfirmaSusStack stack = new ConfirmaSusStack(app, "TestStack", null);
        template = Template.fromStack(stack);
    }

    @Test
    void vpcHasNoNatGateway() {
        template.resourceCountIs("AWS::EC2::NatGateway", 0);
        template.resourceCountIs("AWS::EC2::VPC", 1);
    }

    @Test
    void ecsClusterIsProvisioned() {
        template.resourceCountIs("AWS::ECS::Cluster", 1);
    }

    @Test
    void fourFargateServicesAreProvisioned() {
        // postgres, gateway-service, auth-service, agendamento-confirmacao-service
        // (Story 1.1 do Epic 1) -- os demais servicos de dominio ficam deferidos.
        template.resourceCountIs("AWS::ECS::Service", 4);
    }

    @Test
    void allFargateServicesAssignPublicIp() {
        template.allResourcesProperties("AWS::ECS::Service", Map.of(
                "NetworkConfiguration", Match.objectLike(Map.of(
                        "AwsvpcConfiguration", Match.objectLike(Map.of(
                                "AssignPublicIp", "ENABLED"))))));
    }

    @Test
    void authServiceAppPortIsOnlyReachableFromGatewaySecurityGroup() {
        // A regra de ingress da porta 8081 (auth-service) deve ter como
        // origem especificamente o security group do gateway-service, nao
        // qualquer origem (Match.anyValue() so garantia que UMA origem
        // existia, nao QUAL -- passaria mesmo se a regra fosse um CIDR
        // aberto acrescentado por engano).
        template.hasResourceProperties("AWS::EC2::SecurityGroupIngress", Map.of(
                "FromPort", 8081,
                "ToPort", 8081,
                "IpProtocol", "tcp",
                "SourceSecurityGroupId", Match.objectLike(Map.of(
                        "Fn::GetAtt", Match.arrayWith(java.util.List.of(
                                Match.stringLikeRegexp("^GatewayAppSg.*"),
                                "GroupId"))))));
    }

    @Test
    void authServiceHealthPortIsPubliclyReachable() {
        // Excecao estreita de health-check (AD-8/AD-12): a porta 8090
        // (management) do auth-service e publica -- sem isso a AC de
        // health-check do epic nao seria satisfeita para o auth-service.
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Map.of(
                "SecurityGroupIngress", Match.arrayWith(java.util.List.of(Match.objectLike(Map.of(
                        "CidrIp", "0.0.0.0/0",
                        "FromPort", 8090,
                        "ToPort", 8090)))))));
    }

    @Test
    void allTaskDefinitionsUseArm64RuntimePlatform() {
        // Regressao evitada: imagens construidas nativamente em ARM64 (Apple
        // Silicon) rodando em task definitions X86_64 (default da Fargate)
        // morrem com "exec format error" -- bug real encontrado e corrigido
        // no deploy ao vivo desta story.
        template.allResourcesProperties("AWS::ECS::TaskDefinition", Map.of(
                "RuntimePlatform", Map.of(
                        "CpuArchitecture", "ARM64",
                        "OperatingSystemFamily", "LINUX")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void postgresServiceDependsOnNamespaceAndEfsMountTargets() {
        // Duas corridas de infra encontradas e corrigidas no deploy ao vivo
        // desta story: sem DependsOn explicito, o ECS::Service do Postgres
        // podia ser criado antes do namespace Cloud Map do cluster estar
        // pronto ("Failed to retrieve namespace") ou antes dos mount targets
        // EFS propagarem (timeout de mount NFS).
        Map<String, Object> json = template.toJSON();
        Map<String, Object> resources = (Map<String, Object>) json.get("Resources");
        Map<String, Object> postgresService = resources.entrySet().stream()
                .filter(e -> e.getKey().startsWith("PostgresService"))
                .map(e -> (Map<String, Object>) e.getValue())
                .findFirst()
                .orElseThrow(() -> new AssertionError("PostgresService resource nao encontrado no template"));

        java.util.List<String> dependsOn = (java.util.List<String>) postgresService.get("DependsOn");
        assertThat(dependsOn).isNotNull();
        assertThat(dependsOn).anyMatch(id -> id.startsWith("ConfirmaSusClusterDefaultServiceDiscoveryNamespace"));
        assertThat(dependsOn).anyMatch(id -> id.startsWith("PostgresDataFsEfsMountTarget"));
    }

    @Test
    void gatewayAppPortIsPubliclyReachable() {
        template.hasResourceProperties("AWS::EC2::SecurityGroup", Match.objectLike(Map.of(
                "SecurityGroupIngress", Match.arrayWith(java.util.List.of(Match.objectLike(Map.of(
                        "CidrIp", "0.0.0.0/0",
                        "FromPort", 8080,
                        "ToPort", 8080)))))));
    }

    @Test
    void postgresRunsAsContainerNotRds() {
        template.resourceCountIs("AWS::RDS::DBInstance", 0);
        template.resourceCountIs("AWS::RDS::DBCluster", 0);
        template.resourceCountIs("AWS::EFS::FileSystem", 1);
    }

    @Test
    void dbSecretIsNotAPlainParameter() {
        // PostgresSecret (Story 1.1) + JwtSecret (Story 1.2, AD-14).
        template.resourceCountIs("AWS::SecretsManager::Secret", 2);
    }

    @Test
    void authServiceHasServiceConnectDnsName() {
        // Gateway alcanca o login via DNS interno auth-service:8081 (sem
        // sufixo de namespace -- o Envoy resolve pela string exata do
        // dnsName) (application.yml da rota publica) -- sem isso o AC de
        // login nao e demonstravel fora de um curl direto ao IP publico da
        // task. O CDK
        // aninha DnsName/Port dentro de ClientAliases, nao direto em Services[].
        Map<String, Object> clientAlias = Map.of("DnsName", "auth-service", "Port", 8081);
        Map<String, Object> serviceEntry = Map.of("ClientAliases", Match.arrayWith(java.util.List.of(
                Match.objectLike(clientAlias))));
        Map<String, Object> serviceConnectConfig = Map.of(
                "Services", Match.arrayWith(java.util.List.of(Match.objectLike(serviceEntry))));
        template.hasResourceProperties("AWS::ECS::Service", Match.objectLike(Map.of(
                "ServiceConnectConfiguration", Match.objectLike(serviceConnectConfig))));
    }

    @Test
    void gatewayServiceIsServiceConnectClient() {
        // gateway-service precisa do sidecar Envoy do Service Connect pra
        // resolver "auth-service"/"postgres" -- so cliente, nao publica
        // nada (sem "Services" na config). Bug real encontrado na
        // verificacao ao vivo da Story 1.2: sem isso, POST /v1/auth/login
        // retornava 500 (UnknownHostException: Failed to resolve
        // 'auth-service', NXDOMAIN) mesmo com o hostname certo.
        Map<String, Map<String, Object>> serviceConnectServices = template.findResources("AWS::ECS::Service",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "ServiceConnectConfiguration", Match.objectLike(
                                Map.of("Enabled", true, "Namespace", "confirmasus.local")))))));
        long clientOnlyCount = serviceConnectServices.values().stream()
                .filter(resource -> {
                    Object properties = resource.get("Properties");
                    Object serviceConnectConfig = ((Map<?, ?>) properties).get("ServiceConnectConfiguration");
                    return !((Map<?, ?>) serviceConnectConfig).containsKey("Services");
                })
                .count();
        assertThat(clientOnlyCount).isEqualTo(1);
    }

    @Test
    void authServiceReceivesJwtSecretAndDbCredentialsAsEcsSecrets() {
        // Nem o segredo JWT nem a senha do Postgres vao no repositorio (NFR-6) --
        // ambos chegam ao container so via ECS Secret (Secrets Manager), nunca
        // como "Environment" em texto puro. Cada nome e verificado
        // isoladamente (Match.arrayWith com 1 padrao) -- a ordem dos 3
        // secrets no array sintetizado nao e um invariante desta spec.
        assertAuthContainerHasSecret("CONFIRMASUS_JWT_SECRET");
        assertAuthContainerHasSecret("SPRING_DATASOURCE_USERNAME");
        assertAuthContainerHasSecret("SPRING_DATASOURCE_PASSWORD");
    }

    private static void assertAuthContainerHasSecret(final String secretEnvName) {
        assertContainerHasSecret("auth-service", secretEnvName);
    }

    @Test
    void gatewayServiceReceivesJwtSecretAsEcsSecret() {
        // Spec de validacao de JWT no gateway (AD-8/AD-14): o
        // JwtAuthenticationFilter precisa do mesmo segredo HS256 do
        // auth-service, injetado so via ECS Secret (Secrets Manager),
        // nunca como "Environment" em texto puro (NFR-6).
        assertContainerHasSecret("gateway-service", "CONFIRMASUS_JWT_SECRET");
    }

    private static void assertContainerHasSecret(final String containerName, final String secretEnvName) {
        Object secretsMatch = Match.arrayWith(java.util.List.of(
                Match.objectLike(Map.of("Name", secretEnvName))));
        Map<String, Object> container = Map.of(
                "Name", containerName,
                "Secrets", secretsMatch);
        Object containerDefinitions = Match.arrayWith(java.util.List.of(Match.objectLike(container)));
        template.hasResourceProperties("AWS::ECS::TaskDefinition", Match.objectLike(Map.of(
                "ContainerDefinitions", containerDefinitions)));
    }

    @Test
    void templateIsNotNull() {
        assertThat(template).isNotNull();
    }

    @Test
    void noTaskRoleWithTriagemScoreServiceDescriptionExistsAnymore() {
        // triagem-score-service (produto anterior, Score de Prioridade
        // Clinica) foi decomissionado por restricao legal, sem substituto:
        // nenhuma role com essa descricao deve existir na stack.
        Map<String, Map<String, Object>> roles = template.findResources("AWS::IAM::Role",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "Description", Match.stringLikeRegexp(".*triagem-score-service.*"))))));
        assertThat(roles).isEmpty();
    }

    @Test
    void noScoreCalculadoTopicOrQueueExistsAnymore() {
        // O topico/fila do evento ScoreCalculado (consumido pelo extinto
        // triagem-score-service para priorizar por score de gravidade) foi
        // removido junto com a priorizacao clinica -- nao deve haver
        // resquicio na stack.
        Map<String, Map<String, Object>> topicos = template.findResources("AWS::SNS::Topic",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "TopicName", "score-calculado.fifo")))));
        assertThat(topicos).isEmpty();

        Map<String, Map<String, Object>> filas = template.findResources("AWS::SQS::Queue",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "QueueName", Match.stringLikeRegexp("score-calculado.*"))))));
        assertThat(filas).isEmpty();
    }

    @Test
    void liberacaoRepasseServiceStillHasNoFargateServiceDeployed() {
        // Boundaries da spec 3.1b -- "Never: deploy ECS/CDK do servico": a
        // stack ganha a fila consumidora + DLQ + a role de consumo, mas nao
        // um ECS::Service proprio. Regressao evitada: continua so postgres +
        // gateway-service + auth-service + agendamento-confirmacao-service
        // (Story 1.1 do Epic 1, o unico servico de dominio ja deployado).
        template.resourceCountIs("AWS::ECS::Service", 4);
    }

    @Test
    void matchingAlocacaoEventosTopicIsFifo() {
        // Story 3-3a (emenda, AD-3): topico SNS FIFO proprio do
        // liberacao-repasse-service que RelaySnsPublisherJob publica --
        // ordem deterministica por recurso via MessageGroupId=recursoId,
        // mesma propriedade do topico irmao AgendamentoConfirmacaoEventosTopic
        // (ContentBasedDeduplication=false: MessageDeduplicationId sempre
        // explicito, o eventId do outbox).
        template.hasResourceProperties("AWS::SNS::Topic", Match.objectLike(Map.of(
                "TopicName", "matching-alocacao-eventos.fifo",
                "FifoTopic", true,
                "ContentBasedDeduplication", false)));
    }

    @Test
    void liberacaoRepasseServiceTaskRoleCanPublishToMatchingAlocacaoEventosTopic() {
        // Deploy ECS do liberacao-repasse-service continua deferido -- mas a
        // policy de publish no topico outbox proprio ja precisa existir
        // (Code Map da spec 3-3a), presa a MESMA LiberacaoRepasseServiceTaskRole
        // ja usada pela role de liberacao-repasse-service, nao uma
        // role nova: resolve os logical IDs reais do topico e da role,
        // confirma que E esta policy, presa a ESTA role, que aponta para
        // ESTE topico (mesmo raciocinio usado nos demais testes de
        // grant/policy desta classe).
        Map<String, Map<String, Object>> topicos = template.findResources("AWS::SNS::Topic",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "TopicName", "matching-alocacao-eventos.fifo")))));
        assertThat(topicos).hasSize(1);
        String topicoLogicalId = topicos.keySet().iterator().next();

        Map<String, Map<String, Object>> roles = template.findResources("AWS::IAM::Role",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "Description", Match.stringLikeRegexp(".*liberacao-repasse-service.*"))))));
        assertThat(roles).hasSize(1);
        String roleLogicalId = roles.keySet().iterator().next();

        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
                "Roles", Match.arrayWith(java.util.List.of(Match.objectLike(Map.of("Ref", roleLogicalId)))),
                "PolicyDocument", Match.objectLike(Map.of(
                        "Statement", Match.arrayWith(java.util.List.of(Match.objectLike(Map.of(
                                "Action", "sns:Publish",
                                "Effect", "Allow",
                                "Resource", Match.objectLike(Map.of("Ref", topicoLogicalId)))))))))));
    }

    @Test
    void vagaLiberadaQueueIsFifoWithDeadLetterQueue() {
        // Story 6.2: fila FIFO (exigida pela assinatura no topico FIFO) + DLQ
        // FIFO com maxReceiveCount=5 e visibilityTimeout=60s.
        Map<String, Map<String, Object>> dlqs = template.findResources("AWS::SQS::Queue",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "QueueName", "vaga-liberada-liberacao-repasse-dlq.fifo",
                        "FifoQueue", true)))));
        assertThat(dlqs).hasSize(1);
        String dlqLogicalId = dlqs.keySet().iterator().next();

        template.hasResourceProperties("AWS::SQS::Queue", Match.objectLike(Map.of(
                "QueueName", "vaga-liberada-liberacao-repasse.fifo",
                "FifoQueue", true,
                "VisibilityTimeout", 60,
                "RedrivePolicy", Match.objectLike(Map.of(
                        "deadLetterTargetArn", Match.objectLike(Map.of(
                                "Fn::GetAtt", Match.arrayWith(java.util.List.of(dlqLogicalId, "Arn")))),
                        "maxReceiveCount", 5)))));
    }

    @Test
    void vagaLiberadaQueueIsSubscribedToAgendamentoConfirmacaoEventosTopic() {
        // Envelope SNS mantido (sem RawMessageDelivery): o consumidor desembrulha.
        template.hasResourceProperties("AWS::SNS::Subscription", Match.objectLike(Map.of(
                "Protocol", "sqs",
                "RawMessageDelivery", Match.absent(),
                "FilterPolicyScope", "MessageBody",
                "FilterPolicy", Map.of("eventType", java.util.List.of("VagaLiberada")))));
        template.resourceCountIs("AWS::SNS::Subscription", 1);
    }

    @Test
    void liberacaoAgendadaLegacyQueueWasRemoved() {
        template.resourceCountIs("AWS::SQS::Queue", 2);
        template.hasOutput("VagaLiberadaQueueUrl", Match.anyValue());
    }

    @Test
    void agendamentoConfirmacaoEventosTopicIsFifo() {
        // Spec 1.2 (AD-3): topico SNS FIFO proprio do
        // agendamento-confirmacao-service que RelaySnsPublisherJob publica --
        // ordem deterministica por Agendamento via
        // MessageGroupId=agendamentoId, mesma propriedade dos topicos irmaos
        // MatchingAlocacaoEventosTopic
        // (ContentBasedDeduplication=false: MessageDeduplicationId sempre
        // explicito, o eventId do outbox).
        template.hasResourceProperties("AWS::SNS::Topic", Match.objectLike(Map.of(
                "TopicName", "agendamento-confirmacao-eventos.fifo",
                "FifoTopic", true,
                "ContentBasedDeduplication", false)));
    }

    @Test
    void agendamentoConfirmacaoServiceTaskRoleCanPublishToAgendamentoConfirmacaoEventosTopic() {
        // Diferente de liberacao-repasse-service (deploy ECS ainda
        // deferido, role standalone pre-criada), agendamento-confirmacao-service
        // ja tem um FargateService real -- a policy de publish precisa estar
        // presa a TaskRole DE FATO usada em runtime (a task role default da
        // AgendamentoConfirmacaoTaskDef), nao uma role a parte.
        Map<String, Map<String, Object>> topicos = template.findResources("AWS::SNS::Topic",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "TopicName", "agendamento-confirmacao-eventos.fifo")))));
        assertThat(topicos).hasSize(1);
        String topicoLogicalId = topicos.keySet().iterator().next();

        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
                "Roles", Match.arrayWith(java.util.List.of(Match.objectLike(Map.of("Ref",
                        Match.stringLikeRegexp("^AgendamentoConfirmacaoTaskDefTaskRole.*"))))),
                "PolicyDocument", Match.objectLike(Map.of(
                        "Statement", Match.arrayWith(java.util.List.of(Match.objectLike(Map.of(
                                "Action", "sns:Publish",
                                "Effect", "Allow",
                                "Resource", Match.objectLike(Map.of("Ref", topicoLogicalId)))))))))));
    }

    @Test
    void agendamentoConfirmacaoServiceReceivesOutboxTopicArnAsEnvironmentVariable() {
        // Spec 1.2: ARN nao e segredo -- injetado como env var comum
        // (Environment), nunca como ECS Secret.
        Object environmentMatch = Match.arrayWith(java.util.List.of(
                Match.objectLike(Map.of("Name", "CONFIRMASUS_AGENDAMENTO_OUTBOX_RELAY_TOPIC_ARN"))));
        Map<String, Object> container = Map.of(
                "Name", "agendamento-confirmacao-service",
                "Environment", environmentMatch);
        Object containerDefinitions = Match.arrayWith(java.util.List.of(Match.objectLike(container)));
        template.hasResourceProperties("AWS::ECS::TaskDefinition", Match.objectLike(Map.of(
                "ContainerDefinitions", containerDefinitions)));
    }

    @Test
    void liberacaoRepasseServiceTaskRoleCanConsumeVagaLiberadaQueue() {
        Map<String, Map<String, Object>> filas = template.findResources("AWS::SQS::Queue",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "QueueName", "vaga-liberada-liberacao-repasse.fifo")))));
        assertThat(filas).hasSize(1);
        String filaLogicalId = filas.keySet().iterator().next();

        Map<String, Map<String, Object>> roles = template.findResources("AWS::IAM::Role",
                Match.objectLike(Map.of("Properties", Match.objectLike(Map.of(
                        "Description", Match.stringLikeRegexp(".*liberacao-repasse-service.*"))))));
        assertThat(roles).hasSize(1);
        String roleLogicalId = roles.keySet().iterator().next();

        template.hasResourceProperties("AWS::IAM::Policy", Match.objectLike(Map.of(
                "Roles", Match.arrayWith(java.util.List.of(Match.objectLike(Map.of("Ref", roleLogicalId)))),
                "PolicyDocument", Match.objectLike(Map.of(
                        "Statement", Match.arrayWith(java.util.List.of(Match.objectLike(Map.of(
                                "Action", Match.arrayWith(java.util.List.of("sqs:ReceiveMessage")),
                                "Effect", "Allow",
                                "Resource", Match.objectLike(Map.of("Fn::GetAtt", Match.arrayWith(
                                        java.util.List.of(filaLogicalId, "Arn")))))))))))));
    }
}
