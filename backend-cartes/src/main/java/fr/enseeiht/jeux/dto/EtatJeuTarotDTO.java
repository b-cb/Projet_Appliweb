package fr.enseeiht.jeux.dto;

import java.util.List;

/**
 * Vue de l'état du jeu Tarot pour un joueur donné.
 * Retourné par GET /api/partie/{id}/tarot/etat?utilisateurId=...
 */
public class EtatJeuTarotDTO {

    private Long partieId;
    private String statut;           // EN_ENCHERE, EN_JEU, TERMINEE
    private String phaseJeu;         // null (enchères), "CHIEN" (écart), "JEU" (tricks)

    // Enchère gagnante
    private String enchereType;      // "PETITE"|"GARDE"|"GARDE_SANS"|"GARDE_CONTRE"
    private int multiplicateur;      // 1|2|4|6

    // Scores affichés (points ×2 pendant le jeu pour afficher les demi-points)
    private int scoreA;              // points preneur (×2)
    private int scoreB;              // points défenseurs (×2)
    private int numPliCourant;

    // Tour de jeu
    private Long tourJoueurId;
    private String tourPseudo;

    // Ma main
    private List<CarteDTO> maMain;
    private Long monJoueurId;
    private int monEquipe;           // 0 = inconnu, 1 = preneur/partenaire, 2 = défenseur
    private boolean estPreneur;
    private boolean estPartenaire;   // true pour le partenaire en 5j (après révélation)

    // 5 joueurs : couleur du Roi appelé (visible par tous une fois la phase APPEL_ROI terminée)
    private String appelRoi;
    private String pseudoPartenaire; // null tant que non révélé

    // Chien (visible par le preneur en phase CHIEN pour PETITE/GARDE/GARDE_SANS)
    private List<CarteDTO> chien;

    // Pli courant
    private List<CartePliDTO> pliCourant;
    private List<CartePliDTO> dernierPli;
    private int dernierPliGagnantEquipe;

    // Historique des enchères Tarot
    private List<EnchereDTO> encheres;

    // Résultat (si TERMINEE)
    private ResultatTarotDTO resultat;

    // Progression du score (bouts capturés par le preneur, total points ×2)
    private int boutsPreneur;
    private int pointsPreneurX2;
    private int seuilCourant;  // seuil basé sur les bouts actuellement capturés

    public EtatJeuTarotDTO() {}

    // --- Getters / Setters ---

    public Long getPartieId() { return partieId; }
    public void setPartieId(Long partieId) { this.partieId = partieId; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getPhaseJeu() { return phaseJeu; }
    public void setPhaseJeu(String phaseJeu) { this.phaseJeu = phaseJeu; }

    public String getEnchereType() { return enchereType; }
    public void setEnchereType(String enchereType) { this.enchereType = enchereType; }

    public int getMultiplicateur() { return multiplicateur; }
    public void setMultiplicateur(int multiplicateur) { this.multiplicateur = multiplicateur; }

    public int getScoreA() { return scoreA; }
    public void setScoreA(int scoreA) { this.scoreA = scoreA; }

    public int getScoreB() { return scoreB; }
    public void setScoreB(int scoreB) { this.scoreB = scoreB; }

    public int getNumPliCourant() { return numPliCourant; }
    public void setNumPliCourant(int numPliCourant) { this.numPliCourant = numPliCourant; }

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
    public void setDernierPliGagnantEquipe(int dernierPliGagnantEquipe) { this.dernierPliGagnantEquipe = dernierPliGagnantEquipe; }

    public List<EnchereDTO> getEncheres() { return encheres; }
    public void setEncheres(List<EnchereDTO> encheres) { this.encheres = encheres; }

    public ResultatTarotDTO getResultat() { return resultat; }
    public void setResultat(ResultatTarotDTO resultat) { this.resultat = resultat; }

    public int getBoutsPreneur() { return boutsPreneur; }
    public void setBoutsPreneur(int boutsPreneur) { this.boutsPreneur = boutsPreneur; }

    public int getPointsPreneurX2() { return pointsPreneurX2; }
    public void setPointsPreneurX2(int pointsPreneurX2) { this.pointsPreneurX2 = pointsPreneurX2; }

    public int getSeuilCourant() { return seuilCourant; }
    public void setSeuilCourant(int seuilCourant) { this.seuilCourant = seuilCourant; }

    // --- Inner DTOs ---

    public static class CartePliDTO {
        private CarteDTO carte;
        private String pseudo;
        private int equipe;

        public CartePliDTO(CarteDTO carte, String pseudo, int equipe) {
            this.carte = carte;
            this.pseudo = pseudo;
            this.equipe = equipe;
        }

        public CarteDTO getCarte() { return carte; }
        public String getPseudo() { return pseudo; }
        public int getEquipe() { return equipe; }
        public void setCarte(CarteDTO carte) { this.carte = carte; }
        public void setPseudo(String pseudo) { this.pseudo = pseudo; }
        public void setEquipe(int equipe) { this.equipe = equipe; }
    }

    public static class ResultatTarotDTO {
        private String enchereType;
        private int multiplicateur;
        private String pseudoPreneur;
        private String pseudoPartenaire; // null si 3j/4j ou si non révélé
        private int boutsPreneur;
        private int pointsPreneurX2;   // actual points = / 2.0
        private int seuil;
        private boolean contratRempli;
        private int scorePartie;       // points gagnés/perdus par le preneur
        private boolean petitAuBout;
        private int gagnantEquipe;     // 1 (preneur) ou 2 (défenseurs)

        public ResultatTarotDTO() {}

        public String getEnchereType() { return enchereType; }
        public void setEnchereType(String enchereType) { this.enchereType = enchereType; }

        public int getMultiplicateur() { return multiplicateur; }
        public void setMultiplicateur(int multiplicateur) { this.multiplicateur = multiplicateur; }

        public String getPseudoPreneur() { return pseudoPreneur; }
        public void setPseudoPreneur(String pseudoPreneur) { this.pseudoPreneur = pseudoPreneur; }

        public String getPseudoPartenaire() { return pseudoPartenaire; }
        public void setPseudoPartenaire(String p) { this.pseudoPartenaire = p; }

        public int getBoutsPreneur() { return boutsPreneur; }
        public void setBoutsPreneur(int boutsPreneur) { this.boutsPreneur = boutsPreneur; }

        public int getPointsPreneurX2() { return pointsPreneurX2; }
        public void setPointsPreneurX2(int pointsPreneurX2) { this.pointsPreneurX2 = pointsPreneurX2; }

        public int getSeuil() { return seuil; }
        public void setSeuil(int seuil) { this.seuil = seuil; }

        public boolean isContratRempli() { return contratRempli; }
        public void setContratRempli(boolean contratRempli) { this.contratRempli = contratRempli; }

        public int getScorePartie() { return scorePartie; }
        public void setScorePartie(int scorePartie) { this.scorePartie = scorePartie; }

        public boolean isPetitAuBout() { return petitAuBout; }
        public void setPetitAuBout(boolean petitAuBout) { this.petitAuBout = petitAuBout; }

        public int getGagnantEquipe() { return gagnantEquipe; }
        public void setGagnantEquipe(int gagnantEquipe) { this.gagnantEquipe = gagnantEquipe; }
    }
}
