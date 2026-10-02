package com.confirmasus.infra;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AssetImageProps;
import software.amazon.awscdk.services.ecs.AuthorizationConfig;
import software.amazon.awscdk.services.ecs.CloudMapNamespaceOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.DeploymentCircuitBreaker;
import software.amazon.awscdk.services.ecs.EfsVolumeConfiguration;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.MountPoint;
import software.amazon.awscdk.services.ecs.CpuArchitecture;
import software.amazon.awscdk.services.ecs.OperatingSystemFamily;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.RuntimePlatform;
import software.amazon.awscdk.services.ecs.ServiceConnectProps;
import software.amazon.awscdk.services.ecs.ServiceConnectService;
import software.amazon.awscdk.services.ecs.Volume;
import software.amazon.awscdk.services.efs.AccessPoint;
import software.amazon.awscdk.services.efs.Acl;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.efs.PosixUser;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.amazon.awscdk.services.servicediscovery.INamespace;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.FilterOrPolicy;
import software.amazon.awscdk.services.sns.StringConditions;
import software.amazon.awscdk.services.sns.SubscriptionFilter;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscriptionProps;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * Stack unica do ConfirmaSus: VPC de subnet publica unica sem NAT Gateway (AD-11),
 * cluster ECS Fargate, Postgres 18 como container Fargate com volume EFS
 * persistente, e os servicos {@code gateway-service}, {@code auth-service},
 * {@code agendamento-confirmacao-service}, {@code liberacao-repasse-service} e
 * {@code auditoria-service}, todos com {@code assignPublicIp = ENABLED}.
 *
 * <p>Seguranca: so o security group do gateway alcanca a porta de aplicacao de cada
 * servico; cada servico tem uma excecao estreita e nomeada para health-check
 * (AD-8/AD-12). Service Connect fornece o DNS interno (ex.:
 * {@code auth-service:8081}, resolvido pelo Envoy como string exata, sem sufixo de
 * namespace). Segredos ({@code PostgresSecret}, {@code JwtSecret}) vivem no Secrets
 * Manager (NFR-6).
 *
 * <p>Mensageria (AD-3): topicos SNS FIFO {@code agendamento-confirmacao-eventos.fifo} e
 * {@code matching-alocacao-eventos.fifo} (nome legado mantido: contrato entre
 * servicos); fila {@code vaga-liberada-liberacao-repasse.fifo} (filtro
 * {@code VagaLiberada}) consumida pelo liberacao-repasse-service; fila
 * {@code auditoria-decisoes.fifo} (raw delivery) assinando os dois topicos e
 * consumida pelo auditoria-service; todas com DLQ ({@code maxReceiveCount=5}).
 * A priorizacao por score (triagem-score-service) foi decomissionada por
 * restricao legal: a Sugestao de Repasse e FIFO pura por Lista de Espera (AD-6).
 */
public class ConfirmaSusStack extends Stack {

    private static final String NAMESPACE = "confirmasus.local";

    // Imagens sao construidas nativamente na maquina de dev (Apple Silicon,
    // arm64) via ContainerImage.fromAsset -- sem isso as tasks Fargate rodam
    // em X86_64 por default e o container morre com "exec format error".
    // ARM64/Graviton tambem custa menos por vCPU-hora na Fargate.
    private static RuntimePlatform arm64Platform() {
        return RuntimePlatform.builder()
                .cpuArchitecture(CpuArchitecture.ARM64)
                .operatingSystemFamily(OperatingSystemFamily.LINUX)
                .build();
    }

    public ConfirmaSusStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        IVpc vpc = buildVpc();
        Cluster cluster = buildCluster(vpc);
        Secret dbSecret = buildDbSecret();

        // --- Security groups (AD-8/AD-12) --------------------------------
        SecurityGroup sgGatewayApp = SecurityGroup.Builder.create(this, "GatewayAppSg")
                .vpc(vpc)
                .description("gateway-service -- porta de aplicacao (unico ponto de entrada publico, AD-8)")
                .allowAllOutbound(true)
                .build();
        sgGatewayApp.addIngressRule(Peer.anyIpv4(), Port.tcp(8080),
                "Publico -- gateway e o unico ponto de entrada do sistema (AD-8); "
                        + "GET /actuator/health roda nesta mesma porta, sem token");

        SecurityGroup sgAuthApp = SecurityGroup.Builder.create(this, "AuthAppSg")
                .vpc(vpc)
                .description("auth-service -- porta de aplicacao, so alcancavel pelo gateway-service (AD-12)")
                .allowAllOutbound(true)
                .build();
        sgAuthApp.addIngressRule(sgGatewayApp, Port.tcp(8081),
                "Somente o security group do gateway-service alcanca a porta de aplicacao "
                        + "do auth-service -- bloqueia bypass direto (AD-12)");

        SecurityGroup sgAuthHealth = SecurityGroup.Builder.create(this, "AuthHealthSg")
                .vpc(vpc)
                .description("auth-service -- excecao estreita e nomeada, so a porta de health-check (AD-8/AD-12)")
                .allowAllOutbound(true)
                .build();
        sgAuthHealth.addIngressRule(Peer.anyIpv4(), Port.tcp(8090),
                "Excecao estreita de health-check por servico, nao reabre a porta de aplicacao (AD-12)");

