package com.confirmasus.auditoria.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditoriaExceptionHandler")
class AuditoriaExceptionHandlerTest {

    private final AuditoriaExceptionHandler handler = new AuditoriaExceptionHandler();

    @Test
    @DisplayName("handleIllegalArgumentException deve retornar HTTP 400 com mensagem")
    void testHandleIllegalArgumentException() {
        // Arrange
        IllegalArgumentException ex = new IllegalArgumentException("Argumento inválido");
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletWebRequest webRequest = new ServletWebRequest(request);

        // Act
        ResponseEntity<Object> response = handler.handleIllegalArgumentException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        Map<Object, Object> body = (Map<Object, Object>) response.getBody();
        assertEquals(400, body.get("status"));
        assertEquals("Bad Request", body.get("error"));
        assertEquals("Argumento inválido", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    @DisplayName("handleIllegalArgumentException deve capturar DecisaoAuditoria null")
    void testHandleDecisaoAuditoriaNull() {
        // Arrange
        IllegalArgumentException ex = new IllegalArgumentException("DecisaoAuditoria não pode ser null");
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletWebRequest webRequest = new ServletWebRequest(request);

        // Act
        ResponseEntity<Object> response = handler.handleIllegalArgumentException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<Object, Object> body = (Map<Object, Object>) response.getBody();
        assertEquals("DecisaoAuditoria não pode ser null", body.get("message"));
    }

    @Test
    @DisplayName("handleGlobalException deve retornar HTTP 500 para exceções genéricas")
    void testHandleGlobalException() {
        // Arrange
        Exception ex = new RuntimeException("Erro genérico");
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletWebRequest webRequest = new ServletWebRequest(request);

        // Act
        ResponseEntity<Object> response = handler.handleGlobalException(ex, webRequest);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());

        @SuppressWarnings("unchecked")
        Map<Object, Object> body = (Map<Object, Object>) response.getBody();
        assertEquals(500, body.get("status"));
        assertEquals("Internal Server Error", body.get("error"));
        assertEquals("Erro ao processar requisição", body.get("message"));
        assertNotNull(body.get("timestamp"));
    }
}
