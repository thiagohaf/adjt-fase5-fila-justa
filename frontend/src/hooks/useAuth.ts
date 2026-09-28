import { useCallback, useState, useEffect } from 'react'
import api from '../services/api'
import { LoginRequest, LoginResponse } from '../types'

export function useAuth() {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [user, setUser] = useState<LoginResponse['user'] | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('auth_token')
    const userData = localStorage.getItem('user')

    if (token && userData) {
      setIsAuthenticated(true)
      setUser(JSON.parse(userData))
    }
    setLoading(false)
  }, [])

  const login = useCallback(async (credentials: LoginRequest) => {
    setLoading(true)
    try {
      const response = await api.post<LoginResponse>('/auth/login', credentials)
      const { token, user } = response.data

      localStorage.setItem('auth_token', token)
      localStorage.setItem('user', JSON.stringify(user))

      setIsAuthenticated(true)
      setUser(user)

      return { success: true, user }
    } catch (error) {
      console.error('Login failed:', error)
      return { success: false, error }
    } finally {
      setLoading(false)
    }
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('user')
    setIsAuthenticated(false)
    setUser(null)
  }, [])

  return {
    isAuthenticated,
    user,
    loading,
    login,
    logout,
  }
}
