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

export default function BiddingPanel({ etatJeu, monTour, monEquipe, onEncherir, onCoincher }) {
  const [contrat, setContrat] = useState(80)
  const [couleur, setCouleur] = useState('Coeur')

  const modeSpecial = MODES_SPECIAUX.includes(couleur)

  // Coinche/Surcoinche logic
  const aUnContrat = etatJeu.contratValeur > 0
  const equipePreneur = etatJeu.preneurEquipe  // will be derived below if needed
  const coinche = etatJeu.coinche || 0
  // Détermine si le joueur courant est dans l'équipe du preneur.
  const jesuisPreneur = etatJeu.preneurId != null && etatJeu.monJoueurId === etatJeu.preneurId
  // Logique pour autoriser la coinche ou surcoinche selon l'équipe du joueur.
  const preneurEquipe = etatJeu.preneurEquipe || 0
  const monEquipeCoinche = etatJeu.monEquipe || monEquipe || 0
  const estEquipePreneur = preneurEquipe > 0 && monEquipeCoinche === preneurEquipe

  const peutCoincher = !monTour && aUnContrat && coinche === 0 && !estEquipePreneur
  const peutSurcoincher = !monTour && aUnContrat && coinche === 1 && estEquipePreneur

  const coincheBadge = coinche === 1 ? '⚡ COINCHÉ (×2)' : coinche === 2 ? '🔥 SURCOINCHÉ (×4)' : null

  return (
    <div className="enchere-centre">
      <div className="enchere-titre">Phase d'enchères</div>

      {coincheBadge && (
        <div style={{
          background: coinche === 2 ? 'rgba(220,40,40,0.8)' : 'rgba(220,140,0,0.8)',
          borderRadius: 8, padding: '4px 14px', textAlign: 'center',
          fontWeight: 700, color: '#fff', marginBottom: 8, fontSize: '0.9rem'
        }}>
          {coincheBadge}
        </div>
      )}

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

      {/* Coinche / Surcoinche buttons — visible hors du tour aussi */}
      {(peutCoincher || peutSurcoincher) && (
        <div style={{ marginTop: 12, display: 'flex', gap: 8, justifyContent: 'center', flexWrap: 'wrap' }}>
          {peutCoincher && (
            <button
              className="btn-primary btn-sm"
              style={{ background: 'rgba(200,120,0,0.9)', borderColor: '#c87800' }}
              onClick={() => onCoincher(false)}
              title="Doubler les points en jeu"
            >
              ⚡ Coincher (×2)
            </button>
          )}
          {peutSurcoincher && (
            <button
              className="btn-primary btn-sm"
              style={{ background: 'rgba(180,30,30,0.9)', borderColor: '#b22020' }}
              onClick={() => onCoincher(true)}
              title="Quadrupler les points en jeu"
            >
              🔥 Surcoincher (×4)
            </button>
          )}
        </div>
      )}
    </div>
  )
}
