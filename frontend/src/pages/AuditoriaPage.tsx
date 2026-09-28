import { useParams } from 'react-router-dom'

export default function AuditoriaPage() {
  const { agendamentoId } = useParams<{ agendamentoId: string }>()

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <div className="max-w-4xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">Histórico de Auditoria</h1>
        <p className="text-gray-600">Agendamento: {agendamentoId}</p>
        {/* UJ-4: Auditor investiga */}
      </div>
    </div>
  )
}
