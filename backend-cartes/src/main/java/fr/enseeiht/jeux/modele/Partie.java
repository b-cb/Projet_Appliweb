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

    // Suivi de jeu
    private int tourJoueurIndex;       // 0-3 : index du joueur dont c'est le tour
    private int contratValeur;         // valeur de l'enchère gagnante (80, 90, ... 160, capot)
    private String contratCouleur;     // couleur de l'enchère gagnante
    private Long preneurId;            // id du Joueur qui a pris le contrat
    private int passesConsecutives;    // compteur de passes (reset à 0 quand une enchère est faite)
    private int numPliCourant;         // 1-8 : numéro du pli en cours

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

    public int getTourJoueurIndex() {
        return tourJoueurIndex;
    }

    public void setTourJoueurIndex(int tourJoueurIndex) {
        this.tourJoueurIndex = tourJoueurIndex;
    }

    public int getContratValeur() {
        return contratValeur;
    }

    public void setContratValeur(int contratValeur) {
        this.contratValeur = contratValeur;
    }

    public String getContratCouleur() {
        return contratCouleur;
    }

    public void setContratCouleur(String contratCouleur) {
        this.contratCouleur = contratCouleur;
    }

    public Long getPreneurId() {
        return preneurId;
    }

    public void setPreneurId(Long preneurId) {
        this.preneurId = preneurId;
    }

    public int getPassesConsecutives() {
        return passesConsecutives;
    }

    public void setPassesConsecutives(int passesConsecutives) {
        this.passesConsecutives = passesConsecutives;
    }

    public int getNumPliCourant() {
        return numPliCourant;
    }

    public void setNumPliCourant(int numPliCourant) {
        this.numPliCourant = numPliCourant;
    }

}
