import { useAuth } from '../hooks/useAuth'

export default function DashboardPage() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center h-16">
            <h1 className="text-xl font-bold">ConfirmaSUS</h1>
            <div className="flex items-center space-x-4">
              <span className="text-gray-700">{user?.username}</span>
              <button
                onClick={logout}
                className="px-4 py-2 text-sm font-medium text-white bg-red-600 hover:bg-red-700 rounded-md"
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
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-white p-6 rounded-lg shadow">
              <h3 className="text-gray-500 text-sm font-medium">Agendamentos Pendentes</h3>
              <p className="mt-2 text-3xl font-bold text-gray-900">0</p>
            </div>
            <div className="bg-white p-6 rounded-lg shadow">
              <h3 className="text-gray-500 text-sm font-medium">Vagas Liberadas</h3>
              <p className="mt-2 text-3xl font-bold text-gray-900">0</p>
            </div>
            <div className="bg-white p-6 rounded-lg shadow">
              <h3 className="text-gray-500 text-sm font-medium">Sugestões Pendentes</h3>
              <p className="mt-2 text-3xl font-bold text-gray-900">0</p>
            </div>
            <div className="bg-white p-6 rounded-lg shadow">
              <h3 className="text-gray-500 text-sm font-medium">Confirmados Hoje</h3>
              <p className="mt-2 text-3xl font-bold text-gray-900">0</p>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
