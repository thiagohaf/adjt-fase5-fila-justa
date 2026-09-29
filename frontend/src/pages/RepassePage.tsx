import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import api from '../services/api'
import RecusarSugestaoModal from '../components/RecusarSugestaoModal'
import Toast from '../components/Toast'
import RecursoNome from '../components/RecursoNome'

interface SugestaoRecursoResponse {
  recursoId: string
  pacienteId: number | null
}

export default function RepassePage() {
  const { recursoId } = useParams<{ recursoId: string }>()
  const navigate = useNavigate()

  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isConfirmingRepasse, setIsConfirmingRepasse] = useState(false)
  const [isRecusandoSugestao, setIsRecusandoSugestao] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [showSuccessToast, setShowSuccessToast] = useState(false)

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

  const handleConfirmarRepasse = async () => {
    if (!recursoId || !sugestao?.pacienteId) return

    setIsConfirmingRepasse(true)
    setErrorMessage(null)

    try {
      await api.post(`/v1/recursos/${recursoId}/alocacoes`, {
        pacienteId: sugestao.pacienteId,
      })
      setSuccessMessage('Repasse confirmado com sucesso!')
      setShowSuccessToast(true)
      setTimeout(() => {
        navigate('/dashboard', { replace: true })
      }, 2000)
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : 'Erro ao confirmar repasse'
      )
      setIsConfirmingRepasse(false)
    }
  }

  const handleRecusarSugestao = async (motivo: string) => {
    if (!recursoId || !sugestao?.pacienteId) return

    setIsRecusandoSugestao(true)
    setErrorMessage(null)

    try {
      await api.post(`/v1/recursos/${recursoId}/alocacoes/recusa`, {
        pacienteId: sugestao.pacienteId,
        motivo,
      })
      setIsModalOpen(false)
      setSuccessMessage('Sugestão recusada com sucesso!')
      setShowSuccessToast(true)
      setTimeout(() => {
        navigate('/dashboard', { replace: true })
      }, 2000)
    } catch (err) {
      setErrorMessage(
        err instanceof Error ? err.message : 'Erro ao recusar sugestão'
      )
      setIsRecusandoSugestao(false)
    }
  }

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
                Recurso
              </label>
              <p className="text-gray-900 text-lg">
                <RecursoNome recursoId={sugestao.recursoId} />
              </p>
            </div>
          </div>

          <div className="mt-6 pt-6 border-t border-gray-200">
            <p className="text-sm text-gray-600">
              Este paciente é o próximo na fila de espera por ordem de chegada.
            </p>
          </div>
        </div>

        {/* Mensagem de Erro */}
        {errorMessage && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-6">
            <p className="text-red-800 font-semibold mb-2">Erro:</p>
            <p className="text-red-700">{errorMessage}</p>
          </div>
        )}

        {/* Botões de Ação */}
        <div className="flex gap-4 justify-center">
          <button
            onClick={handleConfirmarRepasse}
            disabled={isConfirmingRepasse || isRecusandoSugestao}
            className="flex-1 bg-green-600 hover:bg-green-700 text-white font-semibold py-3 px-6 rounded transition disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            {isConfirmingRepasse && (
              <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white" />
            )}
            {isConfirmingRepasse ? 'Confirmando...' : '✓ Confirmar Repasse'}
          </button>

          <button
            onClick={() => setIsModalOpen(true)}
            disabled={isConfirmingRepasse || isRecusandoSugestao}
            className="flex-1 bg-red-600 hover:bg-red-700 text-white font-semibold py-3 px-6 rounded transition disabled:opacity-50 disabled:cursor-not-allowed"
          >
            ✕ Recusar Sugestão
          </button>
        </div>
      </div>

      <RecusarSugestaoModal
        isOpen={isModalOpen}
        isLoading={isRecusandoSugestao}
        onConfirm={handleRecusarSugestao}
        onCancel={() => setIsModalOpen(false)}
      />

      <Toast
        message={successMessage || ''}
        type="success"
        isVisible={showSuccessToast}
        onClose={() => setShowSuccessToast(false)}
        autoCloseDuration={2000}
      />
    </div>
  )
}
