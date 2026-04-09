package fr.enseeiht.jeux.dto;

import fr.enseeiht.jeux.modele.Carte;

public class CarteDTO {

    private Long id;
    private String valeur;
    private String couleur;

    public CarteDTO() {}

    public CarteDTO(Long id, String valeur, String couleur) {
        this.id = id;
        this.valeur = valeur;
        this.couleur = couleur;
    }

    public static CarteDTO fromEntity(Carte c) {
        return new CarteDTO(c.getId(), c.getValeur(), c.getCouleur());
    }

    public Long getId() { return id; }
    public String getValeur() { return valeur; }
    public String getCouleur() { return couleur; }
    public void setId(Long id) { this.id = id; }
    public void setValeur(String valeur) { this.valeur = valeur; }
    public void setCouleur(String couleur) { this.couleur = couleur; }
}
