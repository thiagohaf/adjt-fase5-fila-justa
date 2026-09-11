package com.filajusta.infra;

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
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscriptionProps;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * Stack unica do FilaJusta -- Story 1.1 (Subida do Ambiente com Health-Check
 * Publico) + Story 1.2 (Autenticacao de Usuario via auth-service).
 *
 * <p>Provisiona: VPC de subnet publica unica, sem NAT Gateway (AD-12);
 * cluster ECS Fargate; Postgres 18 como container Fargate com volume EFS
 * persistente (nao RDS, decisao de custo desta story); tasks/services de
 * {@code gateway-service} e {@code auth-service}, com {@code assignPublicIp
 * = ENABLED} (AD-12); security groups que garantem que so o gateway alcanca
 * a porta de aplicacao do auth-service, com excecao estreita e nomeada para
 * health-check (AD-8/AD-12). Story 1.2 acrescenta: Service Connect do
 * auth-service (DNS interno {@code auth-service:8081} -- dnsName resolvido
 * pelo Envoy como string exata, sem sufixo de namespace), secret
 * {@code JwtSecret} (HS256, AD-14) e as credenciais do Postgres
 * (reusando {@code PostgresSecret}) injetadas como env vars no auth-service.
 * A spec de validacao de JWT no gateway acrescenta o mesmo
 * {@code JwtSecret} tambem como env var {@code FILAJUSTA_JWT_SECRET} do
 * {@code gateway-service} -- consumido pelo {@code JwtAuthenticationFilter}
 * (unico ponto de validacao de assinatura/expiracao, AD-8).
 *
 * <p>Os 3 servicos de dominio deferidos (ver deferred-work.md) nao entram
 * aqui -- seguirao o mesmo padrao (task/service + par de security groups
 * app/health) quando suas stories comecarem. Story 3.0 (relay real do
 * evento {@code ScoreCalculado}) e a primeira excecao parcial: declara o
 * topico SNS FIFO {@code score-calculado.fifo} (AD-3) e a IAM role de
 * publish de {@code triagem-score-service} antes do proprio deploy ECS
 * daquele servico continuar deferido -- quando a
 * {@code FargateTaskDefinition} real for criada, ela deve reusar
 * {@code TriagemScoreServiceTaskRole} (nao criar outra), para que esta
 * policy de publish ja valha para a task. Story 3.1b acrescenta o mesmo
 * padrao do lado consumidor: fila SQS FIFO assinante do topico (+ DLQ,
 * {@code maxReceiveCount=5}) e a IAM role de consumo de
 * {@code matching-alocacao-service} -- deploy ECS daquele servico tambem
 * continua deferido; a futura {@code FargateTaskDefinition} deve reusar
 * {@code MatchingAlocacaoServiceTaskRole}. Story 3-3a acrescenta o topico
 * SNS FIFO proprio de {@code matching-alocacao-service}
 * ({@code matching-alocacao-eventos.fifo}, relay outbox daquele servico) --
 * publish concedido a mesma {@code MatchingAlocacaoServiceTaskRole} acima
 * (nao cria outra role); nenhuma fila assinante ainda (fora de escopo,
 * Epic 4/auditoria-service assina depois).
 */
public class FilaJustaStack extends Stack {

    private static final String NAMESPACE = "filajusta.local";

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

