import axios from 'axios'
import { createContext, ReactNode, useCallback, useContext, useEffect, useState } from 'react'
import api from '../services/api'
import { AuthUser, LoginRequest, LoginResponse } from '../types'

interface LoginResult {
  success: boolean
  status?: number
}

interface AuthContextValue {
  isAuthenticated: boolean
  user: AuthUser | null
  loading: boolean
  login: (credentials: LoginRequest) => Promise<LoginResult>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function decodeUsuarioDoToken(token: string): AuthUser | null {
  try {
    const payloadBase64 = token.split('.')[1]
    const payloadJson = atob(payloadBase64.replace(/-/g, '+').replace(/_/g, '/'))
    const payload = JSON.parse(payloadJson)
    if (typeof payload.sub !== 'string') return null
    return { username: payload.sub, role: typeof payload.role === 'string' ? payload.role : '' }
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('auth_token')
    if (token) {
      const usuario = decodeUsuarioDoToken(token)
      if (usuario) {
        setUser(usuario)
        setIsAuthenticated(true)
      } else {
        localStorage.removeItem('auth_token')
      }
    }
    setLoading(false)
  }, [])

  const login = useCallback(async (credentials: LoginRequest): Promise<LoginResult> => {
    setLoading(true)
    try {
      const response = await api.post<LoginResponse>('/v1/auth/login', credentials)
      const { token } = response.data
      const usuario = decodeUsuarioDoToken(token)

      localStorage.setItem('auth_token', token)
      setUser(usuario)
      setIsAuthenticated(true)

      return { success: true }
    } catch (error) {
      const status = axios.isAxiosError(error) ? error.response?.status : undefined
      return { success: false, status }
    } finally {
      setLoading(false)
    }
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token')
    setIsAuthenticated(false)
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ isAuthenticated, user, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth deve ser usado dentro de <AuthProvider>')
  }
  return context
}