        // agendamento-confirmacao-service (Story 1.1, Epic 1) -- mesmo molde
        // de sgAuthApp/sgAuthHealth acima: SG de app so alcancavel pelo
        // gateway-service, SG de health-check publico estreito. Sem SG/porta
        // gRPC nesta story (AD-11 -- gRPC ResolverOuCriarPaciente adiado
        // para Story 2.1, quando liberacao-repasse-service existir).
        SecurityGroup sgAgendamentoConfirmacaoApp = SecurityGroup.Builder.create(this, "AgendamentoConfirmacaoAppSg")
                .vpc(vpc)
                .description("agendamento-confirmacao-service -- porta de aplicacao, "
                        + "so alcancavel pelo gateway-service (AD-12)")
                .allowAllOutbound(true)
                .build();
        sgAgendamentoConfirmacaoApp.addIngressRule(sgGatewayApp, Port.tcp(8082),
                "Somente o security group do gateway-service alcanca a porta de aplicacao "
                        + "do agendamento-confirmacao-service -- bloqueia bypass direto (AD-12)");

        SecurityGroup sgAgendamentoConfirmacaoHealth =
                SecurityGroup.Builder.create(this, "AgendamentoConfirmacaoHealthSg")
                        .vpc(vpc)
                        .description("agendamento-confirmacao-service -- excecao estreita e nomeada, "
                                + "so a porta de health-check (AD-8/AD-12)")
                        .allowAllOutbound(true)
                        .build();
        sgAgendamentoConfirmacaoHealth.addIngressRule(Peer.anyIpv4(), Port.tcp(8091),
                "Excecao estreita de health-check por servico, nao reabre a porta de aplicacao (AD-12)");

        // liberacao-repasse-service e auditoria-service -- mesmo molde: SG de app
        // so alcancavel pelo gateway-service + SG de health-check estreito.
        SecurityGroup sgLiberacaoRepasseApp = appSecurityGroup("LiberacaoRepasseAppSg", vpc, sgGatewayApp,
                8083, "liberacao-repasse-service");
        SecurityGroup sgLiberacaoRepasseHealth = healthSecurityGroup("LiberacaoRepasseHealthSg", vpc, 8092,
                "liberacao-repasse-service");
        SecurityGroup sgAuditoriaApp = appSecurityGroup("AuditoriaAppSg", vpc, sgGatewayApp,
                8085, "auditoria-service");
        SecurityGroup sgAuditoriaHealth = healthSecurityGroup("AuditoriaHealthSg", vpc, 8094,
                "auditoria-service");

        SecurityGroup sgPostgres = SecurityGroup.Builder.create(this, "PostgresSg")
                .vpc(vpc)
                .description("Postgres 18 (container ECS Fargate) -- so alcancavel pelos servicos donos de schema (AD-9)")
                .allowAllOutbound(true)
                .build();
        sgPostgres.addIngressRule(sgAuthApp, Port.tcp(5432),
                "auth-service acessa seu proprio schema (auth) no cluster Postgres (AD-9)");
        sgPostgres.addIngressRule(sgAgendamentoConfirmacaoApp, Port.tcp(5432),
                "agendamento-confirmacao-service acessa seu proprio schema "
                        + "(agendamento_confirmacao) no cluster Postgres (AD-9)");

        sgPostgres.addIngressRule(sgLiberacaoRepasseApp, Port.tcp(5432),
                "liberacao-repasse-service acessa seu proprio schema (matching_alocacao) no cluster Postgres (AD-9)");
        sgPostgres.addIngressRule(sgAuditoriaApp, Port.tcp(5432),
                "auditoria-service acessa seu proprio schema (auditoria) no cluster Postgres (AD-9)");

        SecurityGroup sgPostgresEfs = SecurityGroup.Builder.create(this, "PostgresEfsSg")
                .vpc(vpc)
                .description("Mount targets EFS do volume de dados persistente do Postgres")
                .allowAllOutbound(true)
                .build();
        sgPostgresEfs.addIngressRule(sgPostgres, Port.tcp(2049),
                "Postgres monta o volume EFS persistente entre pause/resume (NFS)");

        // O FileSystem cria seus AWS::EFS::MountTarget dentro do proprio
        // construtor, com os security groups que ele conhece NESSE momento.
        // sgPostgresEfs precisa ser passado aqui, no builder -- adicionar via
        // .getConnections().addSecurityGroup(...) depois de construido nao
        // reabre os mount targets ja criados (causa raiz do timeout de mount
        // NFS: eles ficavam presos no SG default, sem nenhuma regra de
        // ingress).
        FileSystem postgresEfs = buildPostgresEfs(vpc, sgPostgresEfs);
        AccessPoint postgresAccessPoint = buildPostgresAccessPoint(postgresEfs);

        // --- Postgres 18 (task/service) -----------------------------------
        FargateService postgresService = buildPostgresService(
                cluster, vpc, dbSecret, postgresEfs, postgresAccessPoint, sgPostgres);
        // CloudFormation nao infere dependencia entre o ECS::Service (Service
        // Connect) e o namespace Cloud Map do cluster -- sem isso, os dois
        // recursos sao criados em paralelo e o Service pode tentar se
        // registrar antes do namespace propagar ("Failed to retrieve
        // namespace"). Forca a ordem explicitamente.
        INamespace cloudMapNamespace = cluster.getDefaultCloudMapNamespace();
        if (cloudMapNamespace != null) {
            postgresService.getNode().addDependency(cloudMapNamespace);
        }
        // Mesma classe de corrida: os mount targets EFS (um ENI por AZ) levam
        // ~1min para ficar "available" depois de criados; sem esperar por
        // eles, a primeira task tenta montar o volume NFS e recebe timeout
        // (mount.nfs4 mount system call failed). Padrao oficial do CDK.
        postgresService.getNode().addDependency(postgresEfs.getMountTargetsAvailable());

