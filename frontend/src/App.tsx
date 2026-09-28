import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './hooks/useAuth'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import ConfirmacaoPage from './pages/ConfirmacaoPage'
import RepassePage from './pages/RepassePage'
import AuditoriaPage from './pages/AuditoriaPage'
import ProtectedRoute from './components/ProtectedRoute'

function App() {
  const { isAuthenticated } = useAuth()

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={isAuthenticated ? <Navigate to="/dashboard" /> : <Navigate to="/login" />}
      />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/confirmacao/:agendamentoId"
        element={
          <ProtectedRoute>
            <ConfirmacaoPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/repasse/:recursoId"
        element={
          <ProtectedRoute>
            <RepassePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/auditoria/:agendamentoId"
        element={
          <ProtectedRoute>
            <AuditoriaPage />
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

export default App
