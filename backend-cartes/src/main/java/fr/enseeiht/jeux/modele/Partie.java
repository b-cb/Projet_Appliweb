package fr.enseeiht.jeux.modele;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Partie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String statut;
    private String atout;
    private int scoreA;
    private int scoreB;

    @OneToMany(mappedBy = "partie", cascade = CascadeType.ALL)
    private List<Joueur> joueurs = new ArrayList<>();

    public Partie() {
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

    public List<Joueur> getJoueurs() {
        return joueurs;
    }

    public void setJoueurs(List<Joueur> joueurs) {
        this.joueurs = joueurs;
    }

}