        // --- gateway-service (task/service) -------------------------------
        // JwtSecret criado antes do gateway-service: Story 1.2 acrescenta o
        // GlobalFilter que valida o JWT (AD-8) -- gateway-service tambem
        // precisa do segredo compartilhado, igual ao auth-service (AD-14).
        Secret jwtSecret = buildJwtSecret();
        FargateService gatewayService = buildGatewayService(cluster, vpc, sgGatewayApp, jwtSecret);
        // gateway-service agora e cliente Service Connect (Story 1.2, resolve
        // "auth-service"/"postgres") -- mesma corrida do namespace Cloud Map
        // documentada para postgresService/authService (L168-172).
        if (cloudMapNamespace != null) {
            gatewayService.getNode().addDependency(cloudMapNamespace);
        }

        // --- auth-service (task/service) ------------------------------------
        FargateService authService = buildAuthService(cluster, vpc, sgAuthApp, sgAuthHealth, dbSecret, jwtSecret);

        // auth-service conecta ao Postgres via JDBC (Story 1.2) -- precisa
        // existir depois do postgresService. Mesma corrida do namespace
        // Cloud Map do Service Connect que o Postgres ja tinha (L147-150):
        // sem DependsOn explicito, o ECS::Service pode se registrar antes do
        // namespace propagar ("Failed to retrieve namespace").
        authService.getNode().addDependency(postgresService);
        if (cloudMapNamespace != null) {
            authService.getNode().addDependency(cloudMapNamespace);
        }

        // --- Topico de outbox proprio do agendamento-confirmacao-service (spec 1.2, AD-3) ---
        // Criado antes do FargateService para poder ser passado ao metodo de
        // build abaixo, que concede grantPublish diretamente na TaskRole
        // real do servico (ja deployado, diferente de liberacao-repasse-service
        // -- nao ha necessidade de uma Role standalone pre-criada aqui).
        Topic agendamentoConfirmacaoEventosTopic = buildAgendamentoConfirmacaoEventosTopic();

        // --- agendamento-confirmacao-service (task/service, Story 1.1) ----
        FargateService agendamentoConfirmacaoService = buildAgendamentoConfirmacaoService(
                cluster, vpc, sgAgendamentoConfirmacaoApp, sgAgendamentoConfirmacaoHealth, dbSecret,
                agendamentoConfirmacaoEventosTopic);

        // Conecta ao Postgres via JDBC -- mesma corrida do namespace Cloud
        // Map do Service Connect ja documentada para postgresService/authService acima.
        agendamentoConfirmacaoService.getNode().addDependency(postgresService);
        if (cloudMapNamespace != null) {
            agendamentoConfirmacaoService.getNode().addDependency(cloudMapNamespace);
        }

        // --- Topico de outbox do liberacao-repasse-service (Story 3-3a, AD-3) e filas ---
        Role liberacaoRepasseServiceTaskRole = buildLiberacaoRepasseServiceTaskRole();
        Topic matchingAlocacaoEventosTopic = buildMatchingAlocacaoEventosTopic();
        matchingAlocacaoEventosTopic.grantPublish(liberacaoRepasseServiceTaskRole);

        // Fila de VagaLiberada (Story 6.2, AD-3/AD-6): FIFO + DLQ, assinada no
        // topico do agendamento-confirmacao-service; consumida por
        // VagaLiberadaSqsConsumerJob com a MESMA task role.
        Queue vagaLiberadaQueue = buildVagaLiberadaQueue();
        agendamentoConfirmacaoEventosTopic.addSubscription(new SqsSubscription(vagaLiberadaQueue, SqsSubscriptionProps.builder()
                // Envelope {eventType,...} vai no corpo (nao em message attributes):
                // filtra so VagaLiberada, os demais eventos do topico nao entram na fila.
                .filterPolicyWithMessageBody(Map.of("eventType",
                        FilterOrPolicy.filter(SubscriptionFilter.stringFilter(StringConditions.builder()
                                .allowlist(List.of("VagaLiberada"))
                                .build()))))
                .build()));
        vagaLiberadaQueue.grantConsumeMessages(liberacaoRepasseServiceTaskRole);

        // Fila de auditoria (AD-3/AD-7): FIFO + DLQ, assinando os topicos dos DOIS
        // servicos de dominio com raw delivery (o consumidor le o envelope do corpo).
        Queue auditoriaQueue = buildAuditoriaQueue();
        SqsSubscriptionProps rawDelivery = SqsSubscriptionProps.builder().rawMessageDelivery(true).build();
        agendamentoConfirmacaoEventosTopic.addSubscription(new SqsSubscription(auditoriaQueue, rawDelivery));
        matchingAlocacaoEventosTopic.addSubscription(new SqsSubscription(auditoriaQueue, rawDelivery));

