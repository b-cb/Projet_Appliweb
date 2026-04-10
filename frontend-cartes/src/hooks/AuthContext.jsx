import { createContext, useContext, useState, useCallback } from 'react'
import { connexion, inscrire } from '../services/api'

const AuthContext = createContext(null)

function decodeToken(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return { id: parseInt(payload.sub), pseudo: payload.pseudo }
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const storedToken = localStorage.getItem('token')
  const [token, setToken] = useState(storedToken || null)
  const [utilisateur, setUtilisateur] = useState(
    storedToken ? decodeToken(storedToken) : null
  )

  const login = useCallback(async (pseudo, motDePasse, estInscription) => {
    const fn = estInscription ? inscrire : connexion
    const { ok, data } = await fn(pseudo, motDePasse)
    if (ok) {
      setUtilisateur(data.utilisateur)
      setToken(data.token)
      localStorage.setItem('token', data.token)
      return { ok: true }
    }
    return { ok: false, erreur: data?.erreur || "Erreur d'authentification." }
  }, [])

  const logout = useCallback(() => {
    setUtilisateur(null)
    setToken(null)
    localStorage.removeItem('token')
  }, [])

  return (
    <AuthContext.Provider value={{ utilisateur, token, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
