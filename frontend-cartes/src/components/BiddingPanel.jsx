import { useState } from 'react'

const SYMBOLES = { Coeur: '♥', Carreau: '♦', Trefle: '♣', Pique: '♠' }

export default function BiddingPanel({ etatJeu, monTour, onEncherir }) {
  const [contrat, setContrat] = useState(80)
  const [couleur, setCouleur] = useState('Coeur')

  return (
    <div className="enchere-centre">
      <div className="enchere-titre">Phase d'enchères</div>

      {etatJeu.encheres?.length > 0 && (
        <ul className="enchere-list-mini">
          {etatJeu.encheres.map(e => (
            <li key={e.id} className={e.passe ? 'e-passe' : 'e-contrat'}>
              <strong>{e.pseudoJoueur}</strong> :{' '}
              {e.passe ? 'Passe' : `${e.contrat} ${SYMBOLES[e.couleur] || ''} ${e.couleur}`}
            </li>
          ))}
        </ul>
      )}

      {monTour ? (
        <div className="enchere-actions-centre">
          <select value={contrat} onChange={e => setContrat(Number(e.target.value))}>
            {[80, 90, 100, 110, 120, 130, 140, 150, 160].map(v => (
              <option key={v} value={v}
                disabled={etatJeu.contratValeur > 0 && v <= etatJeu.contratValeur}>
                {v}
              </option>
            ))}
          </select>
          <select value={couleur} onChange={e => setCouleur(e.target.value)}>
            {['Coeur', 'Carreau', 'Trefle', 'Pique'].map(c => (
              <option key={c} value={c}>{SYMBOLES[c]} {c}</option>
            ))}
          </select>
          <button className="btn-primary btn-sm" onClick={() => onEncherir({ passe: false, contrat, couleur })}>
            Enchérir
          </button>
          <button className="btn-outline btn-sm" onClick={() => onEncherir({ passe: true })}>
            Passer
          </button>
        </div>
      ) : (
        <p className="attente">En attente de <strong>{etatJeu.tourPseudo}</strong>…</p>
      )}
    </div>
  )
}
