import { useEffect } from 'react'

interface ToastProps {
  message: string
  type: 'success' | 'error'
  isVisible: boolean
  onClose: () => void
  autoCloseDuration?: number
}

export default function Toast({
  message,
  type,
  isVisible,
  onClose,
  autoCloseDuration = 4000,
}: ToastProps) {
  useEffect(() => {
    if (!isVisible) return

    const timer = setTimeout(onClose, autoCloseDuration)
    return () => clearTimeout(timer)
  }, [isVisible, autoCloseDuration, onClose])

  if (!isVisible) return null

  const bgColor = type === 'success' ? 'bg-green-600' : 'bg-red-600'
  const icon = type === 'success' ? '✓' : '✕'

  return (
    <div className="fixed bottom-4 right-4 z-40 animate-in slide-in-from-bottom-4 fade-in">
      <div className={`${bgColor} text-white rounded-lg shadow-lg p-4 flex items-center gap-3 max-w-sm`}>
        <span className="text-xl font-bold">{icon}</span>
        <p className="flex-1">{message}</p>
        <button
          onClick={onClose}
          className="ml-2 text-white hover:opacity-80 transition"
        >
          ✕
        </button>
      </div>
    </div>
  )
}
