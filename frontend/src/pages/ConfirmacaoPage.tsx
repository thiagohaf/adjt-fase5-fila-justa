import { useParams } from 'react-router-dom'

export default function ConfirmacaoPage() {
  const { agendamentoId } = useParams<{ agendamentoId: string }>()

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <div className="max-w-2xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">Confirmação de Presença</h1>
        <p className="text-gray-600">Agendamento: {agendamentoId}</p>
        {/* UJ-1: Paciente confirma presença */}
      </div>
    </div>
  )
}
