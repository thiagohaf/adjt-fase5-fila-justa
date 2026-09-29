package com.confirmasus.matching.application.command;

import com.confirmasus.matching.domain.Cpf;
import com.confirmasus.matching.domain.ListaEsperaEntrada;
import com.confirmasus.matching.domain.Paciente;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Caso de uso de POST /v1/lista-espera (Story 5.3): resolve/cria o Paciente
 * por CPF, valida recursoId e dataSolicitacao, e persiste entrada de Lista
 * de Espera.
 *
 * Idempotência por UNIQUE(paciente_id, recurso_id): reexecução retorna 201
 * se nova, ou 409 se já existe.
 */
public class CriarEntradaListaEspera {

    private final ResolverOuCriarPaciente resolverOuCriarPaciente;
    private final ListaEsperaEntradaRepositorio listaEsperaRepositorio;
    private final Clock clock;

    public CriarEntradaListaEspera(ResolverOuCriarPaciente resolverOuCriarPaciente,
                                   ListaEsperaEntradaRepositorio listaEsperaRepositorio,
                                   Clock clock) {
        this.resolverOuCriarPaciente = resolverOuCriarPaciente;
        this.listaEsperaRepositorio = listaEsperaRepositorio;
        this.clock = clock;
    }

    /**
     * Resultado: 201 (novo), 409 (duplicata)
     */
    @Transactional
    public ListaEsperaEntrada criar(String cpfTexto, String recursoIdTexto, Instant dataSolicitacao) {
        Cpf cpf = new Cpf(cpfTexto);
        UUID recursoId = validarRecursoId(recursoIdTexto);
        Instant agora = clock.instant();
        validarDataSolicitacao(dataSolicitacao, agora);

        Paciente paciente = resolverOuCriarPaciente.resolver(cpf);

        var existente = listaEsperaRepositorio.buscarPorPacienteIdERecursoId(paciente.getId(), recursoId);
        if (existente.isPresent()) {
            throw new EntradaJaExisteException(
                    "Entrada de Lista de Espera já existe para pacienteId=" + paciente.getId() +
                    ", recursoId=" + recursoId);
        }

        ListaEsperaEntrada entrada = ListaEsperaEntrada.nova(paciente.getId(), recursoId, dataSolicitacao, agora);
        try {
            return listaEsperaRepositorio.salvar(entrada);
        } catch (DataIntegrityViolationException e) {
            var existenteAposRace = listaEsperaRepositorio.buscarPorPacienteIdERecursoId(paciente.getId(), recursoId);
            if (existenteAposRace.isPresent()) {
                throw new EntradaJaExisteException("Entrada já existe (race condition)");
            }
            throw e;
        }
    }

    private static UUID validarRecursoId(String recursoIdTexto) {
        if (recursoIdTexto == null || recursoIdTexto.isBlank()) {
            throw new IllegalArgumentException("recursoId é obrigatório");
        }
        try {
            return UUID.fromString(recursoIdTexto);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("recursoId deve ser um UUID válido");
        }
    }

    private static void validarDataSolicitacao(Instant dataSolicitacao, Instant agora) {
        if (dataSolicitacao == null) {
            throw new IllegalArgumentException("dataSolicitacao é obrigatória");
        }
        if (!dataSolicitacao.isBefore(agora) && !dataSolicitacao.equals(agora)) {
            throw new IllegalArgumentException("dataSolicitacao deve ser no passado ou presente");
        }
    }
}
