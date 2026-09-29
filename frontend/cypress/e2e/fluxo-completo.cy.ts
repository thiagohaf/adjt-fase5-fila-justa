describe('Fluxo Completo ConfirmaSUS (UJ-1 a UJ-6)', () => {
  const testUser = {
    username: 'regulador',
    password: 'regulador#2026'
  }

  let agendamentoId: string
  let recursoId: string

  before(() => {
    cy.log('Iniciando testes E2E do fluxo completo ConfirmaSUS')
  })

  // UJ-1: Login (Autenticação)
  describe('UJ-1: Login', () => {
    it('deve fazer login com sucesso', () => {
      cy.visit('/login')
      cy.url().should('include', '/login')

      // Preencher formulário
      cy.get('input[name="username"]').type(testUser.username)
      cy.get('input[name="password"]').type(testUser.password)

      // Submeter
      cy.get('button[type="submit"]').click()

      // Validar redirecionamento para dashboard
      cy.url().should('include', '/dashboard')
      cy.get('button').should('contain', 'Logout')
    })

    it('deve exibir erro com credenciais inválidas', () => {
      cy.visit('/login')

      cy.get('input[name="username"]').type('usuario-invalido')
      cy.get('input[name="password"]').type('senha-invalida')
      cy.get('button[type="submit"]').click()

      // Validar mensagem de erro
      cy.contains(/login falhou|credenciais inválidas|erro/i).should('be.visible')
    })
  })

  // UJ-2/UJ-3: Registrar Agendamento + Countdown
  describe('UJ-2/UJ-3: Dashboard - Agendamentos e Countdown', () => {
    beforeEach(() => {
      // Login antes de cada teste
      cy.login(testUser.username, testUser.password)
      cy.visit('/dashboard')
    })

    it('deve exibir lista de agendamentos', () => {
      cy.url().should('include', '/dashboard')
      cy.contains(/agendamentos|próximas consultas|schedule/i).should('be.visible')
    })

    it('deve navegar para página de confirmação ao clicar em agendamento', () => {
      // Aguardar carregamento da lista
      cy.get('[data-testid="agendamento-item"]', { timeout: 10000 })
        .first()
        .within(() => {
          cy.get('a, button').first().click()
        })

      // Validar que estamos em uma página de confirmação
      cy.url().should('match', /\/confirmacao\/\d+/)
      cy.contains(/confirmação|confirmar presença/i).should('be.visible')
    })

    it('deve exibir contador regressivo para agendamento próximo', () => {
      cy.get('[data-testid="agendamento-item"]')
        .first()
        .should('be.visible')

      // Verificar se há algum indicador de tempo (status, badge, etc)
      cy.get('[data-testid="agendamento-item"]').first().within(() => {
        cy.contains(/aguardando|próximo|horas/i).should('be.visible')
      })
    })
  })

  // UJ-4: Confirmação de Presença
  describe('UJ-4: Confirmação de Presença', () => {
    beforeEach(() => {
      cy.login(testUser.username, testUser.password)
      cy.visit('/dashboard')

      // Navegar para primeiro agendamento
      cy.get('[data-testid="agendamento-item"]', { timeout: 10000 })
        .first()
        .within(() => {
          cy.get('a, button').first().then(($link) => {
            const href = $link.attr('href')
            if (href) {
              agendamentoId = href.match(/\/confirmacao\/(\d+)/)?.[1] || ''
            }
          }).click()
        })

      cy.url().should('match', /\/confirmacao\/\d+/)
    })

    it('deve exibir página de confirmação com dados do agendamento', () => {
      cy.contains(/confirmação|presença|confirmar/i).should('be.visible')
      cy.contains(/data|horário|paciente|profissional/i).should('be.visible')
    })

    it('deve confirmar presença com sucesso', () => {
      // Clicar no botão de confirmação
      cy.get('button').contains(/confirmar|sim/i).click()

      // Validar mensagem de sucesso
      cy.contains(/confirmado|sucesso|presença confirmada/i, { timeout: 10000 }).should('be.visible')

      // Validar que voltou para dashboard ou mostrou estado atualizado
      cy.url().should('match', /\/dashboard|\/confirmacao/)
    })

    it('deve recusar presença com sucesso', () => {
      // Voltar para página de confirmação (se já confirmou)
      cy.visit('/dashboard')
      cy.get('[data-testid="agendamento-item"]')
        .filter(':not([data-status="CONFIRMADO"])')
        .first()
        .within(() => {
          cy.get('a, button').first().click()
        })

      cy.url().should('match', /\/confirmacao/)

      // Clicar no botão de recusa
      cy.get('button').contains(/recusar|não|cancelar/i).click()

      // Validar mensagem de sucesso
      cy.contains(/recusado|sucesso|presença recusada/i, { timeout: 10000 }).should('be.visible')
    })
  })

  // UJ-5: Sugestão e Repasse
  describe('UJ-5: Sugestão e Repasse', () => {
    beforeEach(() => {
      cy.login(testUser.username, testUser.password)
      cy.visit('/dashboard')

      // Procurar por um agendamento com sugestão de repasse
      cy.contains(/sugestão|repasse/i).should('be.visible')
    })

    it('deve exibir sugestão de repasse para agendamento não confirmado', () => {
      // Validar que há badge/indicador de sugestão
      cy.contains(/sugestão|repasse|alternativamente/i).should('be.visible')
    })

    it('deve navegar para página de repasse ao clicar em sugestão', () => {
      cy.get('[data-testid="repasse-btn"]')
        .first()
        .click()

      cy.url().should('match', /\/repasse\/\d+/)
      cy.contains(/repasse|alterar|profissional/i).should('be.visible')
    })

    it('deve confirmar repasse com sucesso', () => {
      // Se não estamos em página de repasse, navegar
      if (!cy.url().then(url => url.includes('/repasse/'))) {
        cy.visit('/dashboard')
        cy.get('[data-testid="repasse-btn"]').first().click()
      }

      cy.url().should('match', /\/repasse/)

      // Clicar em confirmar repasse
      cy.get('button').contains(/confirmar|aceitar|sim/i).click()

      // Validar mensagem de sucesso
      cy.contains(/aceito|sucesso|repasse confirmado/i, { timeout: 10000 }).should('be.visible')
    })

    it('deve recusar repasse com sucesso', () => {
      cy.visit('/dashboard')
      cy.get('[data-testid="repasse-btn"]').first().click()

      cy.url().should('match', /\/repasse/)

      // Clicar em recusar repasse
      cy.get('button').contains(/recusar|não|voltar/i).click()

      // Validar mensagem de sucesso
      cy.contains(/recusado|sucesso|repasse recusado/i, { timeout: 10000 }).should('be.visible')
    })
  })

  // UJ-6: Auditoria - Histórico de Eventos
  describe('UJ-6: Auditoria - Histórico de Eventos', () => {
    beforeEach(() => {
      cy.login(testUser.username, testUser.password)
      cy.visit('/dashboard')

      // Procurar por um agendamento processado
      cy.get('[data-testid="agendamento-item"]')
        .first()
        .within(() => {
          cy.get('a, button').first().then(($link) => {
            const href = $link.attr('href')
            agendamentoId = href?.match(/\/confirmacao\/(\d+)/)?.[1] || ''
          })
        })
    })

    it('deve exibir página de auditoria com histórico de eventos', () => {
      cy.visit(`/auditoria/${agendamentoId}`)

      cy.url().should('include', `/auditoria/${agendamentoId}`)
      cy.contains(/auditoria|histórico|eventos/i).should('be.visible')
    })

    it('deve exibir lista de eventos com tipos e datas', () => {
      cy.visit(`/auditoria/${agendamentoId}`)

      // Validar que há eventos listados
      cy.get('[data-testid="evento-item"]', { timeout: 10000 })
        .should('have.length.greaterThan', 0)
        .each(($evento) => {
          cy.wrap($evento).within(() => {
            // Validar presença de tipo e data
            cy.contains(/notificação|confirmação|recusa|sugestão|repasse|liberação/i).should('exist')
            cy.contains(/\d{2}\/\d{2}\/\d{4}|\d{2}:\d{2}/).should('exist')
          })
        })
    })

    it('deve filtrar eventos por tipo', () => {
      cy.visit(`/auditoria/${agendamentoId}`)

      // Selecionar filtro
      cy.get('[data-testid="filtro-tipo"]').first().click()

      // Validar que a lista foi filtrada
      cy.get('[data-testid="evento-item"]').should('have.length.greaterThan', 0)
    })

    it('deve exibir motivo quando presente no evento', () => {
      cy.visit(`/auditoria/${agendamentoId}`)

      // Procurar por evento com motivo
      cy.get('[data-testid="evento-item"]').each(($evento) => {
        cy.wrap($evento).within(() => {
          cy.get('[data-testid="evento-motivo"]').then(($motivo) => {
            if ($motivo.length > 0) {
              cy.wrap($motivo).should('be.visible')
            }
          })
        })
      })
    })

    it('deve exibir estado "vazio" quando nenhum evento disponível', () => {
      // Usar um agendamento ID que não tem eventos (ou criar novo)
      cy.visit('/auditoria/999999')

      cy.contains(/nenhum evento|sem histórico|vazio/i).should('be.visible')
    })

    it('deve exibir estado "erro" ao falhar requisição', () => {
      cy.intercept('GET', '/v1/auditoria/agendamento/*', {
        statusCode: 500,
        body: { message: 'Internal Server Error' }
      })

      cy.visit(`/auditoria/${agendamentoId}`)

      cy.contains(/erro|falha|tente novamente/i).should('be.visible')
    })

    it('deve exibir estado "loading" durante carregamento', () => {
      cy.intercept('GET', '/v1/auditoria/agendamento/*', (req) => {
        req.reply((res) => {
          res.delay(2000) // Delay para ver o spinner
          res.send()
        })
      })

      cy.visit(`/auditoria/${agendamentoId}`)

      // Validar spinner ou loading state
      cy.contains(/carregando|loading/i, { timeout: 3000 }).should('be.visible')
    })
  })

  // Testes de integração ponta-a-ponta
  describe('Integração Ponta-a-Ponta', () => {
    it('deve completar fluxo completo: login → agendamento → confirmação → auditoria', () => {
      // 1. Login
      cy.visit('/login')
      cy.get('input[name="username"]').type(testUser.username)
      cy.get('input[name="password"]').type(testUser.password)
      cy.get('button[type="submit"]').click()
      cy.url().should('include', '/dashboard')

      // 2. Dashboard - ver agendamentos
      cy.contains(/agendamentos|consultas/i).should('be.visible')
      cy.get('[data-testid="agendamento-item"]', { timeout: 10000 }).should('have.length.greaterThan', 0)

      // 3. Navegar para confirmação
      cy.get('[data-testid="agendamento-item"]').first().within(() => {
        cy.get('a, button').first().then(($link) => {
          const href = $link.attr('href')
          agendamentoId = href?.match(/\/confirmacao\/(\d+)/)?.[1] || ''
        }).click()
      })

      cy.url().should('match', /\/confirmacao\/\d+/)
      cy.contains(/confirmação|presença/i).should('be.visible')

      // 4. Confirmar presença
      cy.get('button').contains(/confirmar|sim/i).click()
      cy.contains(/confirmado|sucesso/i, { timeout: 10000 }).should('be.visible')

      // 5. Voltar ao dashboard
      cy.visit('/dashboard')

      // 6. Navegar para auditoria
      cy.visit(`/auditoria/${agendamentoId}`)
      cy.url().should('include', `/auditoria/${agendamentoId}`)
      cy.contains(/auditoria|histórico/i).should('be.visible')

      // 7. Validar histórico com evento de confirmação
      cy.get('[data-testid="evento-item"]', { timeout: 10000 }).should('have.length.greaterThan', 0)
    })

    it('deve validar transições de estado corretas', () => {
      cy.login(testUser.username, testUser.password)

      // Coletar agendamento em estado AGUARDANDO_JANELA
      cy.visit('/dashboard')
      cy.get('[data-testid="agendamento-item"]')
        .first()
        .then(($item) => {
          const status = $item.data('status') || $item.text()
          expect(status).to.include.oneOf([
            'AGUARDANDO_JANELA',
            'CONFIRMADO',
            'LIBERADO',
            'REPASSE_AGUARDANDO',
            'REPASSE_ACEITO',
            'REPASSE_RECUSADO'
          ])
        })
    })
  })
})

// Adicionar comando customizado para login
Cypress.Commands.add('login', (username: string, password: string) => {
  cy.visit('/login')
  cy.get('input[name="username"]').type(username)
  cy.get('input[name="password"]').type(password)
  cy.get('button[type="submit"]').click()
  cy.url().should('include', '/dashboard')
})

declare global {
  namespace Cypress {
    interface Chainable {
      login(username: string, password: string): Chainable<void>
    }
  }
}
