package fr.enseeiht.jeux.dto;

public class ResultatDTO {

    private int scoreA;
    private int scoreB;
    private int gagnantEquipe;      // 1 ou 2
    private boolean contratRempli;
    private int contratValeur;
    private String contratCouleur;
    private String pseudoPreneur;   // joueur qui avait pris le contrat

    public ResultatDTO() {}

    public int getScoreA() { return scoreA; }
    public void setScoreA(int scoreA) { this.scoreA = scoreA; }

    public int getScoreB() { return scoreB; }
    public void setScoreB(int scoreB) { this.scoreB = scoreB; }

    public int getGagnantEquipe() { return gagnantEquipe; }
    public void setGagnantEquipe(int gagnantEquipe) { this.gagnantEquipe = gagnantEquipe; }

    public boolean isContratRempli() { return contratRempli; }
    public void setContratRempli(boolean contratRempli) { this.contratRempli = contratRempli; }

    public int getContratValeur() { return contratValeur; }
    public void setContratValeur(int contratValeur) { this.contratValeur = contratValeur; }

    public String getContratCouleur() { return contratCouleur; }
    public void setContratCouleur(String contratCouleur) { this.contratCouleur = contratCouleur; }

    public String getPseudoPreneur() { return pseudoPreneur; }
    public void setPseudoPreneur(String pseudoPreneur) { this.pseudoPreneur = pseudoPreneur; }
}
