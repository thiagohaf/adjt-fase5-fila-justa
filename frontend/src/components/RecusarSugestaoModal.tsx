import { useState } from 'react'

interface RecusarSugestaoModalProps {
  isOpen: boolean
  isLoading: boolean
  onConfirm: (motivo: string) => void
  onCancel: () => void
}

export default function RecusarSugestaoModal({
  isOpen,
  isLoading,
  onConfirm,
  onCancel,
}: RecusarSugestaoModalProps) {
  const [motivo, setMotivo] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (motivo.trim()) {
      onConfirm(motivo)
    }
  }

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full">
        <form onSubmit={handleSubmit}>
          <div className="p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Recusar Sugestão</h2>
            <p className="text-gray-600 mb-6">
              Por favor, descreva o motivo da recusa:
            </p>

            <textarea
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              disabled={isLoading}
              placeholder="Ex: Paciente impossibilitado de comparecer, necessita reagendamento..."
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500 disabled:bg-gray-50 disabled:text-gray-500"
              rows={4}
              required
            />
          </div>

          <div className="bg-gray-50 px-6 py-4 flex gap-3 justify-end rounded-b-lg">
            <button
              type="button"
              onClick={onCancel}
              disabled={isLoading}
              className="px-4 py-2 text-gray-700 border border-gray-300 rounded hover:bg-gray-100 disabled:opacity-50 transition"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={isLoading || !motivo.trim()}
              className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded disabled:opacity-50 disabled:cursor-not-allowed transition flex items-center gap-2"
            >
              {isLoading && <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white" />}
              {isLoading ? 'Recusando...' : 'Recusar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
