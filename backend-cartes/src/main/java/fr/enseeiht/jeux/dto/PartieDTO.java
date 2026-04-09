package fr.enseeiht.jeux.dto;

import fr.enseeiht.jeux.modele.Partie;

public class PartieDTO {

    private Long id;
    private String statut;
    private String atout;
    private int scoreA;
    private int scoreB;
    private int nombreJoueurs;

    public PartieDTO() {
    }

    public PartieDTO(Long id, String statut, String atout, int scoreA, int scoreB, int nombreJoueurs) {
        this.id = id;
        this.statut = statut;
        this.atout = atout;
        this.scoreA = scoreA;
        this.scoreB = scoreB;
        this.nombreJoueurs = nombreJoueurs;
    }

    public static PartieDTO fromEntity(Partie p) {
        return new PartieDTO(
                p.getId(),
                p.getStatut(),
                p.getAtout(),
                p.getScoreA(),
                p.getScoreB(),
                p.getJoueurs() != null ? p.getJoueurs().size() : 0
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
}
