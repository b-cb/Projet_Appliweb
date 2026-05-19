package fr.enseeiht.jeux.service;

import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.modele.Utilisateur;
import fr.enseeiht.jeux.repository.UtilisateurRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UtilisateurRepository utilisateurRepository,
                       PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Inscription d'un utilisateur.
    public Utilisateur inscrire(String pseudo, String motDePasse) {
        if (utilisateurRepository.findByPseudo(pseudo).isPresent()) {
            throw new BusinessException("Le pseudo '" + pseudo + "' est déjà pris.");
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setPseudo(pseudo);
        utilisateur.setMdp(passwordEncoder.encode(motDePasse));
        utilisateur.setScoreGlobal(0);
        return utilisateurRepository.save(utilisateur);
    }

    // Connexion d'un utilisateur.
    public Utilisateur connexion(String pseudo, String motDePasse) {
        Utilisateur utilisateur = utilisateurRepository.findByPseudo(pseudo)
                .orElseThrow(() -> new BusinessException("Pseudo ou mot de passe incorrect."));

        if (!passwordEncoder.matches(motDePasse, utilisateur.getMdp())) {
            throw new BusinessException("Pseudo ou mot de passe incorrect.");
        }

        return utilisateur;
    }
}
