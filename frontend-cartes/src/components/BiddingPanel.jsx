import { useState } from 'react'

const SYMBOLES = {
  Coeur: '♥',
  Carreau: '♦',
  Trefle: '♣',
  Pique: '♠',
  'Sans-atout': 'SA',
  'Tout-atout': 'TA',
}

const COULEURS_NORMALES = ['Coeur', 'Carreau', 'Trefle', 'Pique']
const MODES_SPECIAUX = ['Sans-atout', 'Tout-atout']
const TOUTES_COULEURS = [...COULEURS_NORMALES, ...MODES_SPECIAUX]

export default function BiddingPanel({ etatJeu, monTour, onEncherir }) {
  const [contrat, setContrat] = useState(80)
  const [couleur, setCouleur] = useState('Coeur')

  const modeSpecial = MODES_SPECIAUX.includes(couleur)

  const libelleCouleur = (c) =>
    MODES_SPECIAUX.includes(c) ? `${SYMBOLES[c]} ${c}` : `${SYMBOLES[c]} ${c}`

  return (
    <div className="enchere-centre">
      <div className="enchere-titre">Phase d'enchères</div>

      {etatJeu.encheres?.length > 0 && (
        <ul className="enchere-list-mini">
          {etatJeu.encheres.map(e => (
            <li key={e.id} className={e.passe ? 'e-passe' : 'e-contrat'}>
              <strong>{e.pseudoJoueur}</strong> :{' '}
              {e.passe
                ? 'Passe'
                : MODES_SPECIAUX.includes(e.couleur)
                  ? `${e.contrat} ${SYMBOLES[e.couleur] || e.couleur} ${e.couleur}`
                  : `${e.contrat} ${SYMBOLES[e.couleur] || ''} ${e.couleur}`}
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
            <optgroup label="Couleur">
              {COULEURS_NORMALES.map(c => (
                <option key={c} value={c}>{SYMBOLES[c]} {c}</option>
              ))}
            </optgroup>
            <optgroup label="Mode spécial">
              {MODES_SPECIAUX.map(c => (
                <option key={c} value={c}>{SYMBOLES[c]} {c}</option>
              ))}
            </optgroup>
          </select>

          {modeSpecial && (
            <span className="mode-special-badge">{couleur}</span>
          )}

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
