package fr.enseeiht.jeux.modele;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Joueur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int equipe;
    private int position;

    @ManyToOne(optional = false)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "partie_id")
    private Partie partie;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "joueur_carte", joinColumns = @JoinColumn(name = "joueur_id"), inverseJoinColumns = @JoinColumn(name = "carte_id"))
    private List<Carte> cartesEnMain = new ArrayList<>();

    public Joueur() {
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

    public Utilisateur getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    public Partie getPartie() {
        return partie;
    }

    public void setPartie(Partie partie) {
        this.partie = partie;
    }

    public List<Carte> getCartesEnMain() {
        return cartesEnMain;
    }

    public void setCartesEnMain(List<Carte> cartesEnMain) {
        this.cartesEnMain = cartesEnMain;
    }

}
