const BASE = `http://${window.location.hostname}:8080/api`

function headers(token, json = true) {
  const h = {}
  if (json) h['Content-Type'] = 'application/json'
  if (token) h['Authorization'] = `Bearer ${token}`
  return h
}

// Auth
export async function inscrire(pseudo, motDePasse) {
  const res = await fetch(`${BASE}/auth/inscrire`, {
    method: 'POST',
    headers: headers(null),
    body: JSON.stringify({ pseudo, motDePasse })
  })
  return { ok: res.ok, data: await res.json() }
}

export async function connexion(pseudo, motDePasse) {
  const res = await fetch(`${BASE}/auth/connexion`, {
    method: 'POST',
    headers: headers(null),
    body: JSON.stringify({ pseudo, motDePasse })
  })
  return { ok: res.ok, data: await res.json() }
}

// Parties
export async function fetchParties(token) {
  const res = await fetch(`${BASE}/parties`, { headers: headers(token) })
  return res.ok ? res.json() : []
}

export async function fetchPartie(token, partieId) {
  const res = await fetch(`${BASE}/partie/${partieId}`, { headers: headers(token) })
  return res.ok ? res.json() : null
}

export async function creerPartie(token, options = {}) {
  const { typeJeu = 'COINCHE', nbJoueurs = 4, maxDonnes = 0, maxPoints = 0 } = options
  const res = await fetch(`${BASE}/partie/creer`, {
    method: 'POST',
    headers: headers(token),
    body: JSON.stringify({ typeJeu, nbJoueurs, maxDonnes, maxPoints })
  })
  return { ok: res.ok, data: await res.json() }
}

export async function creerPartieAvecBots(token, utilisateurId, options = {}) {
  const { typeJeu = 'COINCHE', nbJoueurs = 4, maxDonnes = 0, maxPoints = 0 } = options
  const res = await fetch(`${BASE}/partie/creer?avecBots=true&utilisateurId=${utilisateurId}`, {
    method: 'POST',
    headers: headers(token),
    body: JSON.stringify({ typeJeu, nbJoueurs, maxDonnes, maxPoints })
  })
  return { ok: res.ok, data: await res.json() }
}

export async function supprimerPartie(token, partieId, utilisateurId) {
  const res = await fetch(`${BASE}/partie/${partieId}?utilisateurId=${utilisateurId}`, {
    method: 'DELETE', headers: headers(token)
  })
  return { ok: res.ok, data: res.status !== 204 ? await res.json().catch(() => null) : null }
}

export async function rejoindrePartie(token, partieId, utilisateurId) {
  const res = await fetch(`${BASE}/partie/${partieId}/rejoindre?utilisateurId=${utilisateurId}`, {
    method: 'POST', headers: headers(token)
  })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

export async function demarrerPartie(token, partieId) {
  const res = await fetch(`${BASE}/partie/${partieId}/demarrer`, { method: 'POST', headers: headers(token) })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

export async function fetchJoueurs(token, partieId) {
  const res = await fetch(`${BASE}/partie/${partieId}/joueurs`, { headers: headers(token) })
  return res.ok ? res.json() : []
}

// Jeu
export async function fetchEtatJeu(token, partieId, utilisateurId) {
  const res = await fetch(`${BASE}/partie/${partieId}/etat?utilisateurId=${utilisateurId}`, {
    headers: headers(token)
  })
  return res.ok ? res.json() : null
}

export async function encherir(token, partieId, utilisateurId, body) {
  const res = await fetch(`${BASE}/partie/${partieId}/encherir?utilisateurId=${utilisateurId}`, {
    method: 'POST', headers: headers(token), body: JSON.stringify(body)
  })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

export async function jouerCarte(token, partieId, utilisateurId, carteId) {
  const res = await fetch(`${BASE}/partie/${partieId}/jouer?utilisateurId=${utilisateurId}`, {
    method: 'POST', headers: headers(token), body: JSON.stringify({ carteId })
  })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

// Chat
export async function fetchHistoriqueChat(token, partieId) {
  const res = await fetch(`${BASE}/partie/${partieId}/chat`, { headers: headers(token) })
  return res.ok ? res.json() : []
}

export async function envoyerMessage(token, partieId, utilisateurId, contenu) {
  const res = await fetch(`${BASE}/partie/${partieId}/chat?utilisateurId=${utilisateurId}`, {
    method: 'POST', headers: headers(token), body: JSON.stringify({ contenu })
  })
  return { ok: res.ok }
}

// Utilisateurs
export async function fetchUtilisateurs(token) {
  const res = await fetch(`${BASE}/utilisateurs`, { headers: headers(token) })
  return res.ok ? res.json() : []
}

// Invitations
export async function fetchInvitations(token, utilisateurId) {
  const res = await fetch(`${BASE}/invitation/recues?utilisateurId=${utilisateurId}`, {
    headers: headers(token)
  })
  return res.ok ? res.json() : []
}

export async function envoyerInvitation(token, expediteurId, destinataireId, partieId) {
  const res = await fetch(
    `${BASE}/invitation/envoyer?expediteurId=${expediteurId}&destinataireId=${destinataireId}&partieId=${partieId}`,
    { method: 'POST', headers: headers(token) }
  )
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

export async function accepterInvitation(token, invId) {
  const res = await fetch(`${BASE}/invitation/${invId}/accepter`, { method: 'POST', headers: headers(token) })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

export async function refuserInvitation(token, invId) {
  const res = await fetch(`${BASE}/invitation/${invId}/refuser`, { method: 'POST', headers: headers(token) })
  return { ok: res.ok }
}

// Tarot
export async function fetchEtatJeuTarot(token, partieId, utilisateurId) {
  const res = await fetch(`${BASE}/partie/${partieId}/tarot/etat?utilisateurId=${utilisateurId}`, {
    headers: headers(token)
  })
  return res.ok ? res.json() : null
}

export async function enchirirTarot(token, partieId, utilisateurId, typeBid) {
  const res = await fetch(`${BASE}/partie/${partieId}/tarot/encherir?utilisateurId=${utilisateurId}`, {
    method: 'POST', headers: headers(token), body: JSON.stringify({ typeBid })
  })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

export async function ecarterCartes(token, partieId, utilisateurId, carteIds) {
  const res = await fetch(`${BASE}/partie/${partieId}/tarot/ecarter?utilisateurId=${utilisateurId}`, {
    method: 'POST', headers: headers(token), body: JSON.stringify({ carteIds })
  })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

export async function jouerCarteTarot(token, partieId, utilisateurId, carteId) {
  const res = await fetch(`${BASE}/partie/${partieId}/tarot/jouer?utilisateurId=${utilisateurId}`, {
    method: 'POST', headers: headers(token), body: JSON.stringify({ carteId })
  })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

export async function appelerRoiTarot(token, partieId, utilisateurId, couleur) {
  const res = await fetch(`${BASE}/partie/${partieId}/tarot/appeler-roi?utilisateurId=${utilisateurId}`, {
    method: 'POST', headers: headers(token), body: JSON.stringify({ couleur })
  })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}

export async function declarePoigneeTarot(token, partieId, utilisateurId, type) {
  const res = await fetch(`${BASE}/partie/${partieId}/tarot/poignee?utilisateurId=${utilisateurId}`, {
    method: 'POST', headers: headers(token), body: JSON.stringify({ type })
  })
  return { ok: res.ok, data: await res.json().catch(() => null) }
}
