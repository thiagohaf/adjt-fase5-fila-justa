import { useQuery } from '@tanstack/react-query'
import api from '../services/api'

export interface SugestaoRecurso {
  recursoId: string
  sugestaoId: string | null
  pacienteId: number | null
  situacao: 'PENDENTE' | 'CONFIRMADA' | 'ESGOTADA' | null
}

export const sugestaoQueryKey = (recursoId: string) => ['sugestao', recursoId] as const

export async function buscarSugestaoRecurso(recursoId: string) {
  const response = await api.get<SugestaoRecurso>(`/v1/recursos/${recursoId}/sugestao`)
  return response.data
}

export function useSugestaoRecurso(recursoId: string) {
  return useQuery({
    queryKey: sugestaoQueryKey(recursoId),
    queryFn: () => buscarSugestaoRecurso(recursoId),
    enabled: !!recursoId,
    refetchInterval: 15000,
  })
}

const BADGE = 'inline-block rounded-full px-2 py-0.5 text-xs font-semibold'

export default function SituacaoRepasse({ recursoId }: { recursoId: string }) {
  const { data, isPending, isError } = useSugestaoRecurso(recursoId)

  if (isPending) {
    return <span className="text-xs text-gray-400">verificando repasse...</span>
  }

  if (isError || !data) {
    return <span className={`${BADGE} bg-rose-100 text-rose-800`}>Repasse indisponível</span>
  }

  switch (data.situacao) {
    case 'PENDENTE':
      return (
        <span className={`${BADGE} bg-violet-100 text-violet-800`}>
          Repasse sugerido: Paciente #{data.pacienteId}
        </span>
      )
    case 'ESGOTADA':
      return <span className={`${BADGE} bg-gray-100 text-gray-700`}>Sem candidatos na fila</span>
    case 'CONFIRMADA':
      return <span className={`${BADGE} bg-emerald-100 text-emerald-800`}>Vaga repassada</span>
    default:
      return <span className={`${BADGE} bg-amber-100 text-amber-800`}>Aguardando sugestão</span>
  }
}
