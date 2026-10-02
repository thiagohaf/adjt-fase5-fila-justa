# Code Review Findings — PR #71 + Story 4.4b Bloqueador

**Data**: 2026-09-22  
**Contexto**: Handoff pós-merge de Story 5.2 (PR #71), investigação de achados em code-review  
**Status**: 3 fixos validados ✅ | 2 bloqueadores em Story 4.4b ❌

---

## Achados Resolvidos

### 1. RecursoClient.java:87 — UUID parsing sem exception handling
- **Problema**: `UUID.fromString(recursoIdStr)` pode lançar `NumberFormatException` se gateway retorna UUID malformado
- **Impacto**: Carga de seed-data aborta sem mensagem clara
- **Fix**: Envolver em try-catch, capturar `IllegalArgumentException`
- **Status**: ✅ FIXADO
- **Validação**: Testes seed-adapter (44/44) passam

### 2. RecursoClient.java:83 — Whitespace em isEmpty()
- **Problema**: `recursoIdStr.isEmpty()` não detecta strings com apenas espaços (`" "`)
- **Impacto**: `UUID.fromString(" ")` lança exceção
- **Fix**: Usar `.trim().isEmpty()` para validação robusta
- **Status**: ✅ FIXADO
- **Validação**: Testes seed-adapter (44/44) passam

### 3. SeedDataLoader.java:158 — Whitespace em estado desejado
- **Problema**: `estadoDesejado.isEmpty()` não detecta espaços; `.trim().equals()` não aplicado
- **Impacto**: Agendamento com estado `" "` passa validação e causa erro posterior
- **Fix**: Usar `.trim().isEmpty()` e `.trim().equals()` para validações
- **Status**: ✅ FIXADO
- **Validação**: Testes seed-adapter (44/44) passam

---

## Bloqueadores — Story 4.4b (Filtro Tipo Paciente)

### 4. DecisaoAuditoriaJpaRepository:354-355 — Arquitetura microserviços inválida
```sql
LEFT JOIN agendamento_confirmacao.agendamentos a ON d.agendamento_id = a.id
LEFT JOIN paciente.pacientes p ON d.paciente_id = p.id  -- ❌ TABELA NÃO EXISTE
```

- **Problema**: Query tenta fazer JOIN com tabela `paciente.pacientes` do microserviço **paciente-service**
- **Realidade**: Tabela não existe no banco de dados `auditoria-service`
- **Impacto**: 
  - Queries falham em runtime: `SQL error: schema "paciente" not found`
  - Aborta filtro de tipoPaciente
  - Afeta Story 4.4b completamente
- **Raiz**: Story 4.4b viola independência de microserviços ao fazer cross-service JOIN no banco
- **Status**: ❌ BLOQUEADOR — Não corrigível sem redesign

### 5. AuditoriaController:93 — Lógica de roteamento incorreta
```java
if (startDate != null || endDate != null || tipoDecisao != null || 
    statusAgendamento != null || tipoPaciente != null || limit != null || offset != null) {
    // Sempre chama consultarComFiltrosEStatusAgendamentoETipoPaciente()
    // Mesmo que tipoPaciente=null e statusAgendamento=CONFIRMADO
}
```

- **Problema**: Se qualquer parâmetro fornecido, FORÇA execução de query com JOIN inválido
- **Caso falho**: `GET /v1/auditoria/paciente/1?statusAgendamento=CONFIRMADO`
  - statusAgendamento=CONFIRMADO, tipoPaciente=null
  - Ainda tenta JOIN com paciente.pacientes
  - Query falha (schema não existe)
- **Fix necessário**: 
  - Query 4.4a (statusAgendamento-only) sem JOIN com paciente
  - Query 4.4b (tipoPaciente-only) impossível de implementar sem violação arquitectura
- **Status**: ❌ BLOQUEADOR — Requer removção de tipoPaciente

### 6. Testes de Integração — Assinaturas desatualizadas
- **Arquivo**: `AuditoriaControllerIntegrationTest.java`, `AuditoriaControllerFilterIntegrationTest.java`
- **Erro**: Chamadas a `consultarPaciente()` com assinatura antiga (sem TipoPaciente)
- **Causa**: Story 4.4b mudou assinatura, mas testes não foram atualizados
- **Status**: ❌ BUILD FAILURE — Maven compile falha
- **Consequence**: Impossível rodar testes para validar Story 4.4b

---

## Análise de Arquitetura

**Raiz Cause**: Story 4.4b tentou implementar filtro por tipoPaciente fazendo JOIN com outro microserviço no banco.

**Problema Conceptual**: 
- `auditoria-service` armazena `paciente_id` (referência externa)
- Story 4.4b tentou resolver `paciente_id` para obter `tipo_paciente` via SQL JOIN direto
- Isso é inválido em arquitetura de microserviços

**Soluções possíveis** (todas não-ideais):
1. ❌ **Remover Story 4.4b**: Mais simples, mantém independência
2. ❌ **Chamar paciente-service**: Chamada de rede em query → performance ruim
3. ❌ **Cache de tipoPaciente**: Adiciona coluna em decisao_auditoria → duplicação de dados
4. ❌ **Armazenar no evento**: Carregar tipo na criação da auditoria → design violation

**Recomendação**: Remover Story 4.4b (tipoPaciente filter) e manter Story 4.4a (statusAgendamento filter) que é arquiteturalmente válida.

---

## Decisão: Bloqueio de Story 5.3

**Story 5.3** (Lista de Espera) está **bloqueada até resolver Story 4.4b**.

**Próximos passos sugeridos**:

### Opção A: Remover tipoPaciente (Recomendado)
```
1. Remover métodos findByPacienteIdWithFiltersAndStatusAgendamentoAndTipoPaciente()
2. Remover parâmetro TipoPaciente de ConsultarAuditoriaPaciente
3. Remover parâmetro TipoPaciente de AuditoriaController
4. Atualizar testes antigos (AuditoriaControllerIntegrationTest)
5. Decidir: reverter Story 4.4b ou abrir novo PR de fix
6. Depois: Story 5.3 pode prosseguir
Tempo estimado: 1-2h
```

### Opção B: Preparar Story 5.3 em paralelo
```
1. Criar branch feature/5-3 a partir de develop (atual)
2. Implementar ListaEsperaClient + specs sem depender de auditoria
3. Quando 4.4b resolvido: integrar List de Espera
Tempo estimado: 4-6h para Story 5.3
```

---

## Sumário de Validação

| Step | Status | Evidência |
|------|--------|-----------|
| Clonar/ler código | ✅ | Arquivos lidos |
| Identificar achados | ✅ | 6 achados mapeados |
| Fixar RecursoClient | ✅ | Código editado, testes passam |
| Fixar SeedDataLoader | ✅ | Código editado, testes passam |
| Validar Story 5.1/5.2 | ✅ | `mvn test`: 44/44 passam |
| Investigar Story 4.4b | ✅ | Code review + testes compilação |
| Confirmar bloqueador | ✅ | Testes integração falham (compile error) |
| Documentar | ✅ | Este documento |

---

**Próxima ação recomendada**: Decidir entre Opção A (remove 4.4b) ou Opção B (paralelo 5.3).
