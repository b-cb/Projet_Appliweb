package fr.enseeiht.jeux.dto;

import fr.enseeiht.jeux.modele.Joueur;

public class JoueurDTO {

    private Long id;
    private int equipe;
    private int position;
    private String pseudo;
    private Long utilisateurId;
    private boolean bot;

    public JoueurDTO() {
    }

    public JoueurDTO(Long id, int equipe, int position, String pseudo, Long utilisateurId, boolean bot) {
        this.id = id;
        this.equipe = equipe;
        this.position = position;
        this.pseudo = pseudo;
        this.utilisateurId = utilisateurId;
        this.bot = bot;
    }

    public static JoueurDTO fromEntity(Joueur j) {
        boolean isBot = j.getUtilisateur() != null && j.getUtilisateur().isBot();
        return new JoueurDTO(
                j.getId(),
                j.getEquipe(),
                j.getPosition(),
                j.getUtilisateur() != null ? j.getUtilisateur().getPseudo() : null,
                j.getUtilisateur() != null ? j.getUtilisateur().getId() : null,
                isBot
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getEquipe() {
        return equipe;
    }

    public void setEquipe(int equipe) {
        this.equipe = equipe;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getPseudo() {
        return pseudo;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo;
    }

    public Long getUtilisateurId() {
        return utilisateurId;
    }

    public void setUtilisateurId(Long utilisateurId) {
        this.utilisateurId = utilisateurId;
    }

    public boolean isBot() {
        return bot;
    }

    public void setBot(boolean bot) {
        this.bot = bot;
    }
}
