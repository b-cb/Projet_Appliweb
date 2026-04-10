package fr.enseeiht.jeux.service;

import fr.enseeiht.jeux.config.BotInitializer;
import fr.enseeiht.jeux.dto.*;
import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.modele.*;
import fr.enseeiht.jeux.repository.*;
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
    private final JeuService jeuService;
    private final BotService botService;

    public PartieService(PartieRepository partieRepository,
                         JoueurRepository joueurRepository,
                         UtilisateurRepository utilisateurRepository,
                         CarteRepository carteRepository,
                         SimpMessagingTemplate messagingTemplate,
                         JeuService jeuService,
                         @Lazy BotService botService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.carteRepository = carteRepository;
        this.messagingTemplate = messagingTemplate;
        this.jeuService = jeuService;
        this.botService = botService;
    }

    public Partie creerPartie() {
        Partie partie = new Partie();
        partie.setStatut("OUVERTE");
        partie.setScoreA(0);
        partie.setScoreB(0);
        return partieRepository.save(partie);
    }

    /**
     * Crée une partie, fait rejoindre les 3 bots, démarre immédiatement,
     * puis déclenche le jeu automatique si le premier joueur est un bot.
     */
    public Partie creerEtDemarrerAvecBots(Long utilisateurId) {
        // 1. Créer la partie
        Partie partie = creerPartie();
        Long partieId = partie.getId();

        // 2. Le joueur humain rejoint en position 0
        rejoindrePartie(partieId, utilisateurId);

        // 3. Les 3 bots rejoignent dans l'ordre
        for (String botPseudo : BotInitializer.BOT_PSEUDOS) {
            Utilisateur bot = utilisateurRepository.findByPseudo(botPseudo)
                    .orElseThrow(() -> new ResourceNotFoundException("Bot " + botPseudo + " introuvable."));
            rejoindrePartie(partieId, bot.getId());
        }

        // 4. Démarrer la partie (distribue les cartes, passe EN_ENCHERE)
        Partie demarree = demarrerPartie(partieId);

        // 5. Si le joueur en position 0 est humain, pas besoin de déclencher les bots
        // Le bot ne jouera qu'après que l'humain ait agi — déclenché dans JeuService
        return demarree;
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
        if (joueurs.size() >= 4) {
            throw new BusinessException("La partie est déjà pleine (4 joueurs max).");
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
        if (joueurs.size() != 4) {
            throw new BusinessException(
                    "Il faut exactement 4 joueurs pour démarrer (actuellement " + joueurs.size() + ").");
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
        partieRepository.save(partie);

        // Push WebSocket personnalisé par joueur
        List<Joueur> joueursActualises = joueurRepository.findByPartie_Id(partieId);
        for (Joueur j : joueursActualises) {
            EtatJeuDTO etat = jeuService.getEtatJeu(partieId, j.getUtilisateur().getId());
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
}
