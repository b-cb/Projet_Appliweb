package fr.enseeiht.jeux.tarot;

import fr.enseeiht.jeux.dto.CarteDTO;
import fr.enseeiht.jeux.dto.EnchereDTO;

import java.util.List;
import java.util.Map;

// Vue de l'état du jeu Tarot pour un joueur donné.
public class EtatTarotDTO {

    private Long   partieId;
    private String statut;       // EN_ENCHERE, EN_JEU, TERMINEE
    private String phaseJeu;     // null (enchères) | CHIEN | CHIEN_VU | APPEL_ROI | JEU
    private int    numPliCourant;

    // enchère gagnante
    private String enchereType;  // PETITE | GARDE | GARDE_SANS | GARDE_CONTRE
    private int    multiplicateur;

    // scores de la donne en cours (×2 pour les demi-points)
    private int scoreA;
    private int scoreB;

    // joueur dont c'est le tour
    private Long   tourJoueurId;
    private String tourPseudo;

    // données du joueur connecté
    private List<CarteDTO> maMain;
    private Long           monJoueurId;
    private int            monEquipe;      // 0=inconnu, 1=preneur/partenaire, 2=défenseur
    private boolean        estPreneur;
    private boolean        estPartenaire;  // vrai pour le partenaire en 5 joueurs après révélation

    // 5 joueurs — appel du Roi
    private String appelRoi;         // couleur appelée, visible après l'appel
    private String pseudoPartenaire; // null tant que le partenaire n'est pas révélé

    private List<CarteDTO> chien;    // visible en phase CHIEN et CHIEN_VU

    private List<CartePliDTO> pliCourant;
    private List<CartePliDTO> dernierPli;
    private int               dernierPliGagnantEquipe;

    private List<EnchereDTO> encheres;

    // progression du score pendant le jeu
    private int boutsPreneur;
    private int pointsPreneurX2;    // diviser par 2 pour les points réels
    private int seuilCourant;

    // multi-manche
    private int donneActuelle;
    private int maxDonnes;
    private int maxPoints;
    private int scoreGlobalA;
    private int scoreGlobalB;

    private String poigneeDeclaree; // null | SIMPLE | DOUBLE | TRIPLE

    // true si un joueur a le Petit comme seul atout (peut annuler la donne)
    private boolean petitSecDetecte;
    // true uniquement pour le joueur concerné
    private boolean monPetitEstSec;

    // scores individuels cumulés : joueurId → scorePartie
    private Map<Long, Integer> scoresJoueurs;

    // résultat disponible quand statut = TERMINEE
    private ResultatTarotDTO resultat;

    public EtatTarotDTO() {}

