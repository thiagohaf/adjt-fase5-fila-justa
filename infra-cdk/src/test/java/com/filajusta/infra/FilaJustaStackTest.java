package com.filajusta.infra;

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
class FilaJustaStackTest {

    private static Template template;

    @BeforeAll
    static void synthesize() {
        App app = new App();
        FilaJustaStack stack = new FilaJustaStack(app, "TestStack", null);
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
    void threeFargateServicesAreProvisioned() {
        // postgres, gateway-service, auth-service (os 3 servicos de dominio ficam deferidos)
        template.resourceCountIs("AWS::ECS::Service", 3);
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
        assertThat(dependsOn).anyMatch(id -> id.startsWith("FilaJustaClusterDefaultServiceDiscoveryNamespace"));
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
        template.resourceCountIs("AWS::SecretsManager::Secret", 1);
    }

    @Test
    void templateIsNotNull() {
        assertThat(template).isNotNull();
    }
}
