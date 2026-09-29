import { useQuery } from '@tanstack/react-query'
import api from '../services/api'
import { Recurso } from '../types'

interface RecursoNomeProps {
  recursoId: string
}

export default function RecursoNome({ recursoId }: RecursoNomeProps) {
  const { data, isPending } = useQuery({
    queryKey: ['recurso', recursoId],
    queryFn: async () => {
      const response = await api.get<Recurso>(`/v1/recursos/${recursoId}`)
      return response.data
    },
    staleTime: 5 * 60 * 1000,
    retry: false,
  })

  if (isPending) {
    return <span className="text-gray-400 text-xs">carregando...</span>
  }

  if (!data) {
    return (
      <span className="font-mono text-xs text-gray-400" title={recursoId}>
        {recursoId}
      </span>
    )
  }

  return (
    <span title={recursoId}>
      {data.especialidade ? `${data.especialidade} — ${data.codigoRecurso}` : data.codigoRecurso}
      {data.unidade && <span className="text-gray-400"> ({data.unidade})</span>}
    </span>
  )
}
