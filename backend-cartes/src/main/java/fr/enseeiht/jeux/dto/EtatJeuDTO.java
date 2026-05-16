package fr.enseeiht.jeux.dto;

import java.util.List;

/**
 * Vue complète de l'état du jeu pour un joueur donné.
 * Retourné par GET /api/partie/{id}/etat?utilisateurId=...
 */
public class EtatJeuDTO {

    private Long partieId;
    private String statut;          // EN_ENCHERE, EN_JEU, TERMINEE
    private String atout;           // couleur de l'atout (null pendant les enchères)
    private int contratValeur;
    private String contratCouleur;
    private int scoreA;
    private int scoreB;
    private int numPliCourant;

    // Tour de jeu
    private Long tourJoueurId;      // id du Joueur (pas Utilisateur) dont c'est le tour
    private String tourPseudo;

    // Ma main (uniquement pour l'utilisateur qui appelle)
    private List<CarteDTO> maMain;
    private Long monJoueurId;
    private int monEquipe;

    // Pli en cours (cartes déjà jouées dans le pli courant)
    private List<CartePliDTO> pliCourant;

    // Dernier pli terminé (pour affichage)
    private List<CartePliDTO> dernierPli;
    private int dernierPliGagnantEquipe;

    // Historique des enchères
    private List<EnchereDTO> encheres;

    // Résultat (si TERMINEE)
    private ResultatDTO resultat;

    // Coinche/Surcoinche
    private int coinche;  // 0=normal, 1=coinché (×2), 2=surcoinché (×4)
    private Long preneurId; // id du preneur (pour savoir qui peut surcoincher)
    private int preneurEquipe; // équipe du preneur (1 ou 2)

    // Multi-manche
    private int donneActuelle;
    private int maxDonnes;
    private int maxPoints;
    private int scoreGlobalA;
    private int scoreGlobalB;

    public EtatJeuDTO() {}

    public Long getPartieId() { return partieId; }
    public void setPartieId(Long partieId) { this.partieId = partieId; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getAtout() { return atout; }
    public void setAtout(String atout) { this.atout = atout; }

    public int getContratValeur() { return contratValeur; }
    public void setContratValeur(int contratValeur) { this.contratValeur = contratValeur; }

    public String getContratCouleur() { return contratCouleur; }
    public void setContratCouleur(String contratCouleur) { this.contratCouleur = contratCouleur; }

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

    public List<CartePliDTO> getPliCourant() { return pliCourant; }
    public void setPliCourant(List<CartePliDTO> pliCourant) { this.pliCourant = pliCourant; }

    public List<CartePliDTO> getDernierPli() { return dernierPli; }
    public void setDernierPli(List<CartePliDTO> dernierPli) { this.dernierPli = dernierPli; }

    public int getDernierPliGagnantEquipe() { return dernierPliGagnantEquipe; }
    public void setDernierPliGagnantEquipe(int dernierPliGagnantEquipe) { this.dernierPliGagnantEquipe = dernierPliGagnantEquipe; }

    public List<EnchereDTO> getEncheres() { return encheres; }
    public void setEncheres(List<EnchereDTO> encheres) { this.encheres = encheres; }

    public ResultatDTO getResultat() { return resultat; }
    public void setResultat(ResultatDTO resultat) { this.resultat = resultat; }

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

    public int getCoinche() { return coinche; }
    public void setCoinche(int coinche) { this.coinche = coinche; }

    public Long getPreneurId() { return preneurId; }
    public void setPreneurId(Long preneurId) { this.preneurId = preneurId; }

    public int getPreneurEquipe() { return preneurEquipe; }
    public void setPreneurEquipe(int preneurEquipe) { this.preneurEquipe = preneurEquipe; }

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
}
