package fr.enseeiht.jeux.modele;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
public class Enchere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int contrat;
    private String couleur;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "partie_id")
    private Partie partie;

    @ManyToOne(optional = false)
    @JoinColumn(name = "preneur_id")
    private Joueur preneur;

    public Enchere() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getContrat() {
        return contrat;
    }

    public void setContrat(int contrat) {
        this.contrat = contrat;
    }

    public String getCouleur() {
        return couleur;
    }

    public void setCouleur(String couleur) {
        this.couleur = couleur;
    }

    public Partie getPartie() {
        return partie;
    }

    public void setPartie(Partie partie) {
        this.partie = partie;
    }

    public Joueur getPreneur() {
        return preneur;
    }

    public void setPreneur(Joueur preneur) {
        this.preneur = preneur;
    }

}
