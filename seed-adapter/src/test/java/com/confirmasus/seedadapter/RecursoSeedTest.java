package com.confirmasus.seedadapter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes para RecursoSeed (Story 5.1).
 */
class RecursoSeedTest {

    @Test
    void testRecursoSeedConstruction() {
        RecursoSeed recurso = new RecursoSeed("01", "Cardiologia", "Hospital Central", 1, true);

        assertEquals("01", recurso.getCodigoRecurso());
        assertEquals("Cardiologia", recurso.getEspecialidade());
        assertEquals("Hospital Central", recurso.getUnidade());
        assertEquals(1, recurso.getEspecificidadeRank());
        assertTrue(recurso.isDisponivel());
    }

    @Test
    void testRecursoSeedNoArgsConstructor() {
        RecursoSeed recurso = new RecursoSeed();
        assertNotNull(recurso);
    }

    @Test
    void testRecursoSeedSettersAndGetters() {
        RecursoSeed recurso = new RecursoSeed();
        recurso.setCodigoRecurso("02");
        recurso.setEspecialidade("Cirurgia");
        recurso.setUnidade("UBS");
        recurso.setEspecificidadeRank(2);
        recurso.setDisponivel(false);

        assertEquals("02", recurso.getCodigoRecurso());
        assertEquals("Cirurgia", recurso.getEspecialidade());
        assertEquals("UBS", recurso.getUnidade());
        assertEquals(2, recurso.getEspecificidadeRank());
        assertFalse(recurso.isDisponivel());
    }
}
