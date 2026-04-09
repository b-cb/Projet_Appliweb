package fr.enseeiht.jeux.dto;

import fr.enseeiht.jeux.modele.Enchere;

public class EnchereDTO {

    private Long id;
    private boolean passe;
    private int contrat;
    private String couleur;
    private Long joueurId;
    private String pseudoJoueur;

    public EnchereDTO() {}

    public static EnchereDTO fromEntity(Enchere e) {
        EnchereDTO dto = new EnchereDTO();
        dto.id = e.getId();
        dto.passe = e.isPasse();
        dto.contrat = e.getContrat();
        dto.couleur = e.getCouleur();
        dto.joueurId = e.getPreneur().getId();
        dto.pseudoJoueur = e.getPreneur().getUtilisateur().getPseudo();
        return dto;
    }

    public Long getId() { return id; }
    public boolean isPasse() { return passe; }
    public int getContrat() { return contrat; }
    public String getCouleur() { return couleur; }
    public Long getJoueurId() { return joueurId; }
    public String getPseudoJoueur() { return pseudoJoueur; }
    public void setId(Long id) { this.id = id; }
    public void setPasse(boolean passe) { this.passe = passe; }
    public void setContrat(int contrat) { this.contrat = contrat; }
    public void setCouleur(String couleur) { this.couleur = couleur; }
    public void setJoueurId(Long joueurId) { this.joueurId = joueurId; }
    public void setPseudoJoueur(String pseudoJoueur) { this.pseudoJoueur = pseudoJoueur; }
}
