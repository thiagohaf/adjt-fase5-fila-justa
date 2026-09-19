package com.filajusta.agendamento.db.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida que as migrations Flyway são aplicadas corretamente contra um
 * Postgres 18 real via Testcontainers (mesmo padrão de
 * {@code AgendamentoControllerIntegrationTest}).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "filajusta.agendamento.outbox-relay.enabled=false")
class FlywayMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testV3AddsMotiveLiberacaoColumn() {
        // Verifica que a migration V3 foi aplicada: coluna motivo_liberacao
        // existe, é VARCHAR(32) e permite NULL.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'agendamento_confirmacao' "
                        + "AND table_name = 'agendamentos' "
                        + "AND column_name = 'motivo_liberacao' "
                        + "AND data_type = 'character varying' "
                        + "AND character_maximum_length = 32 "
                        + "AND is_nullable = 'YES'",
                Integer.class);
        assertThat(count)
                .as("Migration V3 deve ter adicionado coluna motivo_liberacao VARCHAR(32) NULL")
                .isEqualTo(1);
    }
}
