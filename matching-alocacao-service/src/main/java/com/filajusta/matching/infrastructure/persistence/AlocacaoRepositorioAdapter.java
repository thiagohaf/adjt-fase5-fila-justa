package com.filajusta.matching.infrastructure.persistence;

import com.filajusta.matching.application.command.AlocacaoRepositorio;
import com.filajusta.matching.application.command.PacienteJaAlocadoException;
import com.filajusta.matching.application.command.RecursoJaAlocadoException;
import com.filajusta.matching.domain.Alocacao;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter que implementa a porta {@link AlocacaoRepositorio}
 * (application/command) usando {@link AlocacaoJpaRepository} (Spring Data,
 * schema {@code matching_alocacao}). {@link #confirmar(Alocacao)} insere a
 * linha via {@link AlocacaoJpaRepository#inserir} -- ao contrário de
 * {@code RecursoRepositorioAdapter#upsert}, não há releitura pós-insert: sem
 * {@code ON CONFLICT}, um INSERT bem-sucedido persiste exatamente o
 * {@code alocacaoId} gerado pela aplicação, então o candidato recebido JÁ É
 * o efetivamente persistido.
 *
 * <p>Os 2 índices únicos parciais ({@code V5__create_alocacao.sql}) são a
 * única fonte de verdade sob concorrência (Boundaries da spec 3-3b1): uma
 * violação chega aqui como {@link DataIntegrityViolationException},
 * traduzida pelo Spring a partir da exceção nativa do driver Postgres. O
 * nome da constraint violada ({@link ConstraintViolationException#getConstraintName()},
 * populado pelo Hibernate a partir do erro reportado pelo Postgres) decide
 * qual exceção de domínio lançar -- {@code ux_alocacao_recurso_ativa} vira
 * {@link RecursoJaAlocadoException}, {@code ux_alocacao_paciente_ativa} vira
 * {@link PacienteJaAlocadoException}. Qualquer outra violação (não
 * esperada) propaga sem tradução, para nunca mascarar um erro real como
 * {@code 409}.
 */
@Component
class AlocacaoRepositorioAdapter implements AlocacaoRepositorio {

    private static final String CONSTRAINT_RECURSO_ATIVA = "ux_alocacao_recurso_ativa";
    private static final String CONSTRAINT_PACIENTE_ATIVA = "ux_alocacao_paciente_ativa";

    private final AlocacaoJpaRepository jpaRepository;

    AlocacaoRepositorioAdapter(AlocacaoJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Alocacao confirmar(Alocacao alocacao) {
        try {
            jpaRepository.inserir(alocacao.getAlocacaoId(), alocacao.getRecursoId(), alocacao.getPacienteId(),
                    alocacao.getStatus(), alocacao.getConfirmadoEm());
        } catch (DataIntegrityViolationException ex) {
            throw traduzir(ex, alocacao);
        }
        return alocacao;
    }

    private static RuntimeException traduzir(DataIntegrityViolationException ex, Alocacao alocacao) {
        String constraintName = nomeDaConstraintViolada(ex);
        if (CONSTRAINT_RECURSO_ATIVA.equals(constraintName)) {
            return new RecursoJaAlocadoException(alocacao.getRecursoId());
        }
        if (CONSTRAINT_PACIENTE_ATIVA.equals(constraintName)) {
            return new PacienteJaAlocadoException(alocacao.getPacienteId());
        }
        return ex;
    }

    private static String nomeDaConstraintViolada(DataIntegrityViolationException ex) {
        Throwable causa = ex.getCause();
        if (causa instanceof ConstraintViolationException constraintViolationException) {
            return constraintViolationException.getConstraintName();
        }
        return null;
    }
}
