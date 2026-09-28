import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import api from '../services/api'

interface SugestaoRecursoResponse {
  recursoId: string
  pacienteId: number | null
}

export default function RepassePage() {
  const { recursoId } = useParams<{ recursoId: string }>()
  const navigate = useNavigate()

  const {
    data: sugestao,
    isPending,
    error,
  } = useQuery({
    queryKey: ['sugestao', recursoId],
    queryFn: async () => {
      const response = await api.get<SugestaoRecursoResponse>(
        `/v1/recursos/${recursoId}/sugestao`
      )
      return response.data
    },
    enabled: !!recursoId,
  })

  if (isPending) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
          <p className="text-gray-600">Carregando sugestão de repasse...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4">
        <div className="max-w-2xl mx-auto">
          <button
            onClick={() => navigate('/dashboard')}
            className="text-blue-600 hover:text-blue-800 mb-6 flex items-center gap-2"
          >
            ← Voltar para Dashboard
          </button>

          <div className="bg-red-50 border border-red-200 rounded-lg p-6 mb-6">
            <h2 className="text-lg font-semibold text-red-800 mb-2">
              Erro ao Carregar Sugestão
            </h2>
            <p className="text-red-700 mb-4">
              {error instanceof Error ? error.message : 'Não foi possível carregar a sugestão de repasse.'}
            </p>
            <button
              onClick={() => navigate('/dashboard')}
              className="inline-block bg-red-600 hover:bg-red-700 text-white font-semibold py-2 px-4 rounded transition"
            >
              Voltar para Dashboard
            </button>
          </div>
        </div>
      </div>
    )
  }

  if (!sugestao || sugestao.pacienteId === null) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4">
        <div className="max-w-2xl mx-auto">
          <button
            onClick={() => navigate('/dashboard')}
            className="text-blue-600 hover:text-blue-800 mb-6 flex items-center gap-2"
          >
            ← Voltar para Dashboard
          </button>

          <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-6">
            <h2 className="text-lg font-semibold text-yellow-800 mb-2">
              Nenhuma Sugestão Pendente
            </h2>
            <p className="text-yellow-700 mb-4">
              Não há pacientes disponíveis na fila de espera para este recurso.
            </p>
            <button
              onClick={() => navigate('/dashboard')}
              className="inline-block bg-yellow-600 hover:bg-yellow-700 text-white font-semibold py-2 px-4 rounded transition"
            >
              Voltar para Dashboard
            </button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <div className="max-w-2xl mx-auto">
        <button
          onClick={() => navigate('/dashboard')}
          className="text-blue-600 hover:text-blue-800 mb-6 flex items-center gap-2"
        >
          ← Voltar para Dashboard
        </button>

        <h1 className="text-3xl font-bold mb-8">Sugestão de Repasse de Vaga</h1>

        {/* Card de Sugestão */}
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <div className="bg-blue-50 border-l-4 border-blue-600 p-4 mb-6">
            <p className="text-blue-800 font-semibold">
              ✓ Próximo paciente da fila de espera disponível
            </p>
          </div>

          {/* Dados da Sugestão */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                ID do Paciente
              </label>
              <p className="text-gray-900 font-mono text-lg">{sugestao.pacienteId}</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                ID do Recurso
              </label>
              <p className="text-gray-900 font-mono text-lg">{sugestao.recursoId}</p>
            </div>
          </div>

          <div className="mt-6 pt-6 border-t border-gray-200">
            <p className="text-sm text-gray-600">
              Este paciente é o próximo na fila de espera por ordem de chegada.
            </p>
          </div>
        </div>

        {/* Mensagem de Próximos Passos */}
        <div className="bg-green-50 border border-green-200 rounded-lg p-4 text-center">
          <p className="text-green-800">
            ✓ Você está pronto para confirmar ou recusar esta sugestão.
          </p>
        </div>
      </div>
    </div>
  )
}
