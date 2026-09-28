import { useCountdown } from '../hooks/useCountdown'

interface CountdownDisplayProps {
  expiryTime: string | Date
}

export default function CountdownDisplay({ expiryTime }: CountdownDisplayProps) {
  const { minutes, seconds, isExpired } = useCountdown(expiryTime)

  const isUrgent = minutes < 1 && !isExpired
  const displayTime = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`

  return (
    <div className="text-center">
      <p className="text-sm text-gray-600 mb-3">Tempo Disponível</p>
      <div
        className={`text-5xl font-bold font-mono mb-2 transition-all ${
          isExpired
            ? 'text-gray-500'
            : isUrgent
              ? 'text-red-600 animate-pulse'
              : 'text-blue-600'
        }`}
      >
        {displayTime}
      </div>
      {isExpired && (
        <p className="text-sm text-gray-600 font-semibold">Tempo Expirado</p>
      )}
      {isUrgent && (
        <p className="text-sm text-red-600 font-semibold">⚠️ Menos de 1 minuto!</p>
      )}
    </div>
  )
}
