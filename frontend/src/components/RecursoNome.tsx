import { useQuery } from '@tanstack/react-query'
import api from '../services/api'
import { Recurso } from '../types'

interface RecursoNomeProps {
  recursoId: string
  mostrarUnidade?: boolean
}

export function useRecurso(recursoId: string) {
  return useQuery({
    queryKey: ['recurso', recursoId],
    queryFn: async () => {
      const response = await api.get<Recurso>(`/v1/recursos/${recursoId}`)
      return response.data
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
  })
}

export default function RecursoNome({ recursoId, mostrarUnidade = true }: RecursoNomeProps) {
  const { data, isPending } = useRecurso(recursoId)

  if (isPending) {
    return <span className="text-gray-400 text-xs">carregando...</span>
  }

  if (!data) {
    return <span className="text-gray-400 italic">Recurso não identificado</span>
  }

  return (
    <span>
      <span className="font-medium text-gray-900">{data.codigoRecurso}</span>
      {mostrarUnidade && data.unidade && (
        <span className="block text-xs text-gray-500">{data.unidade}</span>
      )}
    </span>
  )
}
