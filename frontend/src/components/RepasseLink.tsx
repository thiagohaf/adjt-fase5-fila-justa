import { Link } from 'react-router-dom'
import { useSugestaoRecurso } from './SituacaoRepasse'

/** Botão "Repasse" — só aparece enquanto há sugestão pendente para o recurso. */
export default function RepasseLink({ recursoId }: { recursoId: string }) {
  const { data } = useSugestaoRecurso(recursoId)
  if (data?.situacao !== 'PENDENTE') return null

  return (
    <Link to={`/repasse/${recursoId}`} className="btn btn-sm btn-accent">
      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M7 7h11m0 0l-3-3m3 3l-3 3M17 17H6m0 0l3-3m-3 3l3 3" />
      </svg>
      Repasse
    </Link>
  )
}
