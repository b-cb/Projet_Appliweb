/**
 * Rendu CSS d'une carte d'atout Tarot (1-21) ou de l'Excuse.
 * Utilisé par CardImage quand couleur === "Atout".
 */
export default function TrumpCard({ valeur, largeur = 70, dos = false }) {
  const hauteur = Math.round(largeur * (244.64 / 169.075))
  const fontSize = Math.round(largeur * 0.32)
  const fontSizeCorner = Math.round(largeur * 0.15)
  const isExcuse = valeur === 'Excuse'

  if (dos) {
    return (
      <div style={{
        width: largeur, height: hauteur,
        borderRadius: Math.round(largeur * 0.06),
        background: 'linear-gradient(135deg, #1a237e 0%, #283593 100%)',
        border: `${Math.max(1, Math.round(largeur * 0.02))}px solid #5c6bc0`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: '0 2px 6px rgba(0,0,0,0.4)',
        userSelect: 'none',
      }}>
        <span style={{ fontSize: largeur * 0.4, opacity: 0.6 }}>🂠</span>
      </div>
    )
  }

  const bg = isExcuse
    ? 'linear-gradient(135deg, #4a148c 0%, #7b1fa2 50%, #9c27b0 100%)'
    : 'linear-gradient(135deg, #b8860b 0%, #d4a017 40%, #f0c040 70%, #ffd700 100%)'

  const borderColor = isExcuse ? '#ce93d8' : '#8b6914'
  const textColor = isExcuse ? '#fff' : '#3e2000'
  const label = isExcuse ? '★' : valeur
  const subLabel = isExcuse ? 'Exc' : valeur

  return (
    <div style={{
      width: largeur,
      height: hauteur,
      borderRadius: Math.round(largeur * 0.06),
      background: bg,
      border: `${Math.max(1, Math.round(largeur * 0.025))}px solid ${borderColor}`,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      position: 'relative',
      boxShadow: `0 2px 6px rgba(0,0,0,0.35), inset 0 1px 0 rgba(255,255,255,0.2)`,
      userSelect: 'none',
      overflow: 'hidden',
    }}>
      {/* Coin haut-gauche */}
      <span style={{
        position: 'absolute', top: Math.round(largeur * 0.04), left: Math.round(largeur * 0.06),
        fontSize: fontSizeCorner, fontWeight: 'bold', color: textColor, lineHeight: 1,
      }}>{subLabel}</span>

      {/* Valeur centrale */}
      <span style={{
        fontSize: fontSize, fontWeight: 'bold', color: textColor, lineHeight: 1,
        textShadow: isExcuse ? '0 0 8px rgba(255,255,255,0.5)' : '0 1px 2px rgba(0,0,0,0.2)',
      }}>{label}</span>

      {isExcuse && (
        <span style={{ fontSize: Math.round(largeur * 0.18), color: textColor, opacity: 0.85 }}>
          Excuse
        </span>
      )}

      {/* Coin bas-droit (inversé) */}
      <span style={{
        position: 'absolute', bottom: Math.round(largeur * 0.04), right: Math.round(largeur * 0.06),
        fontSize: fontSizeCorner, fontWeight: 'bold', color: textColor, lineHeight: 1,
        transform: 'rotate(180deg)',
      }}>{subLabel}</span>
    </div>
  )
}
