import { useQuery } from '@tanstack/react-query'
import api from '../services/api'

export interface SugestaoRecurso {
  recursoId: string
  sugestaoId: string | null
  pacienteId: number | null
  situacao: 'PENDENTE' | 'CONFIRMADA' | 'ESGOTADA' | null
}

export function useSugestaoRecurso(recursoId: string, ativo = true) {
  return useQuery({
    queryKey: ['sugestao', recursoId],
    queryFn: async () => {
      const response = await api.get<SugestaoRecurso>(`/v1/recursos/${recursoId}/sugestao`)
      return response.data
    },
    enabled: ativo && !!recursoId,
    refetchInterval: 15000,
  })
}

export default function SituacaoRepasse({ recursoId }: { recursoId: string }) {
  const { data, isPending } = useSugestaoRecurso(recursoId)

  if (isPending || !data) {
    return <span className="text-xs text-gray-400">verificando repasse...</span>
  }

  const badge = 'inline-block rounded-full px-2 py-0.5 text-xs font-semibold'
  switch (data.situacao) {
    case 'PENDENTE':
      return (
        <span className={`${badge} bg-violet-100 text-violet-800`}>
          Repasse sugerido: Paciente #{data.pacienteId}
        </span>
      )
    case 'CONFIRMADA':
      return <span className={`${badge} bg-emerald-100 text-emerald-800`}>Vaga repassada</span>
    case 'ESGOTADA':
      return <span className={`${badge} bg-gray-100 text-gray-700`}>Sem candidatos na fila</span>
    default:
      return <span className={`${badge} bg-amber-100 text-amber-800`}>Aguardando sugestão</span>
  }
}
