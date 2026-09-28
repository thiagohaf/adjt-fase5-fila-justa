import { useState } from 'react'
import { Agendamento } from '../types'
import api from '../services/api'

interface ConfirmacaoBotoesProps {
  agendamento: Agendamento
  janelaExpirada: boolean
  onConfirmacao: () => void
}

export default function ConfirmacaoButtons({
  agendamento,
  janelaExpirada,
  onConfirmacao,
}: ConfirmacaoBotoesProps) {
  const [isConfirmando, setIsConfirmando] = useState(false)
  const [isRecusando, setIsRecusando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  const handleConfirmar = async () => {
    if (janelaExpirada) return

    setErro(null)
    setIsConfirmando(true)

    try {
      await api.post(`/v1/agendamentos/${agendamento.id}/confirmar`)
      onConfirmacao()
    } catch (err) {
      setErro(
        err instanceof Error
          ? err.message
          : 'Erro ao confirmar presença. Tente novamente.'
      )
    } finally {
      setIsConfirmando(false)
    }
  }

  const handleRecusar = async () => {
    if (janelaExpirada) return

    setErro(null)
    setIsRecusando(true)

    try {
      await api.post(`/v1/agendamentos/${agendamento.id}/recusar`)
      onConfirmacao()
    } catch (err) {
      setErro(
        err instanceof Error
          ? err.message
          : 'Erro ao recusar agendamento. Tente novamente.'
      )
    } finally {
      setIsRecusando(false)
    }
  }

  const isDisabled = janelaExpirada || isConfirmando || isRecusando

  return (
    <div className="bg-white rounded-lg shadow p-6">
      {erro && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-4">
          <p className="text-red-700 text-sm">{erro}</p>
        </div>
      )}

      {janelaExpirada && (
        <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-4">
          <p className="text-yellow-700 text-sm">
            ⚠️ A janela de confirmação expirou. Não é possível confirmar ou recusar.
          </p>
        </div>
      )}

      <div className="flex flex-col sm:flex-row gap-4 justify-center">
        <button
          onClick={handleConfirmar}
          disabled={isDisabled}
          className={`px-8 py-3 rounded font-semibold transition-all flex items-center justify-center gap-2 ${
            isDisabled
              ? 'bg-gray-300 text-gray-600 cursor-not-allowed'
              : 'bg-green-600 hover:bg-green-700 text-white'
          }`}
        >
          {isConfirmando && (
            <svg
              className="animate-spin h-5 w-5"
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
          )}
          {isConfirmando ? 'Confirmando...' : '✓ Confirmar Presença'}
        </button>

        <button
          onClick={handleRecusar}
          disabled={isDisabled}
          className={`px-8 py-3 rounded font-semibold transition-all flex items-center justify-center gap-2 ${
            isDisabled
              ? 'bg-gray-300 text-gray-600 cursor-not-allowed'
              : 'bg-red-600 hover:bg-red-700 text-white'
          }`}
        >
          {isRecusando && (
            <svg
              className="animate-spin h-5 w-5"
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
          )}
          {isRecusando ? 'Recusando...' : '✗ Recusar'}
        </button>
      </div>
    </div>
  )
}