        // --- liberacao-repasse-service (task/service) -----------------------
        FargateService liberacaoRepasseService = buildDomainService(cluster, "LiberacaoRepasse",
                "liberacao-repasse-service", 8083, 8092, sgLiberacaoRepasseApp, sgLiberacaoRepasseHealth,
                dbSecret, liberacaoRepasseServiceTaskRole,
                Map.of("CONFIRMASUS_MATCHING_OUTBOX_RELAY_ENABLED", "true",
                        "CONFIRMASUS_MATCHING_OUTBOX_RELAY_TOPIC_ARN", matchingAlocacaoEventosTopic.getTopicArn(),
                        "CONFIRMASUS_MATCHING_VAGA_LIBERADA_CONSUMER_ENABLED", "true",
                        "CONFIRMASUS_MATCHING_VAGA_LIBERADA_CONSUMER_QUEUE_URL", vagaLiberadaQueue.getQueueUrl()));
        liberacaoRepasseService.getNode().addDependency(postgresService);

        // --- auditoria-service (task/service) --------------------------------
        FargateService auditoriaService = buildDomainService(cluster, "Auditoria", "auditoria-service",
                8085, 8094, sgAuditoriaApp, sgAuditoriaHealth, dbSecret, null,
                Map.of("SERVER_PORT", "8085",
                        "MANAGEMENT_SERVER_PORT", "8094",
                        "SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/confirmasus",
                        "AUDITORIA_RELAY_ENABLED", "true",
                        "AUDITORIA_QUEUE_URL", auditoriaQueue.getQueueUrl()));
        auditoriaService.getNode().addDependency(postgresService);
        auditoriaQueue.grantConsumeMessages(auditoriaService.getTaskDefinition().getTaskRole());

        if (cloudMapNamespace != null) {
            liberacaoRepasseService.getNode().addDependency(cloudMapNamespace);
            auditoriaService.getNode().addDependency(cloudMapNamespace);
        }

