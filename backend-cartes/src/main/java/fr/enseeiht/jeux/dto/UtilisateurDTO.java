package fr.enseeiht.jeux.dto;

import fr.enseeiht.jeux.modele.Utilisateur;

public class UtilisateurDTO {

    private Long id;
    private String pseudo;
    private int scoreGlobal;

    public UtilisateurDTO() {
    }

    public UtilisateurDTO(Long id, String pseudo, int scoreGlobal) {
        this.id = id;
        this.pseudo = pseudo;
        this.scoreGlobal = scoreGlobal;
    }

    public static UtilisateurDTO fromEntity(Utilisateur u) {
        return new UtilisateurDTO(u.getId(), u.getPseudo(), u.getScoreGlobal());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPseudo() {
        return pseudo;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo;
    }

    public int getScoreGlobal() {
        return scoreGlobal;
    }

    public void setScoreGlobal(int scoreGlobal) {
        this.scoreGlobal = scoreGlobal;
    }
}
