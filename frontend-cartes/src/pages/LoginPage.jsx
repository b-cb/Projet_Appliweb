import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/AuthContext'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [pseudo, setPseudo] = useState('')
  const [motDePasse, setMotDePasse] = useState('')
  const [isInscription, setIsInscription] = useState(false)
  const [erreur, setErreur] = useState('')

  const handleSubmit = async () => {
    if (!pseudo.trim() || !motDePasse.trim()) return
    const result = await login(pseudo.trim(), motDePasse, isInscription)
    if (result.ok) {
      navigate('/lobby')
    } else {
      setErreur(result.erreur)
    }
  }

  return (
    <div className="app">
      <div className="login-card">
        <div className="login-logo">♠ ♥ ♦ ♣</div>
        <h1>Belote ENSEEIHT</h1>
        <p className="subtitle">Jeu de cartes en ligne</p>
        <input
          type="text"
          placeholder="Pseudo (3-20 caractères)"
          value={pseudo}
          onChange={e => setPseudo(e.target.value)}
        />
        <input
          type="password"
          placeholder="Mot de passe"
          value={motDePasse}
          onChange={e => setMotDePasse(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && handleSubmit()}
        />
        <button className="btn-primary" onClick={handleSubmit}>
          {isInscription ? "S'inscrire" : 'Se connecter'}
        </button>
        <button className="btn-small btn-outline" onClick={() => setIsInscription(v => !v)}>
          {isInscription ? 'Déjà un compte ? Se connecter' : "Pas de compte ? S'inscrire"}
        </button>
        {erreur && <div className="flash">{erreur}</div>}
      </div>
    </div>
  )
}