        // --- Outputs (usados por pause.sh/destroy.sh/deploy.sh, e para o curl de verificacao) ---
        CfnOutput.Builder.create(this, "ClusterName").value(cluster.getClusterName()).build();
        CfnOutput.Builder.create(this, "GatewayServiceName").value(gatewayService.getServiceName()).build();
        CfnOutput.Builder.create(this, "AuthServiceName").value(authService.getServiceName()).build();
        CfnOutput.Builder.create(this, "AgendamentoConfirmacaoServiceName")
                .value(agendamentoConfirmacaoService.getServiceName())
                .build();
        CfnOutput.Builder.create(this, "LiberacaoRepasseServiceName")
                .value(liberacaoRepasseService.getServiceName())
                .build();
        CfnOutput.Builder.create(this, "AuditoriaServiceName").value(auditoriaService.getServiceName()).build();
        CfnOutput.Builder.create(this, "AuditoriaQueueUrl").value(auditoriaQueue.getQueueUrl()).build();
        CfnOutput.Builder.create(this, "PostgresServiceName").value(postgresService.getServiceName()).build();
        CfnOutput.Builder.create(this, "MatchingAlocacaoEventosTopicArn")
                .value(matchingAlocacaoEventosTopic.getTopicArn())
                .build();
        CfnOutput.Builder.create(this, "VagaLiberadaQueueUrl")
                .value(vagaLiberadaQueue.getQueueUrl())
                .build();
        CfnOutput.Builder.create(this, "AgendamentoConfirmacaoEventosTopicArn")
                .value(agendamentoConfirmacaoEventosTopic.getTopicArn())
                .build();
    }

    private Topic buildAgendamentoConfirmacaoEventosTopic() {
        // FIFO (nao standard) -- mesmo padrao de buildMatchingAlocacaoEventosTopic():
        // ordem deterministica por Agendamento via MessageGroupId=agendamentoId
        // (spec 1.2, AD-3). contentBasedDeduplication=false:
        // RelaySnsPublisherJob deste servico sempre manda um
        // MessageDeduplicationId explicito (o eventId do outbox), nunca
        // depende de deduplicacao por conteudo.
        return Topic.Builder.create(this, "AgendamentoConfirmacaoEventosTopic")
                .topicName("agendamento-confirmacao-eventos.fifo")
                .fifo(true)
                .contentBasedDeduplication(false)
                .build();
    }

    private Role buildLiberacaoRepasseServiceTaskRole() {
        return Role.Builder.create(this, "LiberacaoRepasseServiceTaskRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .description("Task role de liberacao-repasse-service -- publish no topico outbox "
                        + "proprio e consume da fila de VagaLiberada; usada como taskRole da "
                        + "FargateTaskDefinition do servico.")
                .build();
    }

    private Topic buildMatchingAlocacaoEventosTopic() {
        // FIFO (nao standard) -- mesmo padrao de buildScoreCalculadoTopic():
        // ordem determinística por recurso via MessageGroupId = recursoId
        // (Story 3-3a, spec-3-3a). contentBasedDeduplication=false:
        // RelaySnsPublisherJob deste servico sempre manda um
        // MessageDeduplicationId explicito (o eventId do outbox), nunca
        // depende de deduplicacao por conteudo.
        return Topic.Builder.create(this, "MatchingAlocacaoEventosTopic")
                .topicName("matching-alocacao-eventos.fifo")
                .fifo(true)
                .contentBasedDeduplication(false)
                .build();
    }

    private SecurityGroup appSecurityGroup(final String id, final IVpc vpc, final SecurityGroup sgGatewayApp,
                                           final int port, final String service) {
        SecurityGroup sg = SecurityGroup.Builder.create(this, id)
                .vpc(vpc)
                .description(service + " -- porta de aplicacao, so alcancavel pelo gateway-service (AD-12)")
                .allowAllOutbound(true)
                .build();
        sg.addIngressRule(sgGatewayApp, Port.tcp(port),
                "Somente o security group do gateway-service alcanca a porta de aplicacao "
                        + "do " + service + " -- bloqueia bypass direto (AD-12)");
        return sg;
    }

    private SecurityGroup healthSecurityGroup(final String id, final IVpc vpc, final int port,
                                              final String service) {
        SecurityGroup sg = SecurityGroup.Builder.create(this, id)
                .vpc(vpc)
                .description(service + " -- excecao estreita e nomeada, so a porta de health-check (AD-8/AD-12)")
                .allowAllOutbound(true)
                .build();
        sg.addIngressRule(Peer.anyIpv4(), Port.tcp(port),
                "Excecao estreita de health-check por servico, nao reabre a porta de aplicacao (AD-12)");
        return sg;
    }

    /**
     * Task/service Fargate de um servico de dominio Spring Boot (mesmo molde de
     * {@code buildAgendamentoConfirmacaoService}): imagem construida do Dockerfile do
     * modulo, credenciais do Postgres via Secrets Manager (NFR-6), Service Connect com
     * DNS interno igual ao nome do servico (sem sufixo de namespace).
     */
    private FargateService buildDomainService(final Cluster cluster, final String idPrefix,
                                              final String serviceName, final int appPort, final int healthPort,
                                              final SecurityGroup sgApp, final SecurityGroup sgHealth,
                                              final Secret dbSecret, final Role taskRole,
                                              final Map<String, String> environment) {
        LogGroup logGroup = LogGroup.Builder.create(this, idPrefix + "LogGroup")
                .logGroupName("/confirmasus/" + serviceName)
                .retention(RetentionDays.THREE_DAYS)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        FargateTaskDefinition.Builder taskDefBuilder = FargateTaskDefinition.Builder.create(this, idPrefix + "TaskDef")
                .cpu(256)
                .memoryLimitMiB(512)
                .runtimePlatform(arm64Platform());
        if (taskRole != null) {
            taskDefBuilder.taskRole(taskRole);
        }
        FargateTaskDefinition taskDef = taskDefBuilder.build();

        taskDef.addContainer(serviceName, ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromAsset("..", AssetImageProps.builder()
                        .file(serviceName + "/Dockerfile")
                        .build()))
                .containerName(serviceName)
                .logging(LogDriver.awsLogs(software.amazon.awscdk.services.ecs.AwsLogDriverProps.builder()
                        .streamPrefix(serviceName)
                        .logGroup(logGroup)
                        .build()))
                .portMappings(List.of(
                        PortMapping.builder().name(serviceName).containerPort(appPort).build(),
                        PortMapping.builder().containerPort(healthPort).build()))
                .secrets(Map.of(
                        "SPRING_DATASOURCE_USERNAME",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "username"),
                        "SPRING_DATASOURCE_PASSWORD",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password")))
                .environment(environment)
                .build());

        return FargateService.Builder.create(this, idPrefix + "Service")
                .cluster(cluster)
                .taskDefinition(taskDef)
                .desiredCount(1)
                .assignPublicIp(true)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .securityGroups(List.of(sgApp, sgHealth))
                .circuitBreaker(DeploymentCircuitBreaker.builder().rollback(true).build())
                .minHealthyPercent(50)
                .maxHealthyPercent(200)
                .serviceConnectConfiguration(ServiceConnectProps.builder()
                        .namespace(NAMESPACE)
                        .services(List.of(ServiceConnectService.builder()
                                .portMappingName(serviceName)
                                .dnsName(serviceName)
                                .port(appPort)
                                .build()))
                        .build())
                .build();
    }

    private Queue buildAuditoriaQueue() {
        // FIFO exigida pela assinatura em topicos SNS FIFO; DLQ com maxReceiveCount=5
        // e visibilityTimeout 60s (convencao do projeto, AD-3). Espelha
        // localstack/init/02-auditoria.sh.
        Queue dlq = Queue.Builder.create(this, "AuditoriaDlq")
                .queueName("auditoria-decisoes-dlq.fifo")
                .fifo(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        return Queue.Builder.create(this, "AuditoriaQueue")
                .queueName("auditoria-decisoes.fifo")
                .fifo(true)
                .deadLetterQueue(DeadLetterQueue.builder()
                        .queue(dlq)
                        .maxReceiveCount(5)
                        .build())
                .visibilityTimeout(Duration.seconds(60))
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private Queue buildVagaLiberadaQueue() {
        // FIFO: a assinatura em topico SNS FIFO exige fila FIFO; ordem por
        // Agendamento (MessageGroupId=agendamentoId). Deduplicacao vem do
        // MessageDeduplicationId explicito do topico (contentBased=false).
        // DLQ com maxReceiveCount=5 e visibilityTimeout 60s (convencao do projeto).
        Queue dlq = Queue.Builder.create(this, "VagaLiberadaDlq")
                .queueName("vaga-liberada-liberacao-repasse-dlq.fifo")
                .fifo(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        return Queue.Builder.create(this, "VagaLiberadaQueue")
                .queueName("vaga-liberada-liberacao-repasse.fifo")
                .fifo(true)
                .deadLetterQueue(DeadLetterQueue.builder()
                        .queue(dlq)
                        .maxReceiveCount(5)
                        .build())
                .visibilityTimeout(Duration.seconds(60))
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private IVpc buildVpc() {
        // Subnet publica unica, 2 AZs, sem NAT Gateway (AD-12, NFR-7).
        return Vpc.Builder.create(this, "ConfirmaSusVpc")
                .maxAzs(2)
                .natGateways(0)
                .subnetConfiguration(List.of(
                        SubnetConfiguration.builder()
                                .name("public")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(24)
                                .build()))
                .build();
    }

    private Cluster buildCluster(final IVpc vpc) {
        return Cluster.Builder.create(this, "ConfirmaSusCluster")
                .vpc(vpc)
                .clusterName("confirmasus")
                // Service Connect (DNS interno) para o Postgres -- Design Notes da spec 1.1.
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name(NAMESPACE)
                        .build())
                .build();
    }

    private Secret buildDbSecret() {
        // Segredo do Postgres nunca no repositorio -- AWS Secrets Manager (NFR-6).
        return Secret.Builder.create(this, "PostgresSecret")
                .description("Credenciais do usuario master do Postgres 18 (ConfirmaSus) -- NFR-6")
                .generateSecretString(SecretStringGenerator.builder()
                        .secretStringTemplate("{\"username\":\"confirmasus_admin\"}")
                        .generateStringKey("password")
                        .excludePunctuation(true)
                        .passwordLength(32)
                        .build())
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private Secret buildJwtSecret() {
        // Segredo HS256 compartilhado entre auth-service (emite) e
        // gateway-service (valida, JwtAuthenticationFilter) -- nunca no
        // repositorio (NFR-6). String simples (nao JSON): >= 32 bytes
        // exigidos pelo jjwt para HS256 (passwordLength 64 sobra de margem).
        return Secret.Builder.create(this, "JwtSecret")
                .description("Segredo HS256 compartilhado entre auth-service e gateway-service (AD-14, NFR-6)")
                .generateSecretString(SecretStringGenerator.builder()
                        .excludePunctuation(true)
                        .passwordLength(64)
                        .build())
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private FileSystem buildPostgresEfs(final IVpc vpc, final SecurityGroup sgPostgresEfs) {
        // Storage efemero da Fargate nao sobrevive a parada da task -- volume
        // EFS garante persistencia entre pause/resume (Design Notes da spec 1.1).
        return FileSystem.Builder.create(this, "PostgresDataFs")
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .securityGroup(sgPostgresEfs)
                .encrypted(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private AccessPoint buildPostgresAccessPoint(final FileSystem postgresEfs) {
        // Imagem oficial postgres roda o processo como uid/gid 999.
        return postgresEfs.addAccessPoint("PostgresAccessPoint",
                software.amazon.awscdk.services.efs.AccessPointOptions.builder()
                        .path("/postgres-data")
                        .createAcl(Acl.builder()
                                .ownerUid("999")
                                .ownerGid("999")
                                .permissions("750")
                                .build())
                        .posixUser(PosixUser.builder()
                                .uid("999")
                                .gid("999")
                                .build())
                        .build());
    }

    private FargateService buildPostgresService(final Cluster cluster, final IVpc vpc, final Secret dbSecret,
                                                 final FileSystem postgresEfs, final AccessPoint postgresAccessPoint,
                                                 final SecurityGroup sgPostgres) {
        LogGroup logGroup = LogGroup.Builder.create(this, "PostgresLogGroup")
                .logGroupName("/confirmasus/postgres")
                .retention(RetentionDays.THREE_DAYS)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "PostgresTaskDef")
                .cpu(512)
                .memoryLimitMiB(1024)
                .runtimePlatform(arm64Platform())
                .volumes(List.of(Volume.builder()
                        .name("postgres-data")
                        .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                                .fileSystemId(postgresEfs.getFileSystemId())
                                .transitEncryption("ENABLED")
                                .authorizationConfig(AuthorizationConfig.builder()
                                        .accessPointId(postgresAccessPoint.getAccessPointId())
                                        .iam("ENABLED")
                                        .build())
                                .build())
                        .build()))
                .build();

        // Task precisa de permissao IAM para montar/escrever no access point EFS.
        postgresEfs.grantRootAccess(taskDef.getTaskRole());

        var container = taskDef.addContainer("postgres", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("postgres:18"))
                .containerName("postgres")
                .environment(Map.of(
                        "POSTGRES_DB", "confirmasus",
                        "PGDATA", "/var/lib/postgresql/data/pgdata"))
                .secrets(Map.of(
                        "POSTGRES_USER",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "username"),
                        "POSTGRES_PASSWORD",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password")))
                .logging(LogDriver.awsLogs(software.amazon.awscdk.services.ecs.AwsLogDriverProps.builder()
                        .streamPrefix("postgres")
                        .logGroup(logGroup)
                        .build()))
                .portMappings(List.of(PortMapping.builder()
                        .name("postgres")
                        .containerPort(5432)
                        .build()))
                .build());
        container.addMountPoints(MountPoint.builder()
                .sourceVolume("postgres-data")
                .containerPath("/var/lib/postgresql/data")
                .readOnly(false)
                .build());

        return FargateService.Builder.create(this, "PostgresService")
                .cluster(cluster)
                .taskDefinition(taskDef)
                .desiredCount(1)
                .assignPublicIp(true) // sem NAT Gateway -- precisa de IP publico p/ pull da imagem (AD-12)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .securityGroups(List.of(sgPostgres))
                .circuitBreaker(DeploymentCircuitBreaker.builder().rollback(true).build())
                .minHealthyPercent(50)
                .maxHealthyPercent(200)
                .serviceConnectConfiguration(ServiceConnectProps.builder()
                        .namespace(NAMESPACE)
                        .services(List.of(ServiceConnectService.builder()
                                .portMappingName("postgres")
                                .dnsName("postgres")
                                .port(5432)
                                .build()))
                        .build())
                .build();
    }

    private FargateService buildGatewayService(final Cluster cluster, final IVpc vpc,
                                                final SecurityGroup sgGatewayApp, final Secret jwtSecret) {
        LogGroup logGroup = LogGroup.Builder.create(this, "GatewayLogGroup")
                .logGroupName("/confirmasus/gateway-service")
                .retention(RetentionDays.THREE_DAYS)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "GatewayTaskDef")
                .cpu(256)
                .memoryLimitMiB(512)
                .runtimePlatform(arm64Platform())
                .build();

        taskDef.addContainer("gateway-service", ContainerDefinitionOptions.builder()
                // Build context = raiz do repo; Dockerfile builda o reactor Maven (AD-13).
                .image(ContainerImage.fromAsset("..", AssetImageProps.builder()
                        .file("gateway-service/Dockerfile")
                        .build()))
                .containerName("gateway-service")
                .logging(LogDriver.awsLogs(software.amazon.awscdk.services.ecs.AwsLogDriverProps.builder()
                        .streamPrefix("gateway-service")
                        .logGroup(logGroup)
                        .build()))
                .portMappings(List.of(PortMapping.builder()
                        .containerPort(8080)
                        .build()))
                // Segredo HS256 compartilhado com o auth-service (AD-14) --
                // consumido pelo JwtAuthenticationFilter (Story 1.2) para
                // validar assinatura/expiracao de qualquer rota fora da
                // allowlist publica. Nunca no repositorio (NFR-6).
                .secrets(Map.of(
                        "CONFIRMASUS_JWT_SECRET",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(jwtSecret)))
                .build());

        return FargateService.Builder.create(this, "GatewayService")
                .cluster(cluster)
                .taskDefinition(taskDef)
                .desiredCount(1)
                .assignPublicIp(true)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .securityGroups(List.of(sgGatewayApp))
                .circuitBreaker(DeploymentCircuitBreaker.builder().rollback(true).build())
                .minHealthyPercent(50)
                .maxHealthyPercent(200)
                // So cliente (nao publica nada) -- sem isso o gateway nao tem o
                // sidecar Envoy do Service Connect e nao resolve NENHUM dnsName
                // (nem "postgres" nem "auth-service"). Bug real encontrado na
                // verificacao ao vivo: POST /v1/auth/login retornava 500 --
                // UnknownHostException "Failed to resolve 'auth-service'"
                // (NXDOMAIN) na rota do gateway para o auth-service.
                .serviceConnectConfiguration(ServiceConnectProps.builder()
                        .namespace(NAMESPACE)
                        .build())
                .build();
    }

    private FargateService buildAuthService(final Cluster cluster, final IVpc vpc,
                                             final SecurityGroup sgAuthApp, final SecurityGroup sgAuthHealth,
                                             final Secret dbSecret, final Secret jwtSecret) {
        LogGroup logGroup = LogGroup.Builder.create(this, "AuthLogGroup")
                .logGroupName("/confirmasus/auth-service")
                .retention(RetentionDays.THREE_DAYS)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "AuthTaskDef")
                .cpu(256)
                .memoryLimitMiB(512)
                .runtimePlatform(arm64Platform())
                .build();

        taskDef.addContainer("auth-service", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromAsset("..", AssetImageProps.builder()
                        .file("auth-service/Dockerfile")
                        .build()))
                .containerName("auth-service")
                .logging(LogDriver.awsLogs(software.amazon.awscdk.services.ecs.AwsLogDriverProps.builder()
                        .streamPrefix("auth-service")
                        .logGroup(logGroup)
                        .build()))
                .portMappings(List.of(
                        // Nome exigido pelo Service Connect (portMappingName abaixo);
                        // a porta de health-check (8090) nao precisa de DNS interno.
                        PortMapping.builder().name("auth-service").containerPort(8081).build(),
                        PortMapping.builder().containerPort(8090).build()))
                // Credenciais do Postgres reusam o secret admin da Story 1.1
                // (mesmo padrao de buildPostgresService); segredo JWT e proprio
                // deste servico -- nenhum dos dois no repositorio (NFR-6).
                .secrets(Map.of(
                        "SPRING_DATASOURCE_USERNAME",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "username"),
                        "SPRING_DATASOURCE_PASSWORD",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password"),
                        "CONFIRMASUS_JWT_SECRET",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(jwtSecret)))
                .build());

        return FargateService.Builder.create(this, "AuthService")
                .cluster(cluster)
                .taskDefinition(taskDef)
                .desiredCount(1)
                .assignPublicIp(true)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                // Duas SGs: app (so gateway) + health (excecao publica estreita) -- AD-8/AD-12.
                .securityGroups(List.of(sgAuthApp, sgAuthHealth))
                .circuitBreaker(DeploymentCircuitBreaker.builder().rollback(true).build())
                .minHealthyPercent(50)
                .maxHealthyPercent(200)
                // DNS interno "auth-service:8081" (SEM sufixo de namespace --
                // o Envoy do Service Connect resolve pela string exata do
                // dnsName) -- e como o gateway-service alcanca o login
                // (application.yml da rota publica), mesmo padrao de
                // buildPostgresService (L308-315).
                .serviceConnectConfiguration(ServiceConnectProps.builder()
                        .namespace(NAMESPACE)
                        .services(List.of(ServiceConnectService.builder()
                                .portMappingName("auth-service")
                                .dnsName("auth-service")
                                .port(8081)
                                .build()))
                        .build())
                .build();
    }

    private FargateService buildAgendamentoConfirmacaoService(final Cluster cluster, final IVpc vpc,
                                                                final SecurityGroup sgAgendamentoConfirmacaoApp,
                                                                final SecurityGroup sgAgendamentoConfirmacaoHealth,
                                                                final Secret dbSecret,
                                                                final Topic agendamentoConfirmacaoEventosTopic) {
        LogGroup logGroup = LogGroup.Builder.create(this, "AgendamentoConfirmacaoLogGroup")
                .logGroupName("/confirmasus/agendamento-confirmacao-service")
                .retention(RetentionDays.THREE_DAYS)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        FargateTaskDefinition taskDef = FargateTaskDefinition.Builder.create(this, "AgendamentoConfirmacaoTaskDef")
                .cpu(256)
                .memoryLimitMiB(512)
                .runtimePlatform(arm64Platform())
                .build();

        // Spec 1.2 (AD-3): RelaySnsPublisherJob deste servico publica no
        // topico proprio -- concede a permissao diretamente na TaskRole real
        // da task (nao uma Role standalone: diferente de
        // liberacao-repasse-service, este servico ja tem um FargateService
        // deployado, entao a role de fato usada em runtime e
        // taskDef.getTaskRole()).
        agendamentoConfirmacaoEventosTopic.grantPublish(taskDef.getTaskRole());

        taskDef.addContainer("agendamento-confirmacao-service", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromAsset("..", AssetImageProps.builder()
                        .file("agendamento-confirmacao-service/Dockerfile")
                        .build()))
                .containerName("agendamento-confirmacao-service")
                .logging(LogDriver.awsLogs(software.amazon.awscdk.services.ecs.AwsLogDriverProps.builder()
                        .streamPrefix("agendamento-confirmacao-service")
                        .logGroup(logGroup)
                        .build()))
                .portMappings(List.of(
                        // Nome exigido pelo Service Connect (portMappingName abaixo);
                        // a porta de health-check (8091) nao precisa de DNS interno.
                        PortMapping.builder().name("agendamento-confirmacao-service").containerPort(8082).build(),
                        PortMapping.builder().containerPort(8091).build()))
                // Credenciais do Postgres reusam o secret admin da Story 1.1
                // (mesmo padrao de buildAuthService) -- nunca no repositorio
                // (NFR-6). Sem segredo gRPC nesta story (AD-11, adiado).
                .secrets(Map.of(
                        "SPRING_DATASOURCE_USERNAME",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "username"),
                        "SPRING_DATASOURCE_PASSWORD",
                        software.amazon.awscdk.services.ecs.Secret.fromSecretsManager(dbSecret, "password")))
                // ARN do topico outbox (spec 1.2) -- nao e segredo (Resource
                // ARN publico dentro da conta), injetado como variavel de
                // ambiente comum (mesmo padrao de CONFIRMASUS_MATCHING_OUTBOX_RELAY_TOPIC_ARN
                // em liberacao-repasse-service, que tambem nao usa Secret).
                .environment(Map.of(
                        "CONFIRMASUS_AGENDAMENTO_OUTBOX_RELAY_TOPIC_ARN",
                        agendamentoConfirmacaoEventosTopic.getTopicArn()))
                .build());

        return FargateService.Builder.create(this, "AgendamentoConfirmacaoService")
                .cluster(cluster)
                .taskDefinition(taskDef)
                .desiredCount(1)
                .assignPublicIp(true)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                // Duas SGs: app (so gateway) + health (excecao publica estreita) -- AD-8/AD-12.
                // Sem terceira SG/porta gRPC nesta story (AD-11 -- adiado
                // para Story 2.1, quando liberacao-repasse-service existir).
                .securityGroups(List.of(sgAgendamentoConfirmacaoApp, sgAgendamentoConfirmacaoHealth))
                .circuitBreaker(DeploymentCircuitBreaker.builder().rollback(true).build())
                .minHealthyPercent(50)
                .maxHealthyPercent(200)
                // DNS interno "agendamento-confirmacao-service:8082" (SEM
                // sufixo de namespace, mesmo padrao de buildAuthService) --
                // e como o gateway-service alcanca POST /v1/agendamentos
                // (rota nova em gateway-service/application.yml).
                .serviceConnectConfiguration(ServiceConnectProps.builder()
                        .namespace(NAMESPACE)
                        .services(List.of(ServiceConnectService.builder()
                                .portMappingName("agendamento-confirmacao-service")
                                .dnsName("agendamento-confirmacao-service")
                                .port(8082)
                                .build()))
                        .build())
                .build();
    }
}