    public Long getPartieId() { return partieId; }
    public void setPartieId(Long partieId) { this.partieId = partieId; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getPhaseJeu() { return phaseJeu; }
    public void setPhaseJeu(String phaseJeu) { this.phaseJeu = phaseJeu; }

    public int getNumPliCourant() { return numPliCourant; }
    public void setNumPliCourant(int numPliCourant) { this.numPliCourant = numPliCourant; }

    public String getEnchereType() { return enchereType; }
    public void setEnchereType(String enchereType) { this.enchereType = enchereType; }

    public int getMultiplicateur() { return multiplicateur; }
    public void setMultiplicateur(int multiplicateur) { this.multiplicateur = multiplicateur; }

    public int getScoreA() { return scoreA; }
    public void setScoreA(int scoreA) { this.scoreA = scoreA; }

    public int getScoreB() { return scoreB; }
    public void setScoreB(int scoreB) { this.scoreB = scoreB; }

    public Long getTourJoueurId() { return tourJoueurId; }
    public void setTourJoueurId(Long tourJoueurId) { this.tourJoueurId = tourJoueurId; }

    public String getTourPseudo() { return tourPseudo; }
    public void setTourPseudo(String tourPseudo) { this.tourPseudo = tourPseudo; }

    public List<CarteDTO> getMaMain() { return maMain; }
    public void setMaMain(List<CarteDTO> maMain) { this.maMain = maMain; }

    public Long getMonJoueurId() { return monJoueurId; }
    public void setMonJoueurId(Long monJoueurId) { this.monJoueurId = monJoueurId; }

    public int getMonEquipe() { return monEquipe; }
    public void setMonEquipe(int monEquipe) { this.monEquipe = monEquipe; }

    public boolean isEstPreneur() { return estPreneur; }
    public void setEstPreneur(boolean estPreneur) { this.estPreneur = estPreneur; }

    public boolean isEstPartenaire() { return estPartenaire; }
    public void setEstPartenaire(boolean estPartenaire) { this.estPartenaire = estPartenaire; }

    public String getAppelRoi() { return appelRoi; }
    public void setAppelRoi(String appelRoi) { this.appelRoi = appelRoi; }

    public String getPseudoPartenaire() { return pseudoPartenaire; }
    public void setPseudoPartenaire(String pseudoPartenaire) { this.pseudoPartenaire = pseudoPartenaire; }

    public List<CarteDTO> getChien() { return chien; }
    public void setChien(List<CarteDTO> chien) { this.chien = chien; }

    public List<CartePliDTO> getPliCourant() { return pliCourant; }
    public void setPliCourant(List<CartePliDTO> pliCourant) { this.pliCourant = pliCourant; }

    public List<CartePliDTO> getDernierPli() { return dernierPli; }
    public void setDernierPli(List<CartePliDTO> dernierPli) { this.dernierPli = dernierPli; }

    public int getDernierPliGagnantEquipe() { return dernierPliGagnantEquipe; }
    public void setDernierPliGagnantEquipe(int v) { this.dernierPliGagnantEquipe = v; }

    public List<EnchereDTO> getEncheres() { return encheres; }
    public void setEncheres(List<EnchereDTO> encheres) { this.encheres = encheres; }

    public int getBoutsPreneur() { return boutsPreneur; }
    public void setBoutsPreneur(int boutsPreneur) { this.boutsPreneur = boutsPreneur; }

    public int getPointsPreneurX2() { return pointsPreneurX2; }
    public void setPointsPreneurX2(int pointsPreneurX2) { this.pointsPreneurX2 = pointsPreneurX2; }

    public int getSeuilCourant() { return seuilCourant; }
    public void setSeuilCourant(int seuilCourant) { this.seuilCourant = seuilCourant; }

    public int getDonneActuelle() { return donneActuelle; }
    public void setDonneActuelle(int donneActuelle) { this.donneActuelle = donneActuelle; }

    public int getMaxDonnes() { return maxDonnes; }
    public void setMaxDonnes(int maxDonnes) { this.maxDonnes = maxDonnes; }

    public int getMaxPoints() { return maxPoints; }
    public void setMaxPoints(int maxPoints) { this.maxPoints = maxPoints; }

    public int getScoreGlobalA() { return scoreGlobalA; }
    public void setScoreGlobalA(int scoreGlobalA) { this.scoreGlobalA = scoreGlobalA; }

    public int getScoreGlobalB() { return scoreGlobalB; }
    public void setScoreGlobalB(int scoreGlobalB) { this.scoreGlobalB = scoreGlobalB; }

    public String getPoigneeDeclaree() { return poigneeDeclaree; }
    public void setPoigneeDeclaree(String poigneeDeclaree) { this.poigneeDeclaree = poigneeDeclaree; }

    public boolean isPetitSecDetecte() { return petitSecDetecte; }
    public void setPetitSecDetecte(boolean petitSecDetecte) { this.petitSecDetecte = petitSecDetecte; }

    public boolean isMonPetitEstSec() { return monPetitEstSec; }
    public void setMonPetitEstSec(boolean monPetitEstSec) { this.monPetitEstSec = monPetitEstSec; }

    public Map<Long, Integer> getScoresJoueurs() { return scoresJoueurs; }
    public void setScoresJoueurs(Map<Long, Integer> scoresJoueurs) { this.scoresJoueurs = scoresJoueurs; }

    public ResultatTarotDTO getResultat() { return resultat; }
    public void setResultat(ResultatTarotDTO resultat) { this.resultat = resultat; }

    public static class CartePliDTO {
        private CarteDTO carte;
        private String   pseudo;
        private int      equipe;

        public CartePliDTO(CarteDTO carte, String pseudo, int equipe) {
            this.carte  = carte;
            this.pseudo = pseudo;
            this.equipe = equipe;
        }

        public CarteDTO getCarte()  { return carte; }
        public String   getPseudo() { return pseudo; }
        public int      getEquipe() { return equipe; }
        public void setCarte(CarteDTO carte)   { this.carte  = carte; }
        public void setPseudo(String pseudo)   { this.pseudo = pseudo; }
        public void setEquipe(int equipe)      { this.equipe = equipe; }
    }

    // Résultat complet de la donne.
    public static class ResultatTarotDTO {
        private String  enchereType;
        private int     multiplicateur;
        private String  pseudoPreneur;
        private String  pseudoPartenaire;  // null si 3j/4j ou jeu solo 5j
        private int     boutsPreneur;
        private int     pointsPreneurX2;   // points réels = /2.0
        private int     seuil;
        private boolean contratRempli;
        private int     scorePartie;       // points gagnés/perdus par le preneur (valeur absolue)
        private boolean petitAuBout;
        private int     gagnantEquipe;     // 1 = preneur | 2 = défenseurs

        public ResultatTarotDTO() {}

        public String  getEnchereType()      { return enchereType; }
        public void    setEnchereType(String v)   { this.enchereType = v; }

        public int     getMultiplicateur()   { return multiplicateur; }
        public void    setMultiplicateur(int v)   { this.multiplicateur = v; }

        public String  getPseudoPreneur()    { return pseudoPreneur; }
        public void    setPseudoPreneur(String v) { this.pseudoPreneur = v; }

        public String  getPseudoPartenaire() { return pseudoPartenaire; }
        public void    setPseudoPartenaire(String v) { this.pseudoPartenaire = v; }

        public int     getBoutsPreneur()     { return boutsPreneur; }
        public void    setBoutsPreneur(int v)     { this.boutsPreneur = v; }

        public int     getPointsPreneurX2()  { return pointsPreneurX2; }
        public void    setPointsPreneurX2(int v)  { this.pointsPreneurX2 = v; }

        public int     getSeuil()            { return seuil; }
        public void    setSeuil(int v)             { this.seuil = v; }

        public boolean isContratRempli()     { return contratRempli; }
        public void    setContratRempli(boolean v) { this.contratRempli = v; }

        public int     getScorePartie()      { return scorePartie; }
        public void    setScorePartie(int v)      { this.scorePartie = v; }

        public boolean isPetitAuBout()       { return petitAuBout; }
        public void    setPetitAuBout(boolean v)   { this.petitAuBout = v; }

        public int     getGagnantEquipe()    { return gagnantEquipe; }
        public void    setGagnantEquipe(int v)    { this.gagnantEquipe = v; }
    }
}
