package fr.enseeiht.jeux.controller;

import fr.enseeiht.jeux.modele.*;
import fr.enseeiht.jeux.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class GameController {

    @Autowired
    private UtilisateurRepository utilisateurRepository;

    @Autowired
    private PartieRepository partieRepository;

    @Autowired
    private JoueurRepository joueurRepository;

    @Autowired
    private CarteRepository carteRepository;

    @Autowired
    private PliRepository pliRepository;

    @Autowired
    private EnchereRepository enchereRepository;

    @Autowired
    private MessageChatRepository messageChatRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    // =====================================================
    // UTILISATEUR
    // =====================================================

    /** Créer un nouvel utilisateur */
    @PostMapping("/api/utilisateur/creer")
    public Utilisateur creerUtilisateur(@RequestParam String pseudo) {
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setPseudo(pseudo);
        utilisateur.setMdp("default_password");
        utilisateur.setScoreGlobal(0);
        return utilisateurRepository.save(utilisateur);
    }

    /** Connexion : vérifie que le pseudo existe, sinon le crée */
    @PostMapping("/api/utilisateur/connexion")
    public Utilisateur connexion(@RequestParam String pseudo) {
        // Chercher un utilisateur existant par pseudo
        Optional<Utilisateur> existant = utilisateurRepository.findAll().stream()
                .filter(u -> u.getPseudo().equals(pseudo))
                .findFirst();
        if (existant.isPresent()) {
            return existant.get();
        }
        // Sinon, créer un nouveau
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setPseudo(pseudo);
        utilisateur.setMdp("default_password");
        utilisateur.setScoreGlobal(0);
        return utilisateurRepository.save(utilisateur);
    }

    /** Récupérer le profil d'un utilisateur */
    @GetMapping("/api/utilisateur/{id}")
    public ResponseEntity<Utilisateur> getUtilisateur(@PathVariable Long id) {
        return utilisateurRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Lister tous les utilisateurs (pour les invitations) */
    @GetMapping("/api/utilisateurs")
    public List<Utilisateur> getUtilisateurs() {
        return utilisateurRepository.findAll();
    }

    // =====================================================
    // PARTIE
    // =====================================================

    /** Créer une nouvelle partie */
    @PostMapping("/api/partie/creer")
    public Partie creerPartie() {
        Partie partie = new Partie();
        partie.setStatut("OUVERTE");
        partie.setScoreA(0);
        partie.setScoreB(0);
        return partieRepository.save(partie);
    }

    /** Lister toutes les parties */
    @GetMapping("/api/parties")
    public List<Partie> getParties() {
        return partieRepository.findAll();
    }

    /** Récupérer l'état complet d'une partie */
    @GetMapping("/api/partie/{id}")
    public ResponseEntity<Partie> getPartie(@PathVariable Long id) {
        return partieRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Rejoindre une partie — crée un Joueur lié à la Partie */
    @PostMapping("/api/partie/{partieId}/rejoindre")
    public ResponseEntity<?> rejoindrePartie(
            @PathVariable Long partieId,
            @RequestParam Long utilisateurId) {

        Optional<Partie> optPartie = partieRepository.findById(partieId);
        Optional<Utilisateur> optUser = utilisateurRepository.findById(utilisateurId);

        if (optPartie.isEmpty() || optUser.isEmpty()) {
            return ResponseEntity.badRequest().body("Partie ou utilisateur introuvable.");
        }

        Partie partie = optPartie.get();
        Utilisateur utilisateur = optUser.get();

        // Vérifier que la partie est ouverte
        if (!"OUVERTE".equals(partie.getStatut())) {
            return ResponseEntity.badRequest().body("La partie n'est plus ouverte.");
        }

        // Vérifier qu'il n'y a pas déjà 4 joueurs
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        if (joueurs.size() >= 4) {
            return ResponseEntity.badRequest().body("La partie est déjà pleine (4 joueurs max).");
        }

        // Vérifier que l'utilisateur n'est pas déjà dans la partie
        boolean dejaPresent = joueurs.stream()
                .anyMatch(j -> j.getUtilisateur().getId().equals(utilisateurId));
        if (dejaPresent) {
            return ResponseEntity.badRequest().body("Cet utilisateur est déjà dans la partie.");
        }

        // Créer le joueur avec équipe et position automatiques
        Joueur joueur = new Joueur();
        joueur.setUtilisateur(utilisateur);
        joueur.setPartie(partie);
        joueur.setPosition(joueurs.size()); // 0, 1, 2, 3
        joueur.setEquipe((joueurs.size() % 2) + 1); // Équipe 1, 2, 1, 2

        joueurRepository.save(joueur);

        return ResponseEntity.ok(joueur);
    }

    /** Démarrer une partie — distribue les 32 cartes */
    @PostMapping("/api/partie/{partieId}/demarrer")
    public ResponseEntity<?> demarrerPartie(@PathVariable Long partieId) {

        Optional<Partie> optPartie = partieRepository.findById(partieId);
        if (optPartie.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Partie partie = optPartie.get();

        if (!"OUVERTE".equals(partie.getStatut())) {
            return ResponseEntity.badRequest().body("La partie n'est pas en statut OUVERTE.");
        }

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        if (joueurs.size() != 4) {
            return ResponseEntity.badRequest()
                    .body("Il faut exactement 4 joueurs pour démarrer (actuellement " + joueurs.size() + ").");
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

        // Mélanger
        Collections.shuffle(paquet);

        // Distribuer 8 cartes par joueur
        for (int i = 0; i < 4; i++) {
            Joueur joueur = joueurs.get(i);
            List<Carte> main = paquet.subList(i * 8, (i + 1) * 8);
            joueur.setCartesEnMain(new ArrayList<>(main));
            joueurRepository.save(joueur);
        }

        // Mettre à jour le statut
        partie.setStatut("EN_COURS");
        partieRepository.save(partie);

        return ResponseEntity.ok(partie);
    }

    /** Récupérer les joueurs d'une partie */
    @GetMapping("/api/partie/{partieId}/joueurs")
    public List<Joueur> getJoueurs(@PathVariable Long partieId) {
        return joueurRepository.findByPartie_Id(partieId);
    }

    // =====================================================
    // INVITATION
    // =====================================================

    /** Envoyer une invitation */
    @PostMapping("/api/invitation/envoyer")
    public ResponseEntity<?> envoyerInvitation(
            @RequestParam Long expediteurId,
            @RequestParam Long destinataireId,
            @RequestParam Long partieId) {

        Optional<Utilisateur> optExp = utilisateurRepository.findById(expediteurId);
        Optional<Utilisateur> optDest = utilisateurRepository.findById(destinataireId);
        Optional<Partie> optPartie = partieRepository.findById(partieId);

        if (optExp.isEmpty() || optDest.isEmpty() || optPartie.isEmpty()) {
            return ResponseEntity.badRequest().body("Expéditeur, destinataire ou partie introuvable.");
        }

        if (expediteurId.equals(destinataireId)) {
            return ResponseEntity.badRequest().body("Impossible de s'inviter soi-même.");
        }

        Invitation invitation = new Invitation();
        invitation.setExpediteur(optExp.get());
        invitation.setDestinataire(optDest.get());
        invitation.setPartie(optPartie.get());
        invitation.setStatut("EN_ATTENTE");

        invitationRepository.save(invitation);

        return ResponseEntity.ok(invitation);
    }

    /** Lister les invitations reçues par un utilisateur */
    @GetMapping("/api/invitation/recues")
    public List<Invitation> getInvitationsRecues(@RequestParam Long utilisateurId) {
        return invitationRepository.findByDestinataire_Id(utilisateurId);
    }

    /** Accepter une invitation — change le statut et fait rejoindre la partie */
    @PostMapping("/api/invitation/{id}/accepter")
    public ResponseEntity<?> accepterInvitation(@PathVariable Long id) {
        Optional<Invitation> optInv = invitationRepository.findById(id);
        if (optInv.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Invitation invitation = optInv.get();

        if (!"EN_ATTENTE".equals(invitation.getStatut())) {
            return ResponseEntity.badRequest().body("Cette invitation a déjà été traitée.");
        }

        invitation.setStatut("ACCEPTEE");
        invitationRepository.save(invitation);

        // Faire rejoindre automatiquement la partie
        Long partieId = invitation.getPartie().getId();
        Long utilisateurId = invitation.getDestinataire().getId();

        // Réutiliser la logique de rejoindre
        return rejoindrePartie(partieId, utilisateurId);
    }

    /** Refuser une invitation */
    @PostMapping("/api/invitation/{id}/refuser")
    public ResponseEntity<?> refuserInvitation(@PathVariable Long id) {
        Optional<Invitation> optInv = invitationRepository.findById(id);
        if (optInv.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Invitation invitation = optInv.get();

        if (!"EN_ATTENTE".equals(invitation.getStatut())) {
            return ResponseEntity.badRequest().body("Cette invitation a déjà été traitée.");
        }

        invitation.setStatut("REFUSEE");
        invitationRepository.save(invitation);

        return ResponseEntity.ok(invitation);
    }

    /** Lister les invitations d'une partie */
    @GetMapping("/api/invitation/partie/{partieId}")
    public List<Invitation> getInvitationsPartie(@PathVariable Long partieId) {
        return invitationRepository.findByPartie_Id(partieId);
    }
}
