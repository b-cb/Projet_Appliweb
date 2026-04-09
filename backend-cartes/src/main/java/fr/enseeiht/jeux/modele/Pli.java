package fr.enseeiht.jeux.modele;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Pli {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int numTour;
    private int gagnantEquipe;   // 1 ou 2
    private int pointsPli;       // points attribués à ce pli
    private int joueurOuvreurIndex; // index (0-3) du joueur qui a ouvert ce pli

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "partie_id")
    private Partie partie;

    @ManyToMany
    @JoinTable(name = "pli_carte", joinColumns = @JoinColumn(name = "pli_id"), inverseJoinColumns = @JoinColumn(name = "carte_id"))
    private List<Carte> cartesJouees = new ArrayList<>();

    public Pli() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getNumTour() {
        return numTour;
    }

    public void setNumTour(int numTour) {
        this.numTour = numTour;
    }

    public Partie getPartie() {
        return partie;
    }

    public void setPartie(Partie partie) {
        this.partie = partie;
    }

    public List<Carte> getCartesJouees() {
        return cartesJouees;
    }

    public void setCartesJouees(List<Carte> cartesJouees) {
        this.cartesJouees = cartesJouees;
    }

    public int getGagnantEquipe() {
        return gagnantEquipe;
    }

    public void setGagnantEquipe(int gagnantEquipe) {
        this.gagnantEquipe = gagnantEquipe;
    }

    public int getPointsPli() {
        return pointsPli;
    }

    public void setPointsPli(int pointsPli) {
        this.pointsPli = pointsPli;
    }

    public int getJoueurOuvreurIndex() {
        return joueurOuvreurIndex;
    }

    public void setJoueurOuvreurIndex(int joueurOuvreurIndex) {
        this.joueurOuvreurIndex = joueurOuvreurIndex;
    }

}
