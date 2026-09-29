import { Component, ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  error: Error | null
}

export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: { componentStack: string }) {
    console.error('Erro não tratado na aplicação:', error, info.componentStack)
  }

  handleReset = () => {
    localStorage.removeItem('auth_token')
    localStorage.removeItem('user')
    window.location.href = '/login'
  }

  render() {
    if (this.state.error) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50 px-4">
          <div className="max-w-md w-full bg-white rounded-lg shadow p-8 text-center">
            <h1 className="text-xl font-semibold text-red-700 mb-2">Algo deu errado</h1>
            <p className="text-gray-600 text-sm mb-6">
              Ocorreu um erro inesperado ao carregar a página. Isso costuma resolver limpando a
              sessão local.
            </p>
            <button
              onClick={this.handleReset}
              className="bg-brand-600 hover:bg-brand-700 text-white font-medium py-2 px-4 rounded-md transition"
            >
              Limpar sessão e voltar ao login
            </button>
          </div>
        </div>
      )
    }

    return this.props.children
  }
}
