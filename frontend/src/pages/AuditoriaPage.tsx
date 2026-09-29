import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import api from '../services/api'

interface DecisaoAuditoria {
  eventId: string
  agendamentoId: number
  pacienteId: number
  tipoDecisao:
    | 'NOTIFICACAO'
    | 'CONFIRMACAO'
    | 'RECUSA'
    | 'NAO_CONFIRMADO'
    | 'LIBERACAO'
    | 'SUGESTAO_GERADA'
    | 'REPASSE_CONFIRMADO'
    | 'SUGESTAO_RECUSADA'
    | 'GENERICO'
  motivo: string | null
  timestamp: string
  criadoEm: string
}

function getTipoDecisaoLabel(tipo: string): string {
  const labels: Record<string, string> = {
    NOTIFICACAO: 'Notificação',
    CONFIRMACAO: 'Confirmação',
    RECUSA: 'Recusa',
    NAO_CONFIRMADO: 'Não Confirmado',
    LIBERACAO: 'Liberação',
    SUGESTAO_GERADA: 'Sugestão Gerada',
    REPASSE_CONFIRMADO: 'Repasse Confirmado',
    SUGESTAO_RECUSADA: 'Sugestão Recusada',
    GENERICO: 'Genérico',
  }
  return labels[tipo] || tipo
}

function getTipoDecisaoBadgeColor(
  tipo: string
): 'blue' | 'green' | 'red' | 'yellow' | 'purple' | 'gray' {
  const colors: Record<
    string,
    'blue' | 'green' | 'red' | 'yellow' | 'purple' | 'gray'
  > = {
    NOTIFICACAO: 'blue',
    CONFIRMACAO: 'green',
    RECUSA: 'red',
    NAO_CONFIRMADO: 'yellow',
    LIBERACAO: 'green',
    SUGESTAO_GERADA: 'purple',
    REPASSE_CONFIRMADO: 'green',
    SUGESTAO_RECUSADA: 'red',
    GENERICO: 'gray',
  }
  return colors[tipo] || 'gray'
}

