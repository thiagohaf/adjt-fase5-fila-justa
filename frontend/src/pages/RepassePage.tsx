import { useParams } from 'react-router-dom'

export default function RepassePage() {
  const { recursoId } = useParams<{ recursoId: string }>()

  return (
    <div className="min-h-screen bg-gray-50 py-12 px-4">
      <div className="max-w-2xl mx-auto">
        <h1 className="text-3xl font-bold mb-6">Decisão de Repasse</h1>
        <p className="text-gray-600">Recurso: {recursoId}</p>
        {/* UJ-3: Gestor decide o repasse */}
      </div>
    </div>
  )
}
