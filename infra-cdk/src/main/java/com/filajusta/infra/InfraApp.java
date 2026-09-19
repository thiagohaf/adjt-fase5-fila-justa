package com.filajusta.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

/**
 * Ponto de entrada do app CDK do FilaJusta (story 1.1).
 *
 * <p>Conta/regiao vem do ambiente padrao da AWS CLI (variaveis
 * {@code CDK_DEFAULT_ACCOUNT}/{@code CDK_DEFAULT_REGION}, jah exportadas
 * pelo proprio `cdk` a partir do profile ativo) -- nenhuma credencial ou
 * conta fica hardcoded no repositorio (NFR-6).
 */
public final class InfraApp {

    private InfraApp() {
    }

    public static void main(final String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build();

        new FilaJustaStack(app, "FilaJustaStack", StackProps.builder()
                .env(env)
                .description("FilaJusta -- Story 1.1: VPC, ECS Fargate, Postgres 18 containerizado, "
                        + "gateway-service e auth-service (AD-8, AD-9, AD-12, AD-13).")
                .build());

        app.synth();
    }
}
