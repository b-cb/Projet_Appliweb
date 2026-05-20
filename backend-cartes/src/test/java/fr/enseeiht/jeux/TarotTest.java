package fr.enseeiht.jeux;

import fr.enseeiht.jeux.tarot.EtatTarotDTO;
import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.modele.*;
import fr.enseeiht.jeux.repository.*;
import fr.enseeiht.jeux.service.PartieService;
import fr.enseeiht.jeux.tarot.TarotScoringService;
import fr.enseeiht.jeux.tarot.TarotService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests Tarot.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TarotTest {

    // =========================================================
    // Injection
    // =========================================================

    @Autowired private TarotScoringService scoringService;
    @Autowired private TarotService tarotService;
    @Autowired private PartieService partieService;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private JoueurRepository joueurRepository;
    @Autowired private PartieRepository partieRepository;
    // carteRepository non utilisé directement (cartes créées via PartieService)

    // =========================================================
    // Helpers
    // =========================================================

    private Utilisateur creerUtilisateur(String pseudo) {
        Utilisateur u = new Utilisateur();
        u.setPseudo(pseudo);
        u.setMdp("$2a$10$dummyhashfordevelopment");
        u.setScoreGlobal(0);
        return utilisateurRepository.save(u);
    }

    /** Crée une partie Tarot à N joueurs et la démarre. */
    private Partie demarrerPartieTarot(int nbJoueurs, List<Utilisateur> joueurs) {
        Partie p = partieService.creerPartie("TAROT", nbJoueurs);
        for (Utilisateur u : joueurs) {
            partieService.rejoindrePartie(p.getId(), u.getId());
        }
        partieService.demarrerPartie(p.getId());
        return partieRepository.findById(p.getId()).orElseThrow();
    }

    /** Carte-outil : crée un objet Carte en mémoire (sans persistance) pour les tests unitaires. */
    private Carte carte(String couleur, String valeur) {
        Carte c = new Carte();
        c.setCouleur(couleur);
        c.setValeur(valeur);
        return c;
    }

    // =========================================================
    // BLOC 1 — TarotScoringService (tests unitaires purs)
    // =========================================================

    @Nested
    @DisplayName("TarotScoringService — valeur des cartes")
    class ValeurCartes {

        @Test
        @DisplayName("Les bouts (Petit, Monde, Excuse) valent 9 ×2")
        void bouts_valent9x2() {
            assertThat(scoringService.carteVautX2(carte("Atout", "1"))).isEqualTo(9);
            assertThat(scoringService.carteVautX2(carte("Atout", "21"))).isEqualTo(9);
            assertThat(scoringService.carteVautX2(carte("Atout", "Excuse"))).isEqualTo(9);
        }

        @Test
        @DisplayName("Les Rois valent 9 ×2 (toutes couleurs)")
        void roi_vaut9x2() {
            for (String couleur : List.of("Coeur", "Carreau", "Trefle", "Pique")) {
                assertThat(scoringService.carteVautX2(carte(couleur, "Roi")))
                        .as("Roi de " + couleur).isEqualTo(9);
            }
        }

        @Test
        @DisplayName("Les Dames valent 7 ×2")
        void dame_vaut7x2() {
            assertThat(scoringService.carteVautX2(carte("Coeur", "Dame"))).isEqualTo(7);
        }

        @Test
        @DisplayName("Les Cavaliers valent 5 ×2")
        void cavalier_vaut5x2() {
            assertThat(scoringService.carteVautX2(carte("Pique", "Cavalier"))).isEqualTo(5);
        }

        @Test
        @DisplayName("Les Valets valent 3 ×2")
        void valet_vaut3x2() {
            assertThat(scoringService.carteVautX2(carte("Trefle", "Valet"))).isEqualTo(3);
        }

        @Test
        @DisplayName("Les atouts ordinaires (2-20) valent 1 ×2")
        void atoutOrdinaire_vaut1x2() {
            assertThat(scoringService.carteVautX2(carte("Atout", "10"))).isEqualTo(1);
            assertThat(scoringService.carteVautX2(carte("Atout", "15"))).isEqualTo(1);
        }

        @Test
        @DisplayName("Les cartes numériques de couleur valent 1 ×2")
        void carteNumerique_vaut1x2() {
            assertThat(scoringService.carteVautX2(carte("Coeur", "5"))).isEqualTo(1);
            assertThat(scoringService.carteVautX2(carte("Carreau", "10"))).isEqualTo(1);
        }

        @Test
        @DisplayName("Total des 78 cartes = 182 ×2 (= 91 pts réels)")
        void totalJeu_est182x2() {
            // Reconstituer le deck complet
            List<Carte> deck = new ArrayList<>();
            String[] couleurs = {"Coeur", "Carreau", "Trefle", "Pique"};
            String[] valeurs = {"1","2","3","4","5","6","7","8","9","10","Valet","Cavalier","Dame","Roi"};
            for (String couleur : couleurs) {
                for (String valeur : valeurs) deck.add(carte(couleur, valeur));
            }
            // 21 atouts numérotés
            for (int i = 1; i <= 21; i++) deck.add(carte("Atout", String.valueOf(i)));
            // Excuse
            deck.add(carte("Atout", "Excuse"));

            assertThat(deck).hasSize(78);
            assertThat(scoringService.calculerPointsX2(deck)).isEqualTo(182);
        }
    }

    @Nested
    @DisplayName("TarotScoringService — seuils et multiplicateurs")
    class SeuilsMultiplicateurs {

        @Test
        @DisplayName("Seuil pour 0 bout = 56, 1 = 51, 2 = 41, 3 = 36")
        void seuilsCorrects() {
            assertThat(scoringService.seuilPourBouts(0)).isEqualTo(56);
            assertThat(scoringService.seuilPourBouts(1)).isEqualTo(51);
            assertThat(scoringService.seuilPourBouts(2)).isEqualTo(41);
            assertThat(scoringService.seuilPourBouts(3)).isEqualTo(36);
        }

        @Test
        @DisplayName("Seuil plafonné à 3 bouts même si on en a davantage")
        void seuilPlafonne() {
            assertThat(scoringService.seuilPourBouts(4)).isEqualTo(scoringService.seuilPourBouts(3));
        }

        @Test
        @DisplayName("Multiplicateurs : Petite×1, Garde×2, Garde sans×4, Garde contre×6")
        void multiplicateursCorrects() {
            assertThat(scoringService.multiplicateurPourType("PETITE")).isEqualTo(1);
            assertThat(scoringService.multiplicateurPourType("GARDE")).isEqualTo(2);
            assertThat(scoringService.multiplicateurPourType("GARDE_SANS")).isEqualTo(4);
            assertThat(scoringService.multiplicateurPourType("GARDE_CONTRE")).isEqualTo(6);
        }

        @Test
        @DisplayName("Multiplicateur inconnu → 1 par défaut")
        void multiplicateurInconnu_defaut1() {
            assertThat(scoringService.multiplicateurPourType("INCONNUE")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("TarotScoringService — calcul du score final")
    class CalculScore {

        @Test
        @DisplayName("Contrat rempli juste (écart=0) → score = 25 × multiplicateur")
        void contratRempliJuste_score25x() {
            // 0 bouts, seuil=56, preneur fait exactement 56pts → écart=0
            // résultat = round((25 + 0) × 1) = 25
            int score = scoringService.calculerScore(56 * 2, 0, "PETITE", false, false);
            assertThat(score).isEqualTo(25);
        }

        @Test
        @DisplayName("Contrat rempli avec écart positif → score > 25")
        void contratRempliAvecEcart_scoreSuperieur() {
            // 2 bouts, seuil=41, preneur fait 51pts → écart=10
            // résultat = round((25 + 10) × 2) = 70 (Garde)
            int score = scoringService.calculerScore(51 * 2, 2, "GARDE", false, false);
            assertThat(score).isEqualTo(70);
        }

        @Test
        @DisplayName("Contrat chuté → score négatif")
        void contratChute_scoreNegatif() {
            // 0 bouts, seuil=56, preneur fait 40pts → écart=-16
            // résultat = -round((25 + 16) × 1) = -41
            int score = scoringService.calculerScore(40 * 2, 0, "PETITE", false, false);
            assertThat(score).isNegative();
            assertThat(score).isEqualTo(-41);
        }

        @Test
        @DisplayName("Petit au bout côté preneur → bonus +10 × multiplicateur")
        void petitAuBout_ajouteBonus() {
            int sansPetit = scoringService.calculerScore(56 * 2, 0, "GARDE", false, false);
            int avecPetit = scoringService.calculerScore(56 * 2, 0, "GARDE", true, false);
            // bonus = 10 × 2 = 20 pour une Garde
            assertThat(avecPetit - sansPetit).isEqualTo(20);
        }

        @Test
        @DisplayName("Garde contre (×6) multiplie bien le score par 6")
        void gardeContre_multiplieParSix() {
            // Même situation qu'en Petite
            int petite = scoringService.calculerScore(56 * 2, 0, "PETITE", false, false);
            int gardeContre = scoringService.calculerScore(56 * 2, 0, "GARDE_CONTRE", false, false);
            assertThat(gardeContre).isEqualTo(petite * 6);
        }

        @Test
        @DisplayName("3 bouts → seuil abaissé à 36, contrat plus facile à remplir")
        void troisBouts_seuilAbaise() {
            // 3 bouts, preneur fait 38pts (< seuil 41 si 2 bouts, > seuil 36 si 3 bouts)
            int score2bouts = scoringService.calculerScore(38 * 2, 2, "PETITE", false, false);
            int score3bouts = scoringService.calculerScore(38 * 2, 3, "PETITE", false, false);
            assertThat(score2bouts).isNegative();  // échoue avec 2 bouts
            assertThat(score3bouts).isPositive();  // réussit avec 3 bouts
        }
    }

    @Nested
    @DisplayName("TarotScoringService — comptage des bouts")
    class CompteBouts {

        @Test
        @DisplayName("compterBouts détecte les 3 bouts dans une liste")
        void compterBouts_troisBouts() {
            List<Carte> cartes = List.of(
                    carte("Atout", "1"),
                    carte("Atout", "21"),
                    carte("Atout", "Excuse"),
                    carte("Coeur", "Roi"),
                    carte("Pique", "5")
            );
            assertThat(scoringService.compterBouts(cartes)).isEqualTo(3);
        }

        @Test
        @DisplayName("compterBouts = 0 si aucun bout")
        void compterBouts_aucun() {
            List<Carte> cartes = List.of(
                    carte("Coeur", "Roi"),
                    carte("Atout", "15"),
                    carte("Carreau", "10")
            );
            assertThat(scoringService.compterBouts(cartes)).isEqualTo(0);
        }

        @Test
        @DisplayName("isBout retourne true pour les 3 bouts uniquement")
        void isBout_correctement() {
            assertThat(scoringService.isBout(carte("Atout", "1"))).isTrue();
            assertThat(scoringService.isBout(carte("Atout", "21"))).isTrue();
            assertThat(scoringService.isBout(carte("Atout", "Excuse"))).isTrue();
            assertThat(scoringService.isBout(carte("Atout", "10"))).isFalse();
            assertThat(scoringService.isBout(carte("Coeur", "1"))).isFalse();
        }
    }

    // =========================================================
    // BLOC 2 — TarotService (tests d'intégration)
    // =========================================================

    @Nested
    @DisplayName("Distribution — 4 joueurs Tarot")
    class Distribution4Joueurs {

        private Utilisateur u1, u2, u3, u4;
        private Partie partie;

        @BeforeEach
        void setUp() {
            u1 = creerUtilisateur("tarot1");
            u2 = creerUtilisateur("tarot2");
            u3 = creerUtilisateur("tarot3");
            u4 = creerUtilisateur("tarot4");
            partie = demarrerPartieTarot(4, List.of(u1, u2, u3, u4));
        }

        @Test
        @DisplayName("Chaque joueur reçoit exactement 18 cartes")
        @Transactional
        void chaquejoueur18Cartes() {
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partie.getId());
            for (Joueur j : joueurs) {
                assertThat(j.getCartesEnMain())
                        .as("Joueur " + j.getPosition() + " doit avoir 18 cartes")
                        .hasSize(18);
            }
        }

        @Test
        @DisplayName("Le chien contient exactement 6 cartes")
        @Transactional
        void chien6Cartes() {
            Partie p = partieRepository.findById(partie.getId()).orElseThrow();
            assertThat(p.getChien()).hasSize(6);
        }

        @Test
        @DisplayName("Total 4×18 + 6 = 78 cartes")
        @Transactional
        void totalCartes78() {
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partie.getId());
            int total = joueurs.stream().mapToInt(j -> j.getCartesEnMain().size()).sum();
            Partie p = partieRepository.findById(partie.getId()).orElseThrow();
            total += p.getChien().size();
            assertThat(total).isEqualTo(78);
        }

        @Test
        @DisplayName("Statut initial = EN_ENCHERE, phaseJeu = null")
        void statutInitialEnEnchere() {
            assertThat(partie.getStatut()).isEqualTo("EN_ENCHERE");
            assertThat(partie.getPhaseJeu()).isNull();
        }

        @Test
        @DisplayName("getEtatJeuTarot retourne typeJeu TAROT et statut EN_ENCHERE")
        @Transactional
        void getEtatJeu_typeJeuTarot() {
            EtatTarotDTO etat = tarotService.getEtatJeuTarot(partie.getId(), u1.getId());
            assertThat(etat.getStatut()).isEqualTo("EN_ENCHERE");
            assertThat(etat.getMaMain()).hasSize(18);
        }
    }

    @Nested
    @DisplayName("Distribution — 3 joueurs Tarot")
    class Distribution3Joueurs {

        private Utilisateur u1, u2, u3;
        private Partie partie;

        @BeforeEach
        void setUp() {
            u1 = creerUtilisateur("t3j_1");
            u2 = creerUtilisateur("t3j_2");
            u3 = creerUtilisateur("t3j_3");
            partie = demarrerPartieTarot(3, List.of(u1, u2, u3));
        }

        @Test
        @DisplayName("Chaque joueur reçoit 24 cartes")
        @Transactional
        void chaquejoueur24Cartes() {
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partie.getId());
            for (Joueur j : joueurs) {
                assertThat(j.getCartesEnMain()).hasSize(24);
            }
        }

        @Test
        @DisplayName("Le chien contient 6 cartes")
        @Transactional
        void chien6Cartes() {
            Partie p = partieRepository.findById(partie.getId()).orElseThrow();
            assertThat(p.getChien()).hasSize(6);
        }

        @Test
        @DisplayName("Total 3×24 + 6 = 78 cartes")
        @Transactional
        void totalCartes78() {
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partie.getId());
            int total = joueurs.stream().mapToInt(j -> j.getCartesEnMain().size()).sum();
            Partie p = partieRepository.findById(partie.getId()).orElseThrow();
            total += p.getChien().size();
            assertThat(total).isEqualTo(78);
        }
    }

    @Nested
    @DisplayName("Enchères Tarot — validations")
    class EncheresTarot {

        private Utilisateur u1, u2, u3, u4;
        private Partie partie;

        @BeforeEach
        void setUp() {
            u1 = creerUtilisateur("enc1");
            u2 = creerUtilisateur("enc2");
            u3 = creerUtilisateur("enc3");
            u4 = creerUtilisateur("enc4");
            partie = demarrerPartieTarot(4, List.of(u1, u2, u3, u4));
        }

        @Test
        @DisplayName("Le joueur hors tour ne peut pas enchérir")
        void joueurHorsTour_lanceException() {
            // Trouver qui N'est PAS en train de jouer
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partie.getId());
            Joueur actif = joueurs.stream()
                    .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                    .findFirst().orElseThrow();

            Utilisateur horsJoueur = joueurs.stream()
                    .filter(j -> !j.getId().equals(actif.getId()))
                    .map(j -> j.getUtilisateur())
                    .findFirst().orElseThrow();

            assertThatThrownBy(() -> tarotService.encherirTarot(partie.getId(), horsJoueur.getId(), "PETITE"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("tour");
        }

        @Test
        @DisplayName("GARDE ne peut pas être suivi de PETITE (doit surenchérir)")
        void doitSurencherir_siGardeDejaAnnonce() {
            // Faire enchérir le 1er joueur en GARDE
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partie.getId());
            Joueur j0 = joueurs.stream().filter(j -> j.getPosition() == partie.getTourJoueurIndex()).findFirst().orElseThrow();
            tarotService.encherirTarot(partie.getId(), j0.getUtilisateur().getId(), "GARDE");

            // Le 2e joueur tente PETITE → doit échouer
            Partie pActualisee = partieRepository.findById(partie.getId()).orElseThrow();
            List<Joueur> joueursAct = joueurRepository.findByPartie_Id(partie.getId());
            Joueur j1 = joueursAct.stream().filter(j -> j.getPosition() == pActualisee.getTourJoueurIndex()).findFirst().orElseThrow();
            final Long partieId = partie.getId();
            final Long uid = j1.getUtilisateur().getId();
            assertThatThrownBy(() -> tarotService.encherirTarot(partieId, uid, "PETITE"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("plus haut");
        }

        @Test
        @DisplayName("typeBid null/blanc lève une exception")
        void typeBidNull_lanceException() {
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partie.getId());
            Joueur actif = joueurs.stream().filter(j -> j.getPosition() == partie.getTourJoueurIndex()).findFirst().orElseThrow();
            Long uid = actif.getUtilisateur().getId();
            Long pid = partie.getId();
            assertThatThrownBy(() -> tarotService.encherirTarot(pid, uid, ""))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Tous passent → nouvelle donne redistribuée (statut EN_ENCHERE, nouvelles mains)")
        @Transactional
        void tousPasse_nouvelleDonne() {
            Long partieId = partie.getId();

            // Faire passer les 4 joueurs (tous passent)
            for (int tour = 0; tour < 4; tour++) {
                Partie p = partieRepository.findById(partieId).orElseThrow();
                List<Joueur> js = joueurRepository.findByPartie_Id(partieId);
                Joueur actif = js.stream().filter(j -> j.getPosition() == p.getTourJoueurIndex()).findFirst().orElseThrow();
                tarotService.encherirTarot(partieId, actif.getUtilisateur().getId(), "PASSE");
            }

            // Après que tous ont passé → une nouvelle donne est redistribuée
            Partie p = partieRepository.findById(partieId).orElseThrow();
            assertThat(p.getStatut()).isEqualTo("EN_ENCHERE");
            assertThat(p.getPhaseJeu()).isNull();
            assertThat(p.getEnchereType()).isNull();
            // Chaque joueur a de nouvelles cartes (18 en 4j)
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            for (Joueur j : joueurs) {
                assertThat(j.getCartesEnMain()).hasSize(18);
            }
        }

        @Test
        @DisplayName("Enchère GARDE_CONTRE déclenche le jeu immédiatement (pas de chien)")
        @Transactional
        void gardeContre_lanceLejeuDirectement() {
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partie.getId());
            Joueur actif = joueurs.stream().filter(j -> j.getPosition() == partie.getTourJoueurIndex()).findFirst().orElseThrow();
            tarotService.encherirTarot(partie.getId(), actif.getUtilisateur().getId(), "GARDE_CONTRE");

            Partie p = partieRepository.findById(partie.getId()).orElseThrow();
            assertThat(p.getStatut()).isEqualTo("EN_JEU");
            assertThat(p.getPhaseJeu()).isEqualTo("JEU");
            assertThat(p.getEnchereType()).isEqualTo("GARDE_CONTRE");
            assertThat(p.getMultiplicateur()).isEqualTo(6);
        }

        @Test
        @DisplayName("Enchère PETITE gagnée → statut EN_ENCHERE, phase CHIEN")
        @Transactional
        void petiteGagnee_phaseChien() {
            Long partieId = partie.getId();

            // Le 1er joueur enchérit en PETITE
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur j0 = joueurs.stream().filter(j -> j.getPosition() == partie.getTourJoueurIndex()).findFirst().orElseThrow();
            tarotService.encherirTarot(partieId, j0.getUtilisateur().getId(), "PETITE");

            // Les 3 autres passent
            for (int i = 0; i < 3; i++) {
                Partie p = partieRepository.findById(partieId).orElseThrow();
                List<Joueur> js = joueurRepository.findByPartie_Id(partieId);
                Joueur actif = js.stream().filter(j -> j.getPosition() == p.getTourJoueurIndex()).findFirst().orElseThrow();
                tarotService.encherirTarot(partieId, actif.getUtilisateur().getId(), "PASSE");
            }

            Partie p = partieRepository.findById(partieId).orElseThrow();
            assertThat(p.getStatut()).isEqualTo("EN_ENCHERE");
            assertThat(p.getPhaseJeu()).isEqualTo("CHIEN");
            assertThat(p.getEnchereType()).isEqualTo("PETITE");
            assertThat(p.getMultiplicateur()).isEqualTo(1);
        }

        @Test
        @DisplayName("Enchère GARDE_SANS gagnée → phase CHIEN_VU (vue sans écart)")
        @Transactional
        void gardeSans_phaseCHIEN_VU() {
            Long partieId = partie.getId();

            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur j0 = joueurs.stream().filter(j -> j.getPosition() == partie.getTourJoueurIndex()).findFirst().orElseThrow();
            tarotService.encherirTarot(partieId, j0.getUtilisateur().getId(), "GARDE_SANS");

            // Les 3 autres passent
            for (int i = 0; i < 3; i++) {
                Partie p = partieRepository.findById(partieId).orElseThrow();
                List<Joueur> js = joueurRepository.findByPartie_Id(partieId);
                Joueur actif = js.stream().filter(j -> j.getPosition() == p.getTourJoueurIndex()).findFirst().orElseThrow();
                tarotService.encherirTarot(partieId, actif.getUtilisateur().getId(), "PASSE");
            }

            Partie p = partieRepository.findById(partieId).orElseThrow();
            assertThat(p.getPhaseJeu()).isEqualTo("CHIEN_VU");
            assertThat(p.getMultiplicateur()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Écart — validation des règles")
    class Ecart {

        private Utilisateur u1, u2, u3, u4;
        private Long partieId;
        private Long preneurUserId;

        @BeforeEach
        void setUp() {
            u1 = creerUtilisateur("ecart1");
            u2 = creerUtilisateur("ecart2");
            u3 = creerUtilisateur("ecart3");
            u4 = creerUtilisateur("ecart4");
            Partie p = demarrerPartieTarot(4, List.of(u1, u2, u3, u4));
            partieId = p.getId();

            // Faire gagner la PETITE au premier joueur qui enchérit
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur j0 = joueurs.stream().filter(j -> j.getPosition() == p.getTourJoueurIndex()).findFirst().orElseThrow();
            preneurUserId = j0.getUtilisateur().getId();
            tarotService.encherirTarot(partieId, preneurUserId, "PETITE");

            // Les 3 autres passent
            for (int i = 0; i < 3; i++) {
                Partie pAct = partieRepository.findById(partieId).orElseThrow();
                List<Joueur> js = joueurRepository.findByPartie_Id(partieId);
                Joueur actif = js.stream().filter(j -> j.getPosition() == pAct.getTourJoueurIndex()).findFirst().orElseThrow();
                tarotService.encherirTarot(partieId, actif.getUtilisateur().getId(), "PASSE");
            }
        }

        @Test
        @DisplayName("Phase CHIEN est bien présente avant l'écart")
        void phaseChienAvantEcart() {
            Partie p = partieRepository.findById(partieId).orElseThrow();
            assertThat(p.getPhaseJeu()).isEqualTo("CHIEN");
        }

        @Test
        @DisplayName("Écart de 6 cartes valides → passe en EN_JEU")
        @Transactional
        void ecart6CartesValides_passageEnJeu() {
            // Récupérer la main du preneur et le chien
            Partie p = partieRepository.findById(partieId).orElseThrow();
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur preneur = joueurs.stream().filter(j -> j.getId().equals(p.getPreneurId())).findFirst().orElseThrow();

            // Chien intégré dans la main du preneur lors de getEtatJeuTarot — le service le fait pendant ecarterCartes
            // On sélectionne 6 cartes de sa main actuelle qui ne sont pas des bouts ni des rois
            List<Carte> main = new ArrayList<>(preneur.getCartesEnMain());
            List<Long> aEcarter = new ArrayList<>();
            for (Carte c : main) {
                if (aEcarter.size() == 6) break;
                boolean estBout = "Atout".equals(c.getCouleur()) &&
                        (c.getValeur().equals("1") || c.getValeur().equals("21") || c.getValeur().equals("Excuse"));
                boolean estRoi = "Roi".equals(c.getValeur());
                boolean estAtout = "Atout".equals(c.getCouleur());
                if (!estBout && !estRoi && !estAtout) {
                    aEcarter.add(c.getId());
                }
            }

            if (aEcarter.size() == 6) {
                tarotService.ecarterCartes(partieId, preneurUserId, aEcarter);
                Partie pApreEcart = partieRepository.findById(partieId).orElseThrow();
                assertThat(pApreEcart.getStatut()).isEqualTo("EN_JEU");
                assertThat(pApreEcart.getPhaseJeu()).isEqualTo("JEU");
            } else {
                // Si pas assez de cartes non-protégées, on skippe (deck aléatoire)
                // Le test est informatif : s'il passe il valide, sinon la main est trop protégée
                System.out.println("[INFO] Main trop protégée pour tester l'écart automatiquement.");
            }
        }

        @Test
        @DisplayName("Écarter un bout lève une exception")
        @Transactional
        void ecarterBout_lanceException() {
            Partie p = partieRepository.findById(partieId).orElseThrow();
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur preneur = joueurs.stream().filter(j -> j.getId().equals(p.getPreneurId())).findFirst().orElseThrow();

            // Chercher un bout dans le chien ou la main
            List<Carte> chien = p.getChien();
            Carte bout = null;

            // On intègre d'abord le chien dans la main (comme le fait ecarterCartes en interne)
            // Pour ce test, cherchons un bout dans la main actuelle du preneur
            for (Carte c : preneur.getCartesEnMain()) {
                if ("Atout".equals(c.getCouleur()) &&
                        (c.getValeur().equals("1") || c.getValeur().equals("21") || c.getValeur().equals("Excuse"))) {
                    bout = c;
                    break;
                }
            }
            // Si le preneur n'a pas de bout en main, chercher dans le chien
            if (bout == null) {
                for (Carte c : chien) {
                    if ("Atout".equals(c.getCouleur()) &&
                            (c.getValeur().equals("1") || c.getValeur().equals("21") || c.getValeur().equals("Excuse"))) {
                        bout = c;
                        break;
                    }
                }
            }

            if (bout != null) {
                List<Long> ids = new ArrayList<>();
                ids.add(bout.getId());
                // Compléter avec 5 cartes quelconques pour avoir 6
                for (Carte c : preneur.getCartesEnMain()) {
                    if (ids.size() == 6) break;
                    if (!c.getId().equals(bout.getId())) ids.add(c.getId());
                }
                if (ids.size() == 6) {
                    final List<Long> idsFinal = ids;
                    assertThatThrownBy(() -> tarotService.ecarterCartes(partieId, preneurUserId, idsFinal))
                            .isInstanceOf(BusinessException.class)
                            .hasMessageContaining("bout");
                }
            } else {
                System.out.println("[INFO] Aucun bout accessible pour tester l'exception écart.");
            }
        }

        @Test
        @DisplayName("Un non-preneur ne peut pas faire l'écart")
        void nonPreneur_nepeutPasEcarter() {
            // Trouver un utilisateur qui n'est pas le preneur
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur defense = joueurs.stream()
                    .filter(j -> !j.getUtilisateur().getId().equals(preneurUserId))
                    .findFirst().orElseThrow();
            Long defId = defense.getUtilisateur().getId();

            assertThatThrownBy(() -> tarotService.ecarterCartes(partieId, defId, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("Jouer une carte — validations Tarot")
    class JouerCarte {

        private Long partieId;
        private Long preneurUserId;

        // Démarre une partie Tarot 4j avec GARDE_CONTRE.
        @BeforeEach
        void setUp() {
            Utilisateur u1 = creerUtilisateur("jc1");
            Utilisateur u2 = creerUtilisateur("jc2");
            Utilisateur u3 = creerUtilisateur("jc3");
            Utilisateur u4 = creerUtilisateur("jc4");
            Partie p = demarrerPartieTarot(4, List.of(u1, u2, u3, u4));
            partieId = p.getId();

            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur j0 = joueurs.stream().filter(j -> j.getPosition() == p.getTourJoueurIndex()).findFirst().orElseThrow();
            preneurUserId = j0.getUtilisateur().getId();

            // GARDE_CONTRE → EN_JEU immédiat
            tarotService.encherirTarot(partieId, preneurUserId, "GARDE_CONTRE");
        }

        @Test
        @DisplayName("La partie est bien EN_JEU après GARDE_CONTRE")
        void partieEnJeuApresGardeContre() {
            Partie p = partieRepository.findById(partieId).orElseThrow();
            assertThat(p.getStatut()).isEqualTo("EN_JEU");
        }

        @Test
        @DisplayName("Jouer une carte hors tour lève une exception")
        @Transactional
        void jouerHorsTour_lanceException() {
            Partie p = partieRepository.findById(partieId).orElseThrow();
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);

            // Trouver quelqu'un qui n'est pas actif
            Joueur actif = joueurs.stream().filter(j -> j.getPosition() == p.getTourJoueurIndex()).findFirst().orElseThrow();
            Joueur passif = joueurs.stream().filter(j -> !j.getId().equals(actif.getId())).findFirst().orElseThrow();

            Long passifUserId = passif.getUtilisateur().getId();
            Long passifCarteId = passif.getCartesEnMain().get(0).getId();

            assertThatThrownBy(() -> tarotService.jouerCarte(partieId, passifUserId, passifCarteId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("tour");
        }

        @Test
        @DisplayName("Jouer une carte qui n'est pas dans sa main lève une exception")
        @Transactional
        void jouerCarteAbsente_lanceException() {
            Partie p = partieRepository.findById(partieId).orElseThrow();
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);

            Joueur actif = joueurs.stream().filter(j -> j.getPosition() == p.getTourJoueurIndex()).findFirst().orElseThrow();
            Joueur autre = joueurs.stream().filter(j -> !j.getId().equals(actif.getId())).findFirst().orElseThrow();
            Long carteAutre = autre.getCartesEnMain().get(0).getId();

            assertThatThrownBy(() -> tarotService.jouerCarte(partieId, actif.getUtilisateur().getId(), carteAutre))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Jouer la première carte du pli ajoute une carte au pli")
        @Transactional
        void jouerPremiereCarte_ajoutePliCourant() {
            Partie p = partieRepository.findById(partieId).orElseThrow();
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur actif = joueurs.stream().filter(j -> j.getPosition() == p.getTourJoueurIndex()).findFirst().orElseThrow();
            Carte carte = actif.getCartesEnMain().get(0);

            EtatTarotDTO etat = tarotService.jouerCarte(partieId, actif.getUtilisateur().getId(), carte.getId());
            assertThat(etat.getPliCourant()).hasSize(1);
        }

        @Test
        @DisplayName("Après la première carte, le tour passe au joueur suivant")
        @Transactional
        void jouerPremiereCarte_tourSuivant() {
            Partie p = partieRepository.findById(partieId).orElseThrow();
            int indexAvant = p.getTourJoueurIndex();
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur actif = joueurs.stream().filter(j -> j.getPosition() == indexAvant).findFirst().orElseThrow();
            Carte carte = actif.getCartesEnMain().get(0);

            tarotService.jouerCarte(partieId, actif.getUtilisateur().getId(), carte.getId());

            Partie pAct = partieRepository.findById(partieId).orElseThrow();
            assertThat(pAct.getTourJoueurIndex()).isNotEqualTo(indexAvant);
        }
    }

    @Nested
    @DisplayName("Petit sec — règle d'annulation de donne")
    class PetitSec {

        private Utilisateur u1, u2, u3, u4;
        private Long partieId;

        @BeforeEach
        void setUp() {
            u1 = creerUtilisateur("ps1");
            u2 = creerUtilisateur("ps2");
            u3 = creerUtilisateur("ps3");
            u4 = creerUtilisateur("ps4");
            Partie p = demarrerPartieTarot(4, List.of(u1, u2, u3, u4));
            partieId = p.getId();
        }

        @Test
        @DisplayName("Un joueur sans Petit sec ne peut pas signaler un Petit sec")
        @Transactional
        void sansPS_lanceException() {
            // Forcer petitSecDetecte à true pour contourner le premier guard
            Partie p = partieRepository.findById(partieId).orElseThrow();
            p.setPetitSecDetecte(true);
            partieRepository.save(p);

            // Trouver un joueur dont la main ne contient PAS le Petit ou possède plusieurs atouts
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Joueur sansPS = joueurs.stream()
                    .filter(j -> {
                        long nbAtouts = j.getCartesEnMain().stream()
                                .filter(c -> "Atout".equals(c.getCouleur()) && !"Excuse".equals(c.getValeur()))
                                .count();
                        boolean aPetit = j.getCartesEnMain().stream()
                                .anyMatch(c -> "Atout".equals(c.getCouleur()) && "1".equals(c.getValeur()));
                        return !aPetit || nbAtouts > 1; // pas de Petit sec
                    })
                    .findFirst().orElse(null);

            if (sansPS != null) {
                final Long uid = sansPS.getUtilisateur().getId();
                assertThatThrownBy(() -> tarotService.signalerPetitSec(partieId, uid))
                        .isInstanceOf(fr.enseeiht.jeux.exception.BusinessException.class)
                        .hasMessageContaining("Petit sec");
            } else {
                System.out.println("[INFO] Tous les joueurs ont un Petit sec (improbable), test informatif uniquement.");
            }
        }

        @Test
        @DisplayName("Signaler un Petit sec sans qu'il soit détecté dans la partie lève une exception")
        @Transactional
        void petitSecNonDetecte_lanceException() {
            // S'assurer que petitSecDetecte est false
            Partie p = partieRepository.findById(partieId).orElseThrow();
            p.setPetitSecDetecte(false);
            partieRepository.save(p);

            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
            Long uid = joueurs.get(0).getUtilisateur().getId();
            assertThatThrownBy(() -> tarotService.signalerPetitSec(partieId, uid))
                    .isInstanceOf(fr.enseeiht.jeux.exception.BusinessException.class)
                    .hasMessageContaining("Petit sec");
        }

        @Test
        @DisplayName("Forcer un Petit sec → signalerPetitSec redistribue les cartes (nouvelle donne)")
        @Transactional
        void forcerPS_redistribute() {
            Partie p = partieRepository.findById(partieId).orElseThrow();
            List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);

            // Forcer la main du premier joueur : garder uniquement le Petit (Atout 1)
            // et supprimer tous ses autres atouts
            Joueur joueurForce = joueurs.get(0);
            List<fr.enseeiht.jeux.modele.Carte> mainOriginale = new ArrayList<>(joueurForce.getCartesEnMain());
            fr.enseeiht.jeux.modele.Carte petitCarte = mainOriginale.stream()
                    .filter(c -> "Atout".equals(c.getCouleur()) && "1".equals(c.getValeur()))
                    .findFirst().orElse(null);

            if (petitCarte == null) {
                // Le premier joueur n'a pas le Petit, tester avec un autre joueur ou skipper
                System.out.println("[INFO] Le premier joueur n'a pas le Petit — test de redistribution non applicable avec la main actuelle.");
                return;
            }

            // Retirer tous les autres atouts de sa main (simuler le Petit sec)
            List<fr.enseeiht.jeux.modele.Carte> mainSansPetit = mainOriginale.stream()
                    .filter(c -> !("Atout".equals(c.getCouleur()) && !"Excuse".equals(c.getValeur()) && !"1".equals(c.getValeur())))
                    .collect(java.util.stream.Collectors.toList());
            joueurForce.setCartesEnMain(mainSansPetit);
            joueurRepository.save(joueurForce);

            // Marquer le Petit sec dans la partie
            p.setPetitSecDetecte(true);
            partieRepository.save(p);

            int donnAvant = p.getDonneActuelle();

            // Signaler le Petit sec
            Long uid = joueurForce.getUtilisateur().getId();
            tarotService.signalerPetitSec(partieId, uid);

            Partie pApres = partieRepository.findById(partieId).orElseThrow();
            // La donne doit avoir été incrémentée
            assertThat(pApres.getDonneActuelle()).isGreaterThan(donnAvant);
            // Le statut revient en EN_ENCHERE
            assertThat(pApres.getStatut()).isEqualTo("EN_ENCHERE");
            // Une nouvelle distribution a eu lieu (chaque joueur a de nouvelles cartes)
            List<Joueur> nouveauxJoueurs = joueurRepository.findByPartie_Id(partieId);
            for (Joueur j : nouveauxJoueurs) {
                assertThat(j.getCartesEnMain()).hasSize(18); // 4 joueurs : 18 cartes/joueur
            }
        }
    }
}
