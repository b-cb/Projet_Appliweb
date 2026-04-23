package fr.enseeiht.jeux.dto;

import fr.enseeiht.jeux.modele.Partie;

public class PartieDTO {

    private Long id;
    private String statut;
    private String atout;
    private int scoreA;
    private int scoreB;
    private int nombreJoueurs;
    private String typeJeu;
    private int nbJoueursRequis;
    private java.util.Map<String, Integer> scoresJoueurs;

    public PartieDTO() {
    }

    public PartieDTO(Long id, String statut, String atout, int scoreA, int scoreB,
                     int nombreJoueurs, String typeJeu, int nbJoueursRequis, java.util.Map<String, Integer> scoresJoueurs) {
        this.id = id;
        this.statut = statut;
        this.atout = atout;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.nombreJoueurs = nombreJoueurs;
        this.typeJeu = typeJeu;
        this.nbJoueursRequis = nbJoueursRequis;
        this.scoresJoueurs = scoresJoueurs;
    }

    public static PartieDTO fromEntity(Partie p) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        if (p.getJoueurs() != null) {
            for(fr.enseeiht.jeux.modele.Joueur j : p.getJoueurs()) {
                if (j.getUtilisateur() != null) {
                    map.put(j.getUtilisateur().getPseudo(), j.getScorePartie());
                }
            }
        }
        return new PartieDTO(
                p.getId(),
                p.getStatut(),
                p.getAtout(),
                p.getScoreGlobalA(),
                p.getScoreGlobalB(),
                p.getJoueurs() != null ? p.getJoueurs().size() : 0,
                p.getTypeJeu() != null ? p.getTypeJeu() : "COINCHE",
                p.getNbJoueursRequis() > 0 ? p.getNbJoueursRequis() : 4,
                map
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getAtout() {
        return atout;
    }

    public void setAtout(String atout) {
        this.atout = atout;
    }

    public int getScoreA() {
        return scoreA;
    }

    public void setScoreA(int scoreA) {
        this.scoreA = scoreA;
    }

    public int getScoreB() {
        return scoreB;
    }

    public void setScoreB(int scoreB) {
        this.scoreB = scoreB;
    }

    public int getNombreJoueurs() {
        return nombreJoueurs;
    }

    public void setNombreJoueurs(int nombreJoueurs) {
        this.nombreJoueurs = nombreJoueurs;
    }

    public String getTypeJeu() {
        return typeJeu;
    }

    public void setTypeJeu(String typeJeu) {
        this.typeJeu = typeJeu;
    }


    public int getNbJoueursRequis() {
        return nbJoueursRequis;
    }

    public void setNbJoueursRequis(int nbJoueursRequis) {
        this.nbJoueursRequis = nbJoueursRequis;
    }

    public java.util.Map<String, Integer> getScoresJoueurs() {
        return scoresJoueurs;
    }

    public void setScoresJoueurs(java.util.Map<String, Integer> scoresJoueurs) {
        this.scoresJoueurs = scoresJoueurs;
    }
}
