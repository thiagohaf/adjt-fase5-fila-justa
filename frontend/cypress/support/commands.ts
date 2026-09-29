Cypress.Commands.add('login', (username: string, password: string) => {
  cy.visit('/login')
  cy.get('input[name="username"]').type(username)
  cy.get('input[name="password"]').type(password)
  cy.get('button[type="submit"]').click()
  cy.url().should('include', '/dashboard')
})

Cypress.Commands.add('logout', () => {
  cy.get('button').contains(/logout|sair/i).click()
  cy.url().should('include', '/login')
})

Cypress.Commands.add('navigateToAgendamento', () => {
  cy.visit('/dashboard')
  cy.get('[data-testid="agendamento-item"]', { timeout: 10000 })
    .first()
    .within(() => {
      cy.get('a, button').first().click()
    })
  cy.url().should('match', /\/confirmacao\/\d+/)
})

Cypress.Commands.add('confirmPresenca', () => {
  cy.get('button').contains(/confirmar|sim/i).click()
  cy.contains(/confirmado|sucesso/i, { timeout: 10000 }).should('be.visible')
})

Cypress.Commands.add('recusarPresenca', () => {
  cy.get('button').contains(/recusar|não/i).click()
  cy.contains(/recusado|sucesso/i, { timeout: 10000 }).should('be.visible')
})

Cypress.Commands.add('navigateToAuditoria', (agendamentoId?: string) => {
  if (agendamentoId) {
    cy.visit(`/auditoria/${agendamentoId}`)
  } else {
    cy.visit('/dashboard')
    cy.get('[data-testid="agendamento-item"]')
      .first()
      .find('a, button')
      .first()
      .invoke('attr', 'href')
      .then((href) => {
        const match = href?.match(/\/confirmacao\/(\d+)/)
        if (match?.[1]) {
          cy.visit(`/auditoria/${match[1]}`)
        }
      })
  }
})

declare global {
  namespace Cypress {
    interface Chainable {
      login(username: string, password: string): Chainable<void>
      logout(): Chainable<void>
      navigateToAgendamento(): Chainable<void>
      confirmPresenca(): Chainable<void>
      recusarPresenca(): Chainable<void>
      navigateToAuditoria(agendamentoId?: string): Chainable<void>
    }
  }
}

export {}
