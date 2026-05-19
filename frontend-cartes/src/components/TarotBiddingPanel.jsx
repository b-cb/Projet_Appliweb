// Panel d'enchères pour le Tarot.

const BID_ORDER = ['PETITE', 'GARDE', 'GARDE_SANS', 'GARDE_CONTRE']
const BID_LABELS = {
  PETITE: 'Petite',
  GARDE: 'Garde',
  GARDE_SANS: 'Garde sans',
  GARDE_CONTRE: 'Garde contre',
}
const BID_MULTS = { PETITE: '×1', GARDE: '×2', GARDE_SANS: '×4', GARDE_CONTRE: '×6' }

export default function TarotBiddingPanel({ etatJeu, monTour, onEncherir }) {
  const niveauActuel = etatJeu.enchereType ? BID_ORDER.indexOf(etatJeu.enchereType) : -1

  return (
    <div className="enchere-centre">
      <div className="enchere-titre">Enchères Tarot</div>

      {etatJeu.encheres?.length > 0 && (
        <ul className="enchere-list-mini">
          {etatJeu.encheres.map(e => (
            <li key={e.id} className={e.passe ? 'e-passe' : 'e-contrat'}>
              <strong>{e.pseudoJoueur}</strong> :{' '}
              {e.typeBid === 'PASSE' || e.passe
                ? 'Passe'
                : BID_LABELS[e.typeBid] || e.typeBid}
            </li>
          ))}
        </ul>
      )}

      {monTour ? (
        <div className="tarot-bid-actions">
          <button className="btn-outline btn-sm" onClick={() => onEncherir('PASSE')}>
            Passer
          </button>
          {BID_ORDER.map((bid, i) => (
            <button
              key={bid}
              className="btn-primary btn-sm tarot-bid-btn"
              disabled={i <= niveauActuel}
              onClick={() => onEncherir(bid)}
              title={`${BID_LABELS[bid]} (${BID_MULTS[bid]})`}
            >
              {BID_LABELS[bid]}
              <span className="bid-mult">{BID_MULTS[bid]}</span>
            </button>
          ))}
        </div>
      ) : (
        <p className="attente">En attente de <strong>{etatJeu.tourPseudo}</strong>…</p>
      )}
    </div>
  )
}
