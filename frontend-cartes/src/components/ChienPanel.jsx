/**
 * Panel de gestion du chien et de l'écart.
 *
 * Phase CHIEN (Petite / Garde) :
 *   - Le chien est montré à tous (zone informative)
 *   - Le preneur sélectionne N cartes à écarter DANS SA MAIN COMPLÈTE
 *     (main d'origine + cartes du chien fusionnées côté backend dans maMain)
 *   - Règles : pas de bouts (Petit/21/Excuse), pas de Rois
 *
 * Phase CHIEN_VU (Garde sans) :
 *   - Le chien est montré à tous, pas d'écart possible
 *   - Seul le preneur peut confirmer
 */
import { useState } from 'react'
import CardImage from './CardImage'

export default function ChienPanel({ etatJeu, onEcarter }) {
  const [selectionnes, setSelectionnes] = useState([])

  const chien     = etatJeu.chien      || []
  const main      = etatJeu.maMain     || []
  const tailleEcart = chien.length          // 6 (3j/4j) ou 3 (5j)
  const gardeSans = etatJeu.enchereType === 'GARDE_SANS'
  const estPreneur = etatJeu.estPreneur

  const toggleCarte = (carteId) => {
    setSelectionnes(prev => {
      if (prev.includes(carteId)) return prev.filter(id => id !== carteId)
      if (prev.length >= tailleEcart) return prev   // cap
      return [...prev, carteId]
    })
  }

  const handleValider = () => {
    if (gardeSans) { onEcarter([]); return }
    if (selectionnes.length !== tailleEcart) return
    onEcarter(selectionnes)
  }

  return (
    <div className="chien-overlay">
      <div className="chien-panel">

        {/* ── Zone chien (visible à tous) ── */}
        <div className="chien-titre">
          Le chien&nbsp;— {tailleEcart} carte{tailleEcart > 1 ? 's' : ''}
          {gardeSans && <span className="chien-contrat-label"> (Garde sans — vue seule)</span>}
        </div>

        <div className="chien-cartes">
          {chien.map(c => (
            <div key={c.id} className="carte-svg inactive chien-carte-info">
              <CardImage carte={c} largeur={62} />
            </div>
          ))}
        </div>

        {/* ── Sélection de l'écart (preneur, Petite/Garde uniquement) ── */}
        {estPreneur && !gardeSans && (
          <>
            <div className="chien-instruction">
              Sélectionnez <strong>{tailleEcart}</strong> cartes à écarter dans votre main
              <br />
              <span className="chien-regle">Interdit : bouts (Petit · 21 · Excuse) et Rois</span>
            </div>

            <div className="chien-main-selection">
              {main.map(c => {
                const sel      = selectionnes.includes(c.id)
                const sature   = !sel && selectionnes.length >= tailleEcart
                return (
                  <button
                    key={c.id}
                    className={`carte-svg chien-carte ${sel ? 'chien-carte-sel' : ''} ${sature ? 'inactive' : 'jouable'}`}
                    onClick={() => toggleCarte(c.id)}
                    title={sel ? "Retirer de l'écart" : sature ? 'Écart complet' : 'Écarter cette carte'}
                  >
                    <CardImage carte={c} largeur={55} />
                    {sel && <span className="chien-sel-badge">✓</span>}
                  </button>
                )
              })}
            </div>

            <div className="chien-actions">
              <span className="chien-count">
                {selectionnes.length}&nbsp;/&nbsp;{tailleEcart} sélectionnée{selectionnes.length > 1 ? 's' : ''}
              </span>
              <button
                className="btn-primary"
                disabled={selectionnes.length !== tailleEcart}
                onClick={handleValider}
              >
                Valider l'écart
              </button>
            </div>
          </>
        )}

        {/* ── Confirmer sans écart (Garde sans) ── */}
        {estPreneur && gardeSans && (
          <div className="chien-actions" style={{ justifyContent: 'center', marginTop: 16 }}>
            <button className="btn-primary" onClick={handleValider}>
              Confirmer (pas d'écart)
            </button>
          </div>
        )}

        {/* ── Non-preneur : attente ── */}
        {!estPreneur && (
          <p className="attente" style={{ marginTop: 14 }}>
            En attente de l'écart du preneur…
          </p>
        )}
      </div>
    </div>
  )
}
