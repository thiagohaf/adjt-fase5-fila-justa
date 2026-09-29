import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useQuery } from '@tanstack/react-query'
import api from '../services/api'
import { Agendamento } from '../types'

type StatusFiltro = 'TODOS' | Agendamento['status']

const STATUS_LABELS: Record<Agendamento['status'], string> = {
  AGUARDANDO_JANELA: 'Aguardando Janela',
  AGUARDANDO_CONFIRMACAO: 'Aguardando Confirmação',
  CONFIRMADO: 'Confirmado',
  LIBERADO: 'Liberado',
}

const STATUS_BADGE_CLASSES: Record<Agendamento['status'], string> = {
  AGUARDANDO_JANELA: 'bg-gray-100 text-gray-800',
  AGUARDANDO_CONFIRMACAO: 'bg-yellow-100 text-yellow-800',
  CONFIRMADO: 'bg-green-100 text-green-800',
  LIBERADO: 'bg-red-100 text-red-800',
}

export default function DashboardPage() {
  const { user, logout } = useAuth()
  const [filtro, setFiltro] = useState<StatusFiltro>('TODOS')

  const { data: agendamentos = [], isPending, error } = useQuery({
    queryKey: ['agendamentos'],
    queryFn: async () => {
      const response = await api.get<Agendamento[]>('/v1/agendamentos')
      return response.data
    },
    refetchInterval: 15000,
  })

  const agendamentosAguardando = agendamentos.filter(a => a.status === 'AGUARDANDO_CONFIRMACAO').length
  const agendamentosConfirmados = agendamentos.filter(a => a.status === 'CONFIRMADO').length
  const agendamentosLiberados = agendamentos.filter(a => a.status === 'LIBERADO').length

  const agendamentosFiltrados = agendamentos
    .filter(a => filtro === 'TODOS' || a.status === filtro)
    .sort((a, b) => b.id - a.id)

  const cards: { label: string; valor: number; status: StatusFiltro }[] = [
    { label: 'Aguardando Confirmação', valor: agendamentosAguardando, status: 'AGUARDANDO_CONFIRMACAO' },
    { label: 'Confirmados', valor: agendamentosConfirmados, status: 'CONFIRMADO' },
    { label: 'Liberados', valor: agendamentosLiberados, status: 'LIBERADO' },
    { label: 'Total', valor: agendamentos.length, status: 'TODOS' },
  ]

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <h1 className="text-xl font-bold text-brand-900">ConfirmaSUS</h1>
            <div className="flex items-center gap-4">
              <div className="text-right leading-tight hidden sm:block">
                <p className="text-sm font-medium text-gray-900">{user?.username}</p>
                {user?.role && <p className="text-xs text-gray-500">{user.role}</p>}
              </div>
              <button
                onClick={logout}
                className="px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded-md transition"
              >
                Sair
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <h2 className="text-2xl font-bold mb-4">Dashboard</h2>

          {error && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
              <p className="text-red-700 text-sm">
                Não foi possível carregar os agendamentos. Tente recarregar a página.
              </p>
            </div>
          )}

          {isPending ? (
            <div className="text-center py-12">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
              <p className="text-gray-600">Carregando dados...</p>
            </div>
          ) : (
            <>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                {cards.map(card => (
                  <button
                    key={card.label}
                    onClick={() => setFiltro(card.status)}
                    className={`text-left bg-white p-6 rounded-lg shadow transition ring-2 ${
                      filtro === card.status ? 'ring-blue-500' : 'ring-transparent hover:ring-gray-200'
                    }`}
                  >
                    <h3 className="text-gray-500 text-sm font-medium">{card.label}</h3>
                    <p className="mt-2 text-3xl font-bold text-gray-900">{card.valor}</p>
                  </button>
                ))}
              </div>

              <div className="bg-white rounded-lg shadow overflow-hidden">
                {agendamentosFiltrados.length === 0 ? (
                  <div className="text-center py-12 text-gray-500">
                    Nenhum agendamento {filtro !== 'TODOS' ? `com status "${STATUS_LABELS[filtro]}"` : ''} encontrado.
                  </div>
                ) : (
                  <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">ID</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Paciente</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Recurso</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Data/Hora</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                        <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Ações</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {agendamentosFiltrados.map(agendamento => (
                        <tr key={agendamento.id} className="hover:bg-gray-50">
                          <td className="px-4 py-3 text-sm text-gray-900 font-mono">{agendamento.id}</td>
                          <td className="px-4 py-3 text-sm text-gray-900">{agendamento.pacienteId}</td>
                          <td className="px-4 py-3 text-sm text-gray-500 font-mono truncate max-w-[10rem]" title={agendamento.recursoId}>
                            {agendamento.recursoId}
                          </td>
                          <td className="px-4 py-3 text-sm text-gray-500">
                            {new Date(agendamento.dataHoraAgendamento).toLocaleString('pt-BR')}
                          </td>
                          <td className="px-4 py-3 text-sm">
                            <span className={`px-2 py-1 rounded-full text-xs font-semibold ${STATUS_BADGE_CLASSES[agendamento.status]}`}>
                              {STATUS_LABELS[agendamento.status]}
                            </span>
                          </td>
                          <td className="px-4 py-3 text-sm space-x-3 whitespace-nowrap">
                            <Link
                              to={`/confirmacao/${agendamento.id}`}
                              className="text-blue-600 hover:text-blue-800 font-medium"
                            >
                              Detalhes
                            </Link>
                            <Link
                              to={`/auditoria/${agendamento.id}`}
                              className="text-gray-600 hover:text-gray-900 font-medium"
                            >
                              Auditoria
                            </Link>
                            {agendamento.status === 'LIBERADO' && (
                              <Link
                                to={`/repasse/${agendamento.recursoId}`}
                                className="text-purple-600 hover:text-purple-800 font-medium"
                              >
                                Repasse
                              </Link>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </>
          )}
        </div>
      </main>
    </div>
  )
}
