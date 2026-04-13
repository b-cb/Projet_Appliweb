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

    // Mode de jeu : "COINCHE" (défaut) ou "TAROT"
    private String typeJeu = "COINCHE";
    // Nombre de joueurs requis pour démarrer (4 pour coinche, 3/4/5 pour tarot)
    private int nbJoueursRequis = 4;

    // Suivi de jeu
    private int tourJoueurIndex;       // 0-N : index du joueur dont c'est le tour
    private int contratValeur;         // valeur de l'enchère gagnante (80, 90, ... 160, capot)
    private String contratCouleur;     // couleur de l'enchère gagnante
    private Long preneurId;            // id du Joueur qui a pris le contrat
    private int passesConsecutives;    // compteur de passes (reset à 0 quand une enchère est faite)
    private int numPliCourant;         // 1-N : numéro du pli en cours

    // Tarot : sous-phase de jeu (null pour Coinche)
    // null = enchères, "CHIEN" = phase chien/écart, "JEU" = tricks en cours
    private String phaseJeu;

    // Tarot : type d'enchère gagnante ("PETITE"|"GARDE"|"GARDE_SANS"|"GARDE_CONTRE")
    private String enchereType;

    // Tarot : multiplicateur résultant de l'enchère (1|2|4|6)
    private int multiplicateur;

    // Tarot : le chien (cartes non distribuées initialement)
    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(name = "partie_chien",
        joinColumns = @JoinColumn(name = "partie_id"),
        inverseJoinColumns = @JoinColumn(name = "carte_id"))
    private List<Carte> chien = new ArrayList<>();

    // Tarot : les cartes écartées par le preneur (comptent dans ses plis)
    @ManyToMany(cascade = CascadeType.PERSIST)
    @JoinTable(name = "partie_ecarte",
        joinColumns = @JoinColumn(name = "partie_id"),
        inverseJoinColumns = @JoinColumn(name = "carte_id"))
    private List<Carte> ecartes = new ArrayList<>();

    // Tarot : suivi du Petit au bout
    private boolean petitAuBoutPreneur;

    // Tarot 5 joueurs : couleur du Roi appelé par le preneur ("Coeur"|"Carreau"|"Trefle"|"Pique")
    private String appelRoi;

    // Tarot 5 joueurs : id du Joueur partenaire (révélé quand le Roi appelé est joué)
    private Long partenaireId;

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

    public String getTypeJeu() {
        return typeJeu;
    }

    public void setTypeJeu(String typeJeu) {
        this.typeJeu = typeJeu;
    }

    public int getNbJoueursRequis() {
        return nbJoueursRequis;
    }

    public void setNbJoueursRequis(int nbJoueursRequis) {
        this.nbJoueursRequis = nbJoueursRequis;
    }

    public String getPhaseJeu() {
        return phaseJeu;
    }

    public void setPhaseJeu(String phaseJeu) {
        this.phaseJeu = phaseJeu;
    }

    public String getEnchereType() {
        return enchereType;
    }

    public void setEnchereType(String enchereType) {
        this.enchereType = enchereType;
    }

    public int getMultiplicateur() {
        return multiplicateur;
    }

    public void setMultiplicateur(int multiplicateur) {
        this.multiplicateur = multiplicateur;
    }

    public List<Carte> getChien() {
        return chien;
    }

    public void setChien(List<Carte> chien) {
        this.chien = chien;
    }

    public List<Carte> getEcartes() {
        return ecartes;
    }

    public void setEcartes(List<Carte> ecartes) {
        this.ecartes = ecartes;
    }

    public boolean isPetitAuBoutPreneur() {
        return petitAuBoutPreneur;
    }

    public void setPetitAuBoutPreneur(boolean petitAuBoutPreneur) {
        this.petitAuBoutPreneur = petitAuBoutPreneur;
    }

    public String getAppelRoi() { return appelRoi; }
    public void setAppelRoi(String appelRoi) { this.appelRoi = appelRoi; }

    public Long getPartenaireId() { return partenaireId; }
    public void setPartenaireId(Long partenaireId) { this.partenaireId = partenaireId; }

}
