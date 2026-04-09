package fr.enseeiht.jeux.service;

import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.modele.*;
import fr.enseeiht.jeux.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PartieService {

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CarteRepository carteRepository;

    public PartieService(PartieRepository partieRepository,
                         JoueurRepository joueurRepository,
                         UtilisateurRepository utilisateurRepository,
                         CarteRepository carteRepository) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.carteRepository = carteRepository;
    }

    public Partie creerPartie() {
        Partie partie = new Partie();
        partie.setStatut("OUVERTE");
        partie.setScoreA(0);
        partie.setScoreB(0);
        return partieRepository.save(partie);
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

        return joueurRepository.save(joueur);
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
            Joueur joueur = joueurs.get(i);
            List<Carte> main = paquet.subList(i * 8, (i + 1) * 8);
            joueur.setCartesEnMain(new ArrayList<>(main));
            joueurRepository.save(joueur);
        }

        // La partie passe en phase d'enchères (pas directement EN_JEU)
        partie.setStatut("EN_ENCHERE");
        partie.setTourJoueurIndex(0); // le joueur en position 0 ouvre les enchères
        partie.setPassesConsecutives(0);
        partie.setNumPliCourant(0);
        return partieRepository.save(partie);
    }

    public List<Joueur> getJoueurs(Long partieId) {
        // Vérifier que la partie existe
        if (!partieRepository.existsById(partieId)) {
            throw new ResourceNotFoundException("Partie #" + partieId + " introuvable.");
        }
        return joueurRepository.findByPartie_Id(partieId);
    }
}
