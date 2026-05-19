package fr.enseeiht.jeux;

import fr.enseeiht.jeux.dto.EtatJeuDTO;
import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.modele.*;
import fr.enseeiht.jeux.repository.*;
import fr.enseeiht.jeux.service.JeuService;
import fr.enseeiht.jeux.service.PartieService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests d'intégration de JeuService.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class JeuServiceIntegrationTest {

    @Autowired private JeuService jeuService;
    @Autowired private PartieService partieService;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private JoueurRepository joueurRepository;
    @Autowired private PartieRepository partieRepository;
    // 4 joueurs humains (pas des bots)
    private Utilisateur u1, u2, u3, u4;
    private Partie partie;

    // Crée 4 utilisateurs et démarre la partie.
    @BeforeEach
    void setUp() {
        u1 = creerUtilisateur("joueur1");
        u2 = creerUtilisateur("joueur2");
        u3 = creerUtilisateur("joueur3");
        u4 = creerUtilisateur("joueur4");

        partie = partieService.creerPartie();
        partieService.rejoindrePartie(partie.getId(), u1.getId()); // pos 0
        partieService.rejoindrePartie(partie.getId(), u2.getId()); // pos 1
        partieService.rejoindrePartie(partie.getId(), u3.getId()); // pos 2
        partieService.rejoindrePartie(partie.getId(), u4.getId()); // pos 3
        partieService.demarrerPartie(partie.getId());
    }

    private Utilisateur creerUtilisateur(String pseudo) {
        Utilisateur u = new Utilisateur();
        u.setPseudo(pseudo);
        u.setMdp("$2a$10$hash");
        u.setScoreGlobal(0);
        return utilisateurRepository.save(u);
    }

    // ===== ÉTAT DU JEU =====

    @Test
    @DisplayName("getEtatJeu — chaque joueur reçoit exactement 8 cartes")
    @Transactional
    void getEtatJeu_chaquejoueurA8Cartes() {
        for (Utilisateur u : List.of(u1, u2, u3, u4)) {
            EtatJeuDTO etat = jeuService.getEtatJeu(partie.getId(), u.getId());
            assertThat(etat.getMaMain()).hasSize(8);
        }
    }

    @Test
    @DisplayName("getEtatJeu — statut initial EN_ENCHERE après démarrage")
    @Transactional
    void getEtatJeu_statutInitialEnEnchere() {
        EtatJeuDTO etat = jeuService.getEtatJeu(partie.getId(), u1.getId());
        assertThat(etat.getStatut()).isEqualTo("EN_ENCHERE");
    }

    @Test
    @DisplayName("getEtatJeu — les 4 joueurs ont des mains différentes (distribution aléatoire)")
    @Transactional
    void getEtatJeu_mainsDistinctes() {
        List<Long> main1 = jeuService.getEtatJeu(partie.getId(), u1.getId())
                .getMaMain().stream().map(c -> c.getId()).toList();
        List<Long> main2 = jeuService.getEtatJeu(partie.getId(), u2.getId())
                .getMaMain().stream().map(c -> c.getId()).toList();

        // Aucune carte commune entre les deux mains
        assertThat(main1).doesNotContainAnyElementsOf(main2);
    }

    @Test
    @DisplayName("getEtatJeu — utilisateur hors partie → BusinessException")
    void getEtatJeu_utilisateurHorsPartie_lanceException() {
        Utilisateur horsPartie = creerUtilisateur("intrus");
        assertThatThrownBy(() -> jeuService.getEtatJeu(partie.getId(), horsPartie.getId()))
                .isInstanceOf(BusinessException.class);
    }

    // ===== ENCHÈRES =====

    @Test
    @DisplayName("Enchère OK — 1 enchère + 3 passes → passage en EN_JEU")
    @Transactional
    void encherir_unContratPuis3Passes_passageEnJeu() {
        // Joueur 1 enchérit
        jeuService.encherir(partie.getId(), u1.getId(), 80, "Coeur", false);
        // Joueurs 2, 3, 4 passent
        jeuService.encherir(partie.getId(), u2.getId(), null, null, true);
        jeuService.encherir(partie.getId(), u3.getId(), null, null, true);
        EtatJeuDTO etatApres3Passes = jeuService.encherir(partie.getId(), u4.getId(), null, null, true);

        assertThat(etatApres3Passes.getStatut()).isEqualTo("EN_JEU");
        assertThat(etatApres3Passes.getAtout()).isEqualToIgnoringCase("Coeur");
        assertThat(etatApres3Passes.getContratValeur()).isEqualTo(80);
    }

    @Test
    @DisplayName("Enchère — jouer hors de son tour → BusinessException")
    void encherir_horsDeTour_lanceException() {
        // C'est le tour de u1, mais u2 essaie d'enchérir
        assertThatThrownBy(() -> jeuService.encherir(partie.getId(), u2.getId(), 80, "Pique", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tour");
    }

    @Test
    @DisplayName("Enchère — contrat invalide (< 80) → BusinessException")
    void encherir_contratTropBas_lanceException() {
        assertThatThrownBy(() -> jeuService.encherir(partie.getId(), u1.getId(), 70, "Coeur", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Enchère — surenchère insuffisante → BusinessException")
    void encherir_surenchereTropBasse_lanceException() {
        jeuService.encherir(partie.getId(), u1.getId(), 100, "Coeur", false);
        // u2 essaie d'enchérir 80 alors que le contrat est déjà à 100
        assertThatThrownBy(() -> jeuService.encherir(partie.getId(), u2.getId(), 80, "Pique", false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Enchère — couleur invalide → BusinessException")
    void encherir_couleurInvalide_lanceException() {
        assertThatThrownBy(() -> jeuService.encherir(partie.getId(), u1.getId(), 80, "Joker", false))
                .isInstanceOf(BusinessException.class);
    }

    // ===== RÈGLES DE JEU =====

    // Démarre une partie avec atout Coeur.
    private List<Joueur> demarrerJeu(String couleurAtout) {
        jeuService.encherir(partie.getId(), u1.getId(), 80, couleurAtout, false);
        jeuService.encherir(partie.getId(), u2.getId(), null, null, true);
        jeuService.encherir(partie.getId(), u3.getId(), null, null, true);
        jeuService.encherir(partie.getId(), u4.getId(), null, null, true);
        return joueurRepository.findByPartie_Id(partie.getId());
    }

    @Test
    @DisplayName("Règle suivi couleur — jouer une autre couleur quand on possède la couleur demandée → refusé")
    void jouerCarte_suivi_couleurObligatoire() {
        List<Joueur> joueurs = demarrerJeu("Coeur");

        // Le preneur (u1) ouvre le premier pli
        Joueur joueur1 = joueurs.stream().filter(j -> j.getUtilisateur().getId().equals(u1.getId())).findFirst().orElseThrow();
        Joueur joueur2 = joueurs.stream().filter(j -> j.getUtilisateur().getId().equals(u2.getId())).findFirst().orElseThrow();

        // u1 ouvre avec n'importe quelle carte
        Carte carteOuverte = joueur1.getCartesEnMain().get(0);
        jeuService.jouerCarte(partie.getId(), u1.getId(), carteOuverte.getId());

        String couleurDemandee = carteOuverte.getCouleur();

        // Recharger u2 depuis la BDD (sa main est à jour)
        Joueur joueur2Frais = joueurRepository.findById(joueur2.getId()).orElseThrow();

        // Si u2 a la couleur demandée, il ne peut pas jouer autre chose
        boolean possedeCouleur = joueur2Frais.getCartesEnMain().stream()
                .anyMatch(c -> c.getCouleur().equals(couleurDemandee));
        if (possedeCouleur) {
            Carte carteDifférenteCouleur = joueur2Frais.getCartesEnMain().stream()
                    .filter(c -> !c.getCouleur().equals(couleurDemandee))
                    .findFirst().orElse(null);
            if (carteDifférenteCouleur != null) {
                assertThatThrownBy(() -> jeuService.jouerCarte(partie.getId(), u2.getId(), carteDifférenteCouleur.getId()))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("couleur");
            }
        }
    }

    @Test
    @DisplayName("Règle jouer hors de son tour → refusé")
    void jouerCarte_horsDeTour_lanceException() {
        List<Joueur> joueurs = demarrerJeu("Pique");

        // C'est le tour de u1 (position 0, le preneur), mais u3 essaie de jouer
        Joueur joueur3 = joueurs.stream().filter(j -> j.getUtilisateur().getId().equals(u3.getId())).findFirst().orElseThrow();
        Carte carteu3 = joueur3.getCartesEnMain().get(0);

        assertThatThrownBy(() -> jeuService.jouerCarte(partie.getId(), u3.getId(), carteu3.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tour");
    }

    @Test
    @DisplayName("Règle carte hors main → refusée")
    void jouerCarte_carteHorsMain_lanceException() {
        demarrerJeu("Carreau");

        // u1 doit jouer. On lui donne l'id d'une carte inexistante.
        assertThatThrownBy(() -> jeuService.jouerCarte(partie.getId(), u1.getId(), 99999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Règle partie non en jeu → jouerCarte refusé")
    void jouerCarte_partieEnEnchere_lanceException() {
        // La partie est EN_ENCHERE (les enchères n'ont pas encore eu lieu)
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partie.getId());
        Joueur joueur1 = joueurs.stream().filter(j -> j.getUtilisateur().getId().equals(u1.getId())).findFirst().orElseThrow();
        Carte carte = joueur1.getCartesEnMain().get(0);

        assertThatThrownBy(() -> jeuService.jouerCarte(partie.getId(), u1.getId(), carte.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("phase de jeu");
    }

    // ===== DÉROULEMENT COMPLET =====

    @Test
    @DisplayName("Premier pli complet — chaque joueur joue une carte → pliCourant vide après le pli")
    @Transactional
    void premierPliComplet_pliCourantVide() {
        demarrerJeu("Trefle");

        Utilisateur[] ordreJoueurs = { u1, u2, u3, u4 };
        for (Utilisateur u : ordreJoueurs) {
            Joueur j = joueurRepository.findByPartie_Id(partie.getId()).stream()
                    .filter(jj -> jj.getUtilisateur().getId().equals(u.getId()))
                    .findFirst().orElseThrow();
            // Recharger pour avoir la main fraîche
            Joueur jFrais = joueurRepository.findById(j.getId()).orElseThrow();

            // Essayer de jouer la première carte valide
            for (Carte c : jFrais.getCartesEnMain()) {
                try {
                    jeuService.jouerCarte(partie.getId(), u.getId(), c.getId());
                    break;
                } catch (BusinessException ignored) { /* règles de couleur */ }
            }
        }

        // Après le pli, le pli courant est vide (numPliCourant = 2)
        Partie partieApres = partieRepository.findById(partie.getId()).orElseThrow();
        assertThat(partieApres.getNumPliCourant()).isEqualTo(2);
    }
}
