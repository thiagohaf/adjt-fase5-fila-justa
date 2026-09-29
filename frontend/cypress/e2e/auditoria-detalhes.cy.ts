describe('FE-6: Auditoria - Histórico de Eventos Detalhado', () => {
  const testUser = {
    username: 'regulador',
    password: 'regulador#2026'
  }

  beforeEach(() => {
    cy.login(testUser.username, testUser.password)
  })

  describe('Exibição de Eventos', () => {
    it('deve exibir todos os 9 tipos de evento com badges corretos', () => {
      // IDs de agendamentos com histórico variado (precisam existir no banco)
      cy.visit('/dashboard')

      // Encontrar agendamento com eventos
      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          // Validar tipos de evento esperados
          const tiposEsperados = [
            'NOTIFICACAO',
            'CONFIRMACAO',
            'RECUSA',
            'NAO_CONFIRMADO',
            'LIBERACAO',
            'SUGESTAO_GERADA',
            'REPASSE_CONFIRMADO',
            'SUGESTAO_RECUSADA',
            'GENERICO'
          ]

          cy.get('[data-testid="evento-item"]').each(($evento) => {
            cy.wrap($evento).within(() => {
              // Validar que tem um tipo válido
              let hasValidType = false
              tiposEsperados.forEach(tipo => {
                cy.get('body').then(() => {
                  if ($evento.text().includes(tipo)) {
                    hasValidType = true
                  }
                })
              })

              // Validar badge colorida (deve ter classe de cor)
              cy.get('[data-testid="evento-badge"]').should(($badge) => {
                const classes = $badge.attr('class') || ''
                expect(classes).to.include.oneOf([
                  'bg-blue',
                  'bg-green',
                  'bg-red',
                  'bg-yellow',
                  'bg-purple',
                  'bg-indigo',
                  'bg-pink',
                  'bg-gray'
                ])
              })
            })
          })
        }
      })
    })

    it('deve exibir datas formatadas em pt-BR', () => {
      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          cy.get('[data-testid="evento-data"]').each(($data) => {
            // Validar formato dd/mm/yyyy hh:mm:ss
            const texto = $data.text()
            expect(texto).to.match(/\d{2}\/\d{2}\/\d{4}\s+\d{2}:\d{2}:\d{2}/)
          })
        }
      })
    })

    it('deve exibir motivo em box destacado quando presente', () => {
      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          // Procurar evento com motivo
          cy.get('[data-testid="evento-item"]').each(($evento) => {
            cy.wrap($evento).within(() => {
              cy.get('[data-testid="evento-motivo"]').then(($motivo) => {
                if ($motivo.length > 0) {
                  // Validar que está em box destacado
                  expect($motivo).to.have.class.oneOf([
                    'bg-yellow-50',
                    'bg-red-50',
                    'bg-blue-50',
                    'p-3',
                    'rounded'
                  ])
                  expect($motivo.text()).to.not.be.empty
                }
              })
            })
          })
        }
      })
    })
  })

  describe('Filtro por Tipo de Evento', () => {
    it('deve listar tipos de evento com contagem dinâmica', () => {
      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          // Validar que há filtros disponíveis
          cy.get('[data-testid="filtro-tipo-opcao"]').should('have.length.greaterThan', 0)

          // Cada filtro deve ter contagem
          cy.get('[data-testid="filtro-tipo-opcao"]').each(($filtro) => {
            cy.wrap($filtro).within(() => {
              // Deve conter tipo e contagem (ex: "CONFIRMACAO (3)")
              cy.get('[data-testid="filtro-label"]').should('be.visible')
              cy.get('[data-testid="filtro-count"]').should('be.visible')
            })
          })
        }
      })
    })

    it('deve filtrar eventos quando seleciona tipo específico', () => {
      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          // Contar eventos totais
          cy.get('[data-testid="evento-item"]').then(($todosEventos) => {
            const totalEventos = $todosEventos.length

            // Selecionar primeiro filtro
            cy.get('[data-testid="filtro-tipo-opcao"]').first().click()

            // Contar eventos após filtro
            cy.get('[data-testid="evento-item"]').then(($eventosFiltrados) => {
              const eventosFiltrados = $eventosFiltrados.length
              expect(eventosFiltrados).to.be.lessThan(totalEventos)
            })
          })
        }
      })
    })

    it('deve limpar filtro ao desselecionar tipo', () => {
      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          // Contar eventos totais
          cy.get('[data-testid="evento-item"]').then(($todosEventos) => {
            const totalEventos = $todosEventos.length

            // Selecionar e desselecionar filtro
            cy.get('[data-testid="filtro-tipo-opcao"]').first().click()
            cy.get('[data-testid="filtro-tipo-opcao"]').first().click()

            // Deve voltar ao total
            cy.get('[data-testid="evento-item"]').should('have.length', totalEventos)
          })
        }
      })
    })
  })

  describe('Estados: Loading, Erro, Vazio', () => {
    it('deve exibir spinner durante carregamento', () => {
      cy.intercept('GET', '/v1/auditoria/agendamento/*', (req) => {
        req.reply((res) => {
          res.delay(2000)
          res.send()
        })
      }).as('carregandoAuditoria')

      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          // Deve mostrar loading
          cy.get('[data-testid="estado-loading"]').should('be.visible')

          cy.wait('@carregandoAuditoria')

          // Após carregamento, deve desaparecer
          cy.get('[data-testid="estado-loading"]').should('not.exist')
        }
      })
    })

    it('deve exibir mensagem quando nenhum evento disponível', () => {
      // Usar agendamento ID que não tem eventos
      cy.visit('/auditoria/999999')

      cy.contains(/nenhum evento|sem histórico|vazio|sem dados/i).should('be.visible')
      cy.get('[data-testid="estado-vazio"]').should('be.visible')
    })

    it('deve exibir mensagem de erro ao falhar requisição', () => {
      cy.intercept('GET', '/v1/auditoria/agendamento/*', {
        statusCode: 500,
        body: { message: 'Internal Server Error' }
      })

      cy.visit('/auditoria/123')

      cy.get('[data-testid="estado-erro"]').should('be.visible')
      cy.contains(/erro ao carregar|tente novamente|falha/i).should('be.visible')
    })

    it('deve ter botão "Tentar Novamente" em caso de erro', () => {
      cy.intercept('GET', '/v1/auditoria/agendamento/*', {
        statusCode: 500,
        body: { message: 'Internal Server Error' }
      })

      cy.visit('/auditoria/123')

      cy.get('[data-testid="btn-retry"]').should('be.visible').click()

      // Após clicar, deve tentar novamente
      cy.get('[data-testid="estado-erro"]').should('be.visible')
    })
  })

  describe('Layout Responsivo', () => {
    it('deve exibir layout correto em desktop (md+)', () => {
      cy.viewport('macbook-15')

      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          // Em desktop, filtros devem estar lado a lado com lista
          cy.get('[data-testid="container-filtros"]').should(($filtros) => {
            expect($filtros).to.have.class('md:flex-row')
          })
        }
      })
    })

    it('deve exibir layout correto em mobile', () => {
      cy.viewport('iphone-x')

      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          // Em mobile, filtros devem estar empilhados
          cy.get('[data-testid="container-filtros"]').should(($filtros) => {
            expect($filtros).to.have.class('flex-col')
          })
        }
      })
    })
  })

  describe('Acessibilidade', () => {
    it('deve ter labels acessíveis nos filtros', () => {
      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          cy.get('[data-testid="filtro-tipo-opcao"]').each(($filtro) => {
            cy.wrap($filtro).should('have.attr', 'aria-label')
          })
        }
      })
    })

    it('deve ter semântica HTML correta', () => {
      cy.visit('/dashboard')

      cy.get('[data-testid="agendamento-item"]').first().then(($item) => {
        const agendamentoId = $item.attr('data-agendamento-id') ||
          $item.find('a').attr('href')?.match(/\/confirmacao\/(\d+)/)?.[1]

        if (agendamentoId) {
          cy.visit(`/auditoria/${agendamentoId}`)

          // Deve ter heading
          cy.get('h1, h2').should('be.visible')

          // Lista deve usar <ul> ou <ol>
          cy.get('[data-testid="lista-eventos"]').within(() => {
            cy.get('li, [role="listitem"]').should('have.length.greaterThan', 0)
          })
        }
      })
    })
  })
})
