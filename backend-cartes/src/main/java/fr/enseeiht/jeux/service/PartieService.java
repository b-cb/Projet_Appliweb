package fr.enseeiht.jeux.service;

import fr.enseeiht.jeux.coinche.CoincheBotService;
import fr.enseeiht.jeux.coinche.CoincheService;
import fr.enseeiht.jeux.config.BotInitializer;
import fr.enseeiht.jeux.dto.*;
import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.modele.*;
import fr.enseeiht.jeux.repository.*;
import fr.enseeiht.jeux.tarot.TarotService;
import fr.enseeiht.jeux.coinche.EtatCoincheDTO;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PartieService {

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CarteRepository carteRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CoincheService jeuService;
    private final CoincheBotService botService;
    private final TarotService tarotService;

    public PartieService(PartieRepository partieRepository,
                         JoueurRepository joueurRepository,
                         UtilisateurRepository utilisateurRepository,
                         CarteRepository carteRepository,
                         SimpMessagingTemplate messagingTemplate,
                         CoincheService jeuService,
                         @Lazy CoincheBotService botService,
                         @Lazy TarotService tarotService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.carteRepository = carteRepository;
        this.messagingTemplate = messagingTemplate;
        this.jeuService = jeuService;
        this.botService = botService;
        this.tarotService = tarotService;
    }

    public Partie creerPartie() {
        return creerPartie("COINCHE", 4, 0, 0);
    }

    public Partie creerPartie(String typeJeu, int nbJoueurs) {
        return creerPartie(typeJeu, nbJoueurs, 0, 0);
    }

    public Partie creerPartie(String typeJeu, int nbJoueurs, int maxDonnes, int maxPoints) {
        Partie partie = new Partie();
        partie.setStatut("OUVERTE");
        partie.setScoreA(0);
        partie.setScoreB(0);
        partie.setScoreGlobalA(0);
        partie.setScoreGlobalB(0);
        partie.setDonneActuelle(1);
        partie.setMaxDonnes(maxDonnes);
        partie.setMaxPoints(maxPoints);
        partie.setTypeJeu(typeJeu != null ? typeJeu : "COINCHE");
        partie.setNbJoueursRequis(nbJoueurs > 0 ? nbJoueurs : 4);
        return partieRepository.save(partie);
    }

    /**
     * Crée une partie, fait rejoindre les 3 bots, démarre immédiatement,
     * puis déclenche le jeu automatique si le premier joueur est un bot.
     */
    public Partie creerEtDemarrerAvecBots(Long utilisateurId) {
        return creerEtDemarrerAvecBots(utilisateurId, 0, 0);
    }

    public Partie creerEtDemarrerAvecBots(Long utilisateurId, int maxDonnes, int maxPoints) {
        Partie partie = creerPartie("COINCHE", 4, maxDonnes, maxPoints);
        Long partieId = partie.getId();

        rejoindrePartie(partieId, utilisateurId);

        for (String botPseudo : BotInitializer.BOT_PSEUDOS) {
            Utilisateur bot = utilisateurRepository.findByPseudo(botPseudo)
                    .orElseThrow(() -> new ResourceNotFoundException("Bot " + botPseudo + " introuvable."));
            rejoindrePartie(partieId, bot.getId());
        }

        return demarrerPartie(partieId);
    }

    public List<Partie> listerParties() {
        return partieRepository.findAll();
    }

    public Partie getPartie(Long id) {
        return partieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + id + " introuvable."));
    }

    public Joueur rejoindrePartie(Long partieId, Long utilisateurId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur #" + utilisateurId + " introuvable."));

        if (!"OUVERTE".equals(partie.getStatut())) {
            throw new BusinessException("La partie n'est plus ouverte.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        if (joueurs.size() >= partie.getNbJoueursRequis()) {
            throw new BusinessException("La partie est déjà pleine (" + partie.getNbJoueursRequis() + " joueurs max).");
        }

        boolean dejaPresent = joueurs.stream()
                .anyMatch(j -> j.getUtilisateur().getId().equals(utilisateurId));
        if (dejaPresent) {
            throw new BusinessException("Cet utilisateur est déjà dans la partie.");
        }

        Joueur joueur = new Joueur();
        joueur.setUtilisateur(utilisateur);
        joueur.setPartie(partie);
        joueur.setPosition(joueurs.size());
        joueur.setEquipe((joueurs.size() % 2) + 1);

        Joueur saved = joueurRepository.save(joueur);

        // Push WebSocket sur topic commun
        messagingTemplate.convertAndSend(
                "/topic/partie/" + partieId,
                EvenementJeuDTO.of(EvenementJeuDTO.Type.JOUEUR_REJOINT,
                        JoueurDTO.fromEntity(saved))
        );

        return saved;
    }

    public Partie demarrerPartie(Long partieId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (!"OUVERTE".equals(partie.getStatut())) {
            throw new BusinessException("La partie n'est pas en statut OUVERTE.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        int requis = partie.getNbJoueursRequis() > 0 ? partie.getNbJoueursRequis() : 4;
        if (joueurs.size() != requis) {
            throw new BusinessException(
                    "Il faut exactement " + requis + " joueurs pour démarrer (actuellement " + joueurs.size() + ").");
        }

        if ("TAROT".equals(partie.getTypeJeu())) {
            return demarrerPartieTarot(partieId, partie, joueurs);
        }

        // Créer le jeu de 32 cartes
        String[] valeurs = {"7", "8", "9", "10", "Valet", "Dame", "Roi", "As"};
        String[] couleurs = {"Coeur", "Carreau", "Trefle", "Pique"};

        List<Carte> paquet = new ArrayList<>();
        for (String couleur : couleurs) {
            for (String valeur : valeurs) {
                Carte carte = new Carte();
                carte.setValeur(valeur);
                carte.setCouleur(couleur);
                paquet.add(carteRepository.save(carte));
            }
        }

        Collections.shuffle(paquet);

        for (int i = 0; i < 4; i++) {
            Joueur j = joueurs.get(i);
            List<Carte> main = paquet.subList(i * 8, (i + 1) * 8);
            j.setCartesEnMain(new ArrayList<>(main));
            joueurRepository.save(j);
        }

        partie.setStatut("EN_ENCHERE");
        partie.setTourJoueurIndex(0);
        partie.setPassesConsecutives(0);
        partie.setNumPliCourant(0);
        partie.setScoreA(0);
        partie.setScoreB(0);
        partieRepository.save(partie);

        // Push WebSocket personnalisé par joueur
        List<Joueur> joueursActualises = joueurRepository.findByPartie_Id(partieId);
        for (Joueur j : joueursActualises) {
            EtatCoincheDTO etat = jeuService.getEtatJeu(partieId, j.getUtilisateur().getId());
            messagingTemplate.convertAndSend(
                    "/topic/partie/" + partieId + "/joueur/" + j.getUtilisateur().getId(),
                    EvenementJeuDTO.of(EvenementJeuDTO.Type.ENCHERE, etat)
            );
        }

        return partie;
    }

    @org.springframework.transaction.annotation.Transactional
    public void supprimerPartie(Long partieId, Long utilisateurId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (!"OUVERTE".equals(partie.getStatut())) {
            throw new BusinessException("Seules les parties ouvertes peuvent être supprimées.");
        }

        // Vérifier que l'utilisateur est bien dans la partie (ou créateur = premier joueur)
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        boolean estDansLaPartie = joueurs.stream()
                .anyMatch(j -> j.getUtilisateur().getId().equals(utilisateurId));
        if (!estDansLaPartie && !joueurs.isEmpty()) {
            throw new BusinessException("Vous n'êtes pas dans cette partie.");
        }

        // Vider les mains des joueurs (table de jointure joueur_carte) puis supprimer les joueurs
        for (Joueur j : joueurs) {
            j.getCartesEnMain().clear();
            joueurRepository.save(j);
        }
        joueurRepository.deleteAll(joueurs);
        joueurRepository.flush();

        partieRepository.delete(partie);
    }

    public List<Joueur> getJoueurs(Long partieId) {
        if (!partieRepository.existsById(partieId)) {
            throw new ResourceNotFoundException("Partie #" + partieId + " introuvable.");
        }
        return joueurRepository.findByPartie_Id(partieId);
    }

    /**
     * Démarre une partie Tarot : distribue 78 cartes, met le chien de côté.
     */
    @org.springframework.transaction.annotation.Transactional
    /**
     * Crée une partie Tarot avec bots et la démarre immédiatement.
     * - Tarot 3j : 1 humain + 2 bots (Bot_1, Bot_2)
     * - Tarot 4j : 1 humain + 3 bots (Bot_1, Bot_2, Bot_3)
     * - Tarot 5j : 1 humain + 4 bots (Bot_1, Bot_2, Bot_3, Bot_4)
     */
    public Partie creerEtDemarrerTarotAvecBots(Long utilisateurId, int nbJoueurs) {
        return creerEtDemarrerTarotAvecBots(utilisateurId, nbJoueurs, 0, 0);
    }

    public Partie creerEtDemarrerTarotAvecBots(Long utilisateurId, int nbJoueurs, int maxDonnes, int maxPoints) {
        int nbBots = nbJoueurs - 1;
        Partie partie = creerPartie("TAROT", nbJoueurs, maxDonnes, maxPoints);
        Long partieId = partie.getId();

        rejoindrePartie(partieId, utilisateurId);

        for (int i = 0; i < nbBots; i++) {
            String botPseudo = BotInitializer.BOT_PSEUDOS[i];
            Utilisateur bot = utilisateurRepository.findByPseudo(botPseudo)
                    .orElseThrow(() -> new ResourceNotFoundException("Bot introuvable."));
            rejoindrePartie(partieId, bot.getId());
        }

        return demarrerPartie(partieId);
    }

    private Partie demarrerPartieTarot(Long partieId, Partie partie, List<Joueur> joueurs) {
        int nbJoueurs = partie.getNbJoueursRequis();

        // Taille du chien selon le nombre de joueurs
        int tailleChien = (nbJoueurs == 5) ? 3 : 6;

        // Construire le jeu de 78 cartes Tarot
        List<Carte> paquet = new ArrayList<>();

        // 4 couleurs × 14 cartes (1-10, Valet, Cavalier, Dame, Roi)
        String[] couleurs = {"Coeur", "Carreau", "Trefle", "Pique"};
        String[] valeursCouleur = {"1","2","3","4","5","6","7","8","9","10","Valet","Cavalier","Dame","Roi"};
        for (String couleur : couleurs) {
            for (String valeur : valeursCouleur) {
                Carte c = new Carte();
                c.setCouleur(couleur);
                c.setValeur(valeur);
                paquet.add(carteRepository.save(c));
            }
        }

        // 21 atouts numérotés (1-21)
        for (int i = 1; i <= 21; i++) {
            Carte c = new Carte();
            c.setCouleur("Atout");
            c.setValeur(String.valueOf(i));
            paquet.add(carteRepository.save(c));
        }

        // L'Excuse
        Carte excuse = new Carte();
        excuse.setCouleur("Atout");
        excuse.setValeur("Excuse");
        paquet.add(carteRepository.save(excuse));

        // Mélanger
        Collections.shuffle(paquet);

        // Distribuer N cartes à chaque joueur
        int cartesParJoueur = (paquet.size() - tailleChien) / nbJoueurs;
        for (int i = 0; i < nbJoueurs; i++) {
            Joueur j = joueurs.get(i);
            List<Carte> main = paquet.subList(i * cartesParJoueur, (i + 1) * cartesParJoueur);
            j.setCartesEnMain(new ArrayList<>(main));
            j.setEquipe(0); // équipes inconnues jusqu'à l'enchère
            joueurRepository.save(j);
        }

        // Les dernières cartes forment le chien
        List<Carte> chien = paquet.subList(nbJoueurs * cartesParJoueur, paquet.size());
        partie.setChien(new ArrayList<>(chien));

        // Initialiser l'état de la partie
        partie.setStatut("EN_ENCHERE");
        partie.setPhaseJeu(null);
        partie.setTourJoueurIndex(0);
        partie.setPassesConsecutives(0);
        partie.setNumPliCourant(0);
        partieRepository.save(partie);

        // Push WebSocket
        List<Joueur> joueursActualises = joueurRepository.findByPartie_Id(partieId);
        for (Joueur j : joueursActualises) {
            fr.enseeiht.jeux.tarot.EtatTarotDTO etat = tarotService.getEtatJeuTarot(partieId, j.getUtilisateur().getId());
            messagingTemplate.convertAndSend(
                    "/topic/partie/" + partieId + "/joueur/" + j.getUtilisateur().getId(),
                    fr.enseeiht.jeux.dto.EvenementJeuDTO.of(fr.enseeiht.jeux.dto.EvenementJeuDTO.Type.ENCHERE, etat)
            );
        }

        return partie;
    }

    /**
     * Redémarre une nouvelle donne Coinche : vide les mains, plis et enchères de la donne précédente
     * puis redistribue 32 cartes. Les joueurs et scores globaux sont conservés.
     */
    @org.springframework.transaction.annotation.Transactional
    public void redemarrerDonneCoinche(Partie partie,
                                       List<Joueur> joueurs,
                                       fr.enseeiht.jeux.repository.CarteRepository carteRepository,
                                       fr.enseeiht.jeux.repository.EnchereRepository enchereRepository,
                                       fr.enseeiht.jeux.repository.PliRepository pliRepository) {
        Long partieId = partie.getId();

        // Vider les mains des joueurs
        for (Joueur j : joueurs) {
            j.getCartesEnMain().clear();
            joueurRepository.save(j);
        }

        // Supprimer les plis et enchères de la donne précédente
        pliRepository.deleteAll(pliRepository.findByPartie_Id(partieId));
        enchereRepository.deleteAll(enchereRepository.findByPartie_IdOrderByIdAsc(partieId));

        // Créer et distribuer un nouveau jeu de 32 cartes
        String[] valeurs = {"7", "8", "9", "10", "Valet", "Dame", "Roi", "As"};
        String[] couleurs = {"Coeur", "Carreau", "Trefle", "Pique"};
        List<Carte> paquet = new ArrayList<>();
        for (String couleur : couleurs) {
            for (String valeur : valeurs) {
                Carte carte = new Carte();
                carte.setValeur(valeur);
                carte.setCouleur(couleur);
                paquet.add(carteRepository.save(carte));
            }
        }
        Collections.shuffle(paquet);

        List<Joueur> joueursOrdre = joueurRepository.findByPartie_Id(partieId);
        joueursOrdre.sort(Comparator.comparingInt(Joueur::getPosition));
        for (int i = 0; i < joueursOrdre.size(); i++) {
            Joueur j = joueursOrdre.get(i);
            j.setCartesEnMain(new ArrayList<>(paquet.subList(i * 8, (i + 1) * 8)));
            joueurRepository.save(j);
        }

        // Réinitialiser l'état de la donne (pas les scores globaux)
        partie.setStatut("EN_ENCHERE");
        partie.setAtout(null);
        partie.setContratValeur(0);
        partie.setContratCouleur(null);
        partie.setPreneurId(null);
        partie.setPassesConsecutives(0);
        partie.setNumPliCourant(0);
        partie.setScoreA(0);
        partie.setScoreB(0);
        partie.setTourJoueurIndex(0);
        partieRepository.save(partie);

        // Push WebSocket : tous les joueurs reçoivent le nouvel état
        List<Joueur> joueursActualisesPost = joueurRepository.findByPartie_Id(partieId);
        for (Joueur j : joueursActualisesPost) {
            EtatCoincheDTO etat = jeuService.getEtatJeu(partieId, j.getUtilisateur().getId());
            messagingTemplate.convertAndSend(
                    "/topic/partie/" + partieId + "/joueur/" + j.getUtilisateur().getId(),
                    EvenementJeuDTO.of(EvenementJeuDTO.Type.ENCHERE, etat)
            );
        }
    }
}
