import { useState, useCallback } from 'react'
import { connexion, inscrire } from '../services/api'

export function useAuth() {
  const [utilisateur, setUtilisateur] = useState(null)
  const [token, setToken] = useState(localStorage.getItem('token') || null)

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

  return { utilisateur, token, login, logout }
}
