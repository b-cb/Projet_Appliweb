import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './hooks/AuthContext'
import LoginPage from './pages/LoginPage'
import LobbyPage from './pages/LobbyPage'
import GamePage from './pages/GamePage'
import './App.css'

function RouteProtegee({ children }) {
  const { token } = useAuth()
  return token ? children : <Navigate to="/" replace />
}

function AppRoutes() {
  const { token } = useAuth()
  return (
    <Routes>
      <Route path="/" element={token ? <Navigate to="/lobby" replace /> : <LoginPage />} />
      <Route path="/lobby" element={<RouteProtegee><LobbyPage /></RouteProtegee>} />
      <Route path="/partie/:id" element={<RouteProtegee><GamePage /></RouteProtegee>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </AuthProvider>
  )
}