    public FilaJustaStack(final Construct scope, final String id, final StackProps props) {
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

        SecurityGroup sgPostgres = SecurityGroup.Builder.create(this, "PostgresSg")
                .vpc(vpc)
                .description("Postgres 18 (container ECS Fargate) -- so alcancavel pelos servicos donos de schema (AD-9)")
                .allowAllOutbound(true)
                .build();
        sgPostgres.addIngressRule(sgAuthApp, Port.tcp(5432),
                "auth-service acessa seu proprio schema (auth) no cluster Postgres (AD-9)");

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

        // --- Relay do evento ScoreCalculado (Story 3.0, AD-3) --------------
        // So o topico + a role de publish -- deploy do triagem-score-service
        // no ECS continua deferido (deferred-work.md, ver javadoc da classe).
        Topic scoreCalculadoTopic = buildScoreCalculadoTopic();
        buildTriagemScoreServiceTaskRole(scoreCalculadoTopic);

        // --- Consumidor do evento ScoreCalculado (Story 3.1b) --------------
        // Fila SQS FIFO assinante do topico acima + DLQ + a role de consumo
        // -- deploy do matching-alocacao-service no ECS tambem continua
        // deferido (deferred-work.md, ver javadoc da classe).
        Queue scoreCalculadoConsumerQueue = buildScoreCalculadoConsumerQueue(scoreCalculadoTopic);
        Role matchingAlocacaoServiceTaskRole = buildMatchingAlocacaoServiceTaskRole(scoreCalculadoConsumerQueue);

        // --- Relay outbox proprio do matching-alocacao-service (Story 3-3a, AD-3) ---
        // So o topico + a permissao de publish -- deploy ECS deste servico
        // continua deferido (deferred-work.md, ver javadoc da classe); reusa
        // a MatchingAlocacaoServiceTaskRole ja existente (Story 3.1b), nao
        // cria outra role (mesmo principio do topico ScoreCalculado acima).
        // Nenhuma fila/subscription assinante nesta story -- Epic 4
        // (auditoria-service) assina depois, fora de escopo.
        Topic matchingAlocacaoEventosTopic = buildMatchingAlocacaoEventosTopic();
        matchingAlocacaoEventosTopic.grantPublish(matchingAlocacaoServiceTaskRole);

        // --- Outputs (usados por pause.sh/destroy.sh/deploy.sh, e para o curl de verificacao) ---
        CfnOutput.Builder.create(this, "ClusterName").value(cluster.getClusterName()).build();
        CfnOutput.Builder.create(this, "GatewayServiceName").value(gatewayService.getServiceName()).build();
        CfnOutput.Builder.create(this, "AuthServiceName").value(authService.getServiceName()).build();
        CfnOutput.Builder.create(this, "PostgresServiceName").value(postgresService.getServiceName()).build();
        CfnOutput.Builder.create(this, "ScoreCalculadoTopicArn").value(scoreCalculadoTopic.getTopicArn()).build();
        CfnOutput.Builder.create(this, "ScoreCalculadoConsumerQueueUrl")
                .value(scoreCalculadoConsumerQueue.getQueueUrl())
                .build();
        CfnOutput.Builder.create(this, "MatchingAlocacaoEventosTopicArn")
                .value(matchingAlocacaoEventosTopic.getTopicArn())
                .build();
    }

    private Topic buildScoreCalculadoTopic() {
        // FIFO (nao standard) -- ordem determinística por paciente via
        // MessageGroupId = pacienteId (AD-3, ARCHITECTURE-SPINE.md).
        // contentBasedDeduplication=false: RelaySnsPublisherJob sempre manda
        // um MessageDeduplicationId explicito (o eventId do outbox), nunca
        // depende de deduplicacao por conteudo.
        return Topic.Builder.create(this, "ScoreCalculadoTopic")
                .topicName("score-calculado.fifo")
                .fifo(true)
                .contentBasedDeduplication(false)
                .build();
    }

    private Role buildTriagemScoreServiceTaskRole(final Topic scoreCalculadoTopic) {
        Role role = Role.Builder.create(this, "TriagemScoreServiceTaskRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .description("Task role de triagem-score-service (Story 3.0, RelaySnsPublisherJob) -- criada "
                        + "antes do deploy ECS daquele servico (deferred-work.md) so para a policy de publish "
                        + "no topico SNS FIFO ja existir; reusar esta role (nao criar outra) quando a "
                        + "FargateTaskDefinition for adicionada.")
                .build();
        scoreCalculadoTopic.grantPublish(role);
        return role;
    }