function formatarData(dataISO: string): string {
  const date = new Date(dataISO)
  return date.toLocaleString('pt-BR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

function getBadgeClasses(color: string): string {
  const baseClasses = 'px-3 py-1 rounded-full text-sm font-semibold'
  const colorClasses: Record<string, string> = {
    blue: 'bg-blue-100 text-blue-800',
    green: 'bg-green-100 text-green-800',
    red: 'bg-red-100 text-red-800',
    yellow: 'bg-yellow-100 text-yellow-800',
    purple: 'bg-purple-100 text-purple-800',
    gray: 'bg-gray-100 text-gray-800',
  }
  return `${baseClasses} ${colorClasses[color] || colorClasses['gray']}`
}

export default function AuditoriaPage() {
  const { agendamentoId } = useParams<{ agendamentoId: string }>()
  const navigate = useNavigate()
  const [filtroTipo, setFiltroTipo] = useState<string>('')

  const {
    data: eventos,
    isPending,
    error,
  } = useQuery({
    queryKey: ['auditoria', agendamentoId],
    queryFn: async () => {
      const response = await api.get<DecisaoAuditoria[]>(
        `/auditoria/agendamento/${agendamentoId}`
      )
      return response.data
    },
    enabled: !!agendamentoId,
  })

  const eventosFiltrados =
    filtroTipo && eventos
      ? eventos.filter((e) => e.tipoDecisao === filtroTipo)
      : eventos || []

  if (isPending) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4" />
          <p className="text-gray-600">Carregando histórico de auditoria...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 py-12 px-4">
        <div className="max-w-4xl mx-auto">
          <button
            onClick={() => navigate('/dashboard')}
            className="text-blue-600 hover:text-blue-800 mb-6 flex items-center gap-2"
          >
            ← Voltar para Dashboard
          </button>

          <div className="bg-red-50 border border-red-200 rounded-lg p-6">
            <h2 className="text-lg font-semibold text-red-800 mb-2">
              Erro ao Carregar Auditoria
            </h2>
            <p className="text-red-700 mb-4">
              {error instanceof Error ? error.message : 'Não foi possível carregar o histórico de auditoria.'}
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
      <div className="max-w-4xl mx-auto">
        <button
          onClick={() => navigate('/dashboard')}
          className="text-blue-600 hover:text-blue-800 mb-6 flex items-center gap-2"
        >
          ← Voltar para Dashboard
        </button>

        <div className="mb-8">
          <h1 className="text-3xl font-bold mb-2">Histórico de Auditoria</h1>
          <p className="text-gray-600">
            Agendamento: <span className="font-mono font-semibold">{agendamentoId}</span>
          </p>
        </div>

        {/* Filtro por tipo de evento */}
        <div className="bg-white rounded-lg shadow p-6 mb-8">
          <label className="block text-sm font-medium text-gray-700 mb-3">
            Filtrar por tipo de evento:
          </label>
          <div className="flex flex-wrap gap-2">
            <button
              onClick={() => setFiltroTipo('')}
              className={`px-4 py-2 rounded-lg font-medium transition ${
                filtroTipo === ''
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-200 text-gray-800 hover:bg-gray-300'
              }`}
            >
              Todos ({eventos?.length || 0})
            </button>
            {[
              'NOTIFICACAO',
              'CONFIRMACAO',
              'RECUSA',
              'NAO_CONFIRMADO',
              'LIBERACAO',
              'SUGESTAO_GERADA',
              'REPASSE_CONFIRMADO',
              'SUGESTAO_RECUSADA',
            ].map((tipo) => {
              const count = eventos?.filter((e) => e.tipoDecisao === tipo).length || 0
              return (
                <button
                  key={tipo}
                  onClick={() => setFiltroTipo(tipo)}
                  className={`px-4 py-2 rounded-lg font-medium transition ${
                    filtroTipo === tipo
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-200 text-gray-800 hover:bg-gray-300'
                  }`}
                >
                  {getTipoDecisaoLabel(tipo)} ({count})
                </button>
              )
            })}
          </div>
        </div>

        {/* Lista de eventos */}
        {eventosFiltrados.length === 0 ? (
          <div className="bg-gray-100 rounded-lg p-8 text-center">
            <p className="text-gray-600">
              {filtroTipo
                ? `Nenhum evento do tipo "${getTipoDecisaoLabel(filtroTipo)}" encontrado.`
                : 'Nenhum evento de auditoria encontrado para este agendamento.'}
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            {eventosFiltrados.map((evento) => {
              const badgeColor = getTipoDecisaoBadgeColor(evento.tipoDecisao)
              return (
                <div
                  key={evento.eventId}
                  className="bg-white rounded-lg shadow p-6 border-l-4 border-blue-600"
                >
                  <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-2">
                        <span className={getBadgeClasses(badgeColor)}>
                          {getTipoDecisaoLabel(evento.tipoDecisao)}
                        </span>
                        <span className="text-xs text-gray-500">
                          ID: {evento.eventId.substring(0, 8)}...
                        </span>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
                        <div>
                          <label className="text-gray-500 font-medium">Paciente:</label>
                          <p className="text-gray-900 font-mono">{evento.pacienteId}</p>
                        </div>
                        <div>
                          <label className="text-gray-500 font-medium">Agendamento:</label>
                          <p className="text-gray-900 font-mono">{evento.agendamentoId}</p>
                        </div>
                      </div>

                      {evento.motivo && (
                        <div className="mt-3 p-3 bg-gray-50 rounded border-l-2 border-yellow-500">
                          <label className="text-gray-500 font-medium text-sm">Motivo:</label>
                          <p className="text-gray-700 text-sm mt-1">{evento.motivo}</p>
                        </div>
                      )}
                    </div>

                    <div className="flex-shrink-0 text-right">
                      <p className="text-xs text-gray-500">Data/Hora do Evento</p>
                      <p className="text-gray-900 font-mono text-sm">
                        {formatarData(evento.timestamp)}
                      </p>
                      <p className="text-xs text-gray-400 mt-2">Registrado</p>
                      <p className="text-gray-600 font-mono text-xs">
                        {formatarData(evento.criadoEm)}
                      </p>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
