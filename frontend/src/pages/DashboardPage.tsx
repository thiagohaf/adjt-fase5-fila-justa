import { useAuth } from '../hooks/useAuth'
import { useQuery } from '@tanstack/react-query'
import api from '../services/api'
import { Agendamento } from '../types'

export default function DashboardPage() {
  const { user, logout } = useAuth()
  const { data: agendamentos = [], isPending } = useQuery({
    queryKey: ['agendamentos'],
    queryFn: async () => {
      const response = await api.get<Agendamento[]>('/v1/agendamentos')
      return response.data
    },
  })

  const agendamentosAguardando = agendamentos.filter(a => a.status === 'AGUARDANDO_CONFIRMACAO').length
  const agendamentosConfirmados = agendamentos.filter(a => a.status === 'CONFIRMADO').length
  const agendamentosLiberados = agendamentos.filter(a => a.status === 'LIBERADO').length

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
          {isPending ? (
            <div className="text-center py-12">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
              <p className="text-gray-600">Carregando dados...</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="bg-white p-6 rounded-lg shadow">
                <h3 className="text-gray-500 text-sm font-medium">Agendamentos Aguardando</h3>
                <p className="mt-2 text-3xl font-bold text-gray-900">{agendamentosAguardando}</p>
              </div>
              <div className="bg-white p-6 rounded-lg shadow">
                <h3 className="text-gray-500 text-sm font-medium">Confirmados</h3>
                <p className="mt-2 text-3xl font-bold text-gray-900">{agendamentosConfirmados}</p>
              </div>
              <div className="bg-white p-6 rounded-lg shadow">
                <h3 className="text-gray-500 text-sm font-medium">Liberados</h3>
                <p className="mt-2 text-3xl font-bold text-gray-900">{agendamentosLiberados}</p>
              </div>
              <div className="bg-white p-6 rounded-lg shadow">
                <h3 className="text-gray-500 text-sm font-medium">Total</h3>
                <p className="mt-2 text-3xl font-bold text-gray-900">{agendamentos.length}</p>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