    private Queue buildScoreCalculadoConsumerQueue(final Topic scoreCalculadoTopic) {
        // DLQ com maxReceiveCount=5 (mesma convencao ja usada na fila de
        // teste da Story 3.0, RelaySnsPublisherJobIntegrationTest -- Design
        // Notes da spec 3.1b: convencao do projeto, nao fixada no
        // epic-3-context.md). FIFO (nao standard) -- consistente com o
        // topico origem, preserva ordem por pacienteId dentro do grupo.
        Queue dlq = Queue.Builder.create(this, "ScoreCalculadoConsumerDlq")
                .queueName("score-calculado-matching-dlq.fifo")
                .fifo(true)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Queue queue = Queue.Builder.create(this, "ScoreCalculadoConsumerQueue")
                .queueName("score-calculado-matching.fifo")
                .fifo(true)
                .deadLetterQueue(DeadLetterQueue.builder()
                        .queue(dlq)
                        .maxReceiveCount(5)
                        .build())
                // Achado do code review: default do SQS e 30s. O poller
                // (ScoreCalculadoConsumerJob) processa ate batch-size (10)
                // mensagens sequencialmente numa unica execucao @Scheduled --
                // sob lentidao do banco o tempo cumulativo pode se
                // aproximar/exceder 30s, causando redelivery prematuro antes
                // do deleteMessage rodar (idempotencia cobre a correcao, mas
                // gera reprocessamento/log desnecessario). 60s da margem de
                // seguranca confortavel para um lote inteiro.
                .visibilityTimeout(Duration.seconds(60))
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // RawMessageDelivery=true (mesma escolha da fila de teste da Story
        // 3.0): o corpo da mensagem SQS e o envelope publicado direto, sem
        // o wrapper JSON padrao do SNS -- e o que ScoreCalculadoConsumerJob
        // (matching-alocacao-service) espera ler.
        scoreCalculadoTopic.addSubscription(new SqsSubscription(queue,
                SqsSubscriptionProps.builder().rawMessageDelivery(true).build()));

        return queue;
    }

    private Role buildMatchingAlocacaoServiceTaskRole(final Queue scoreCalculadoConsumerQueue) {
        Role role = Role.Builder.create(this, "MatchingAlocacaoServiceTaskRole")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .description("Task role de matching-alocacao-service (Story 3.1b, ScoreCalculadoConsumerJob) -- "
                        + "criada antes do deploy ECS daquele servico (deferred-work.md) so para a policy de "
                        + "consumo da fila SQS FIFO ja existir; reusar esta role (nao criar outra) quando a "
                        + "FargateTaskDefinition for adicionada.")
                .build();
        scoreCalculadoConsumerQueue.grantConsumeMessages(role);
        return role;
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

    private IVpc buildVpc() {
        // Subnet publica unica, 2 AZs, sem NAT Gateway (AD-12, NFR-7).
        return Vpc.Builder.create(this, "FilaJustaVpc")
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
        return Cluster.Builder.create(this, "FilaJustaCluster")
                .vpc(vpc)
                .clusterName("fila-justa")
                // Service Connect (DNS interno) para o Postgres -- Design Notes da spec 1.1.
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name(NAMESPACE)
                        .build())
                .build();
    }

    private Secret buildDbSecret() {
        // Segredo do Postgres nunca no repositorio -- AWS Secrets Manager (NFR-6).
        return Secret.Builder.create(this, "PostgresSecret")
                .description("Credenciais do usuario master do Postgres 18 (FilaJusta) -- NFR-6")
                .generateSecretString(SecretStringGenerator.builder()
                        .secretStringTemplate("{\"username\":\"filajusta_admin\"}")
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
                .logGroupName("/filajusta/postgres")
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
                        "POSTGRES_DB", "filajusta",
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
                .logGroupName("/filajusta/gateway-service")
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
                        "FILAJUSTA_JWT_SECRET",
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
                .logGroupName("/filajusta/auth-service")
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
                        "FILAJUSTA_JWT_SECRET",
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
}
