import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import api from '../services/api'
import { Agendamento } from '../types'
import CountdownDisplay from '../components/CountdownDisplay'
import ConfirmacaoButtons from '../components/ConfirmacaoButtons'
import RecursoNome from '../components/RecursoNome'

export default function ConfirmacaoPage() {
  const { agendamentoId } = useParams<{ agendamentoId: string }>()
  const navigate = useNavigate()
  const [acaoCompleta, setAcaoCompleta] = useState(false)

  const {
    data: agendamento,
    isPending,
    error,
  } = useQuery({
    queryKey: ['agendamento', agendamentoId],
    queryFn: async () => {
      const response = await api.get<Agendamento>(
        `/v1/agendamentos/${agendamentoId}`
      )
      return response.data
    },
    enabled: !!agendamentoId,
  })

  if (isPending) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
          <p className="text-gray-600">Carregando agendamento...</p>
        </div>
      </div>
    )
  }

  if (error || !agendamento) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4">
        <div className="max-w-2xl mx-auto">
          <div className="bg-red-50 border border-red-200 rounded-lg p-6 mb-6">
            <h2 className="text-lg font-semibold text-red-800 mb-2">
              Erro ao Carregar Agendamento
            </h2>
            <p className="text-red-700 mb-4">
              {error instanceof Error
                ? error.message
                : 'Não foi possível carregar os dados do agendamento.'}
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

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <div className="max-w-2xl mx-auto">
        <button
          onClick={() => navigate('/dashboard')}
          className="text-blue-600 hover:text-blue-800 mb-6 flex items-center gap-2"
        >
          ← Voltar para Dashboard
        </button>

        <h1 className="text-3xl font-bold mb-8">Confirmação de Presença</h1>

        {/* Detalhes do Agendamento */}
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <h2 className="text-xl font-semibold mb-4">Detalhes do Agendamento</h2>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                ID do Agendamento
              </label>
              <p className="text-gray-900 font-mono">{agendamento.id}</p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Status
              </label>
              <p className="inline-block px-3 py-1 rounded-full text-sm font-semibold bg-blue-100 text-blue-800">
                {formatStatus(agendamento.status)}
              </p>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Data e Hora
              </label>
              <p className="text-gray-900">
                {new Date(agendamento.dataHoraAgendamento).toLocaleString('pt-BR')}
              </p>
            </div>

            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Recurso
              </label>
              <p className="text-gray-900">
                <RecursoNome recursoId={agendamento.recursoId} />
              </p>
            </div>

            {agendamento.janelaExpiraEm && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Janela Expira Em
                </label>
                <p className="text-gray-900">
                  {new Date(agendamento.janelaExpiraEm).toLocaleString('pt-BR')}
                </p>
              </div>
            )}

            {agendamento.motivoLiberacao && (
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Motivo da Liberação
                </label>
                <p className="text-gray-900">{formatMotivo(agendamento.motivoLiberacao)}</p>
              </div>
            )}
          </div>
        </div>

        {/* Info Message */}
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6">
          <p className="text-blue-800">
            ℹ️ Por favor, confirme sua presença neste agendamento.
          </p>
        </div>

        {/* Countdown (Story FE-2) */}
        {agendamento.janelaExpiraEm && !acaoCompleta && (
          <div className="bg-white rounded-lg shadow p-8 mb-6">
            <CountdownDisplay expiryTime={agendamento.janelaExpiraEm} />
          </div>
        )}

        {/* Botões de Confirmação/Recusa (Story FE-3) */}
        {!acaoCompleta && (
          <ConfirmacaoButtons
            agendamento={agendamento}
            janelaExpirada={
              agendamento.janelaExpiraEm
                ? new Date(agendamento.janelaExpiraEm) <= new Date()
                : false
            }
            onConfirmacao={() => {
              setAcaoCompleta(true)
              setTimeout(() => {
                navigate('/dashboard', {
                  state: { mensagem: 'Ação registrada com sucesso!' },
                })
              }, 1500)
            }}
          />
        )}

        {/* Mensagem de Sucesso */}
        {acaoCompleta && (
          <div className="bg-green-50 border border-green-200 rounded-lg p-6 text-center">
            <div className="text-4xl mb-4">✓</div>
            <h2 className="text-xl font-semibold text-green-800 mb-2">
              Ação Registrada com Sucesso
            </h2>
            <p className="text-green-700 mb-4">
              Seu registro foi salvo. Redirecionando para o dashboard...
            </p>
          </div>
        )}
      </div>
    </div>
  )
}

function formatStatus(status: string): string {
  const statusMap: Record<string, string> = {
    AGUARDANDO_JANELA: 'Aguardando Janela',
    AGUARDANDO_CONFIRMACAO: 'Aguardando Confirmação',
    CONFIRMADO: 'Confirmado',
    LIBERADO: 'Liberado',
  }
  return statusMap[status] || status
}

function formatMotivo(motivo: string): string {
  const motivoMap: Record<string, string> = {
    RECUSA: 'Recusa do Paciente',
    NAO_CONFIRMADO: 'Não Confirmado',
  }
  return motivoMap[motivo] || motivo
}
