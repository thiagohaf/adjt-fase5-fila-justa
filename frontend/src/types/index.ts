export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
}

export interface AuthUser {
  username: string
  role: string
}

export interface Agendamento {
  id: number
  agendamentoId: string
  pacienteId: number
  recursoId: string
  dataHoraAgendamento: string
  status: 'AGUARDANDO_JANELA' | 'AGUARDANDO_CONFIRMACAO' | 'CONFIRMADO' | 'LIBERADO'
  criadoEm: string
  janelaAbreEm?: string
  janelaExpiraEm?: string
  motivoLiberacao?: 'RECUSA' | 'NAO_CONFIRMADO'
}

export interface Recurso {
  recursoId: string
  codigoRecurso: string
  especificidadeRank: number
  disponivel: boolean
  especialidade?: string
  unidade?: string
}

export interface Confirmacao {
  agendamentoId: string
  status: 'CONFIRMADO' | 'RECUSADO' | 'NAO_CONFIRMADO'
  registradoEm: string
}

export interface SugestaoRepasse {
  id: string
  agendamentoLiberadoId: string
  recursoId: string
  pacienteId: string
  pacienteName?: string
  criadoEm: string
  status: 'PENDENTE' | 'CONFIRMADA' | 'RECUSADA'
}

export interface RepasseConfirmado {
  id: string
  sugestaoRepasseId: string
  agendamentoId: string
  pacienteId: string
  confirmedAt: string
}

export interface RegistroAuditoria {
  id: string
  agendamentoId?: string
  pacienteId?: string
  tipoEvento: string
  motivo?: string
  timestamp: string
  usuario: string
  detalhes?: Record<string, unknown>
}

export interface Fila {
  agendamentos: Agendamento[]
  total: number
}
