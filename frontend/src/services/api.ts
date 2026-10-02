import axios, { AxiosError, AxiosInstance } from 'axios'

const API_BASE_URL = ''

class ApiClient {
  private client: AxiosInstance

  constructor() {
    this.client = axios.create({
      baseURL: API_BASE_URL,
      headers: {
        'Content-Type': 'application/json',
      },
    })

    this.client.interceptors.request.use((config) => {
      const token = localStorage.getItem('auth_token')
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
      return config
    })

    this.client.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        if (error.response?.status === 401) {
          localStorage.removeItem('auth_token')
          window.location.href = '/login'
        }
        return Promise.reject(error)
      }
    )
  }

  get<T>(url: string) {
    return this.client.get<T>(url)
  }

  post<T>(url: string, data?: unknown) {
    return this.client.post<T>(url, data)
  }

  put<T>(url: string, data?: unknown) {
    return this.client.put<T>(url, data)
  }

  patch<T>(url: string, data?: unknown) {
    return this.client.patch<T>(url, data)
  }

  delete<T>(url: string) {
    return this.client.delete<T>(url)
  }
}

/** Extrai a mensagem legivel de um erro axios (RFC 7807 `detail`/`title`) ou generico. */
export function mensagemDeErro(err: unknown, padrao: string): string {
  if (axios.isAxiosError(err)) {
    const corpo = err.response?.data as { detail?: string; title?: string } | undefined
    return corpo?.detail || corpo?.title || padrao
  }
  return err instanceof Error && err.message ? err.message : padrao
}

export default new ApiClient()
