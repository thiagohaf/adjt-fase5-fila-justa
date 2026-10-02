import { useState, useEffect } from 'react'

export interface CountdownState {
  minutes: number
  seconds: number
  isExpired: boolean
}

export function useCountdown(expiryTime: string | Date): CountdownState {
  const [state, setState] = useState<CountdownState>({
    minutes: 0,
    seconds: 0,
    isExpired: false,
  })

  useEffect(() => {
    // Handle visibility API to pause countdown when tab is not active
    const handleVisibilityChange = () => {
      // Countdown will recalculate when tab becomes visible again
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)

    const interval = setInterval(() => {
      const now = new Date()
      const expiry = new Date(expiryTime)

      if (isNaN(expiry.getTime())) {
        setState({ minutes: 0, seconds: 0, isExpired: true })
        clearInterval(interval)
        return
      }

      const diff = expiry.getTime() - now.getTime()

      if (diff <= 0) {
        setState({ minutes: 0, seconds: 0, isExpired: true })
        clearInterval(interval)
      } else {
        const minutes = Math.floor(diff / 60000)
        const seconds = Math.floor((diff % 60000) / 1000)
        setState({ minutes, seconds, isExpired: false })
      }
    }, 1000)

    return () => {
      clearInterval(interval)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [expiryTime])

  return state
}
