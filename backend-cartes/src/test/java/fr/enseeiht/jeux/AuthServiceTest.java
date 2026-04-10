package fr.enseeiht.jeux;

import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.modele.Utilisateur;
import fr.enseeiht.jeux.repository.UtilisateurRepository;
import fr.enseeiht.jeux.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires d'AuthService — isolation complète via Mockito.
 * Vérifie la logique d'inscription et de connexion sans base de données.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private Utilisateur utilisateurExistant;

    @BeforeEach
    void setUp() {
        utilisateurExistant = new Utilisateur();
        utilisateurExistant.setId(1L);
        utilisateurExistant.setPseudo("alice");
        utilisateurExistant.setMdp("$2a$10$hashedPassword");
        utilisateurExistant.setScoreGlobal(0);
    }

    // ===== INSCRIPTION =====

    @Test
    @DisplayName("Inscription OK — sauvegarde l'utilisateur avec mdp hashé")
    void inscrire_pseudoDisponible_retourneUtilisateur() {
        when(utilisateurRepository.findByPseudo("alice")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("$2a$10$hashedPassword");
        when(utilisateurRepository.save(any())).thenAnswer(inv -> {
            Utilisateur u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        Utilisateur result = authService.inscrire("alice", "secret");

        assertThat(result.getPseudo()).isEqualTo("alice");
        assertThat(result.getMdp()).isEqualTo("$2a$10$hashedPassword");
        assertThat(result.getScoreGlobal()).isZero();
        verify(utilisateurRepository).save(any(Utilisateur.class));
    }

    @Test
    @DisplayName("Inscription — pseudo déjà pris → BusinessException")
    void inscrire_pseudoDuplique_lanceException() {
        when(utilisateurRepository.findByPseudo("alice")).thenReturn(Optional.of(utilisateurExistant));

        assertThatThrownBy(() -> authService.inscrire("alice", "autreSecret"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("alice");

        verify(utilisateurRepository, never()).save(any());
    }

    @Test
    @DisplayName("Inscription — le mot de passe est bien hashé, jamais stocké en clair")
    void inscrire_motDePasseHashe() {
        when(utilisateurRepository.findByPseudo(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("motdepasse")).thenReturn("HASH_BCRYPT");
        when(utilisateurRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Utilisateur result = authService.inscrire("bob", "motdepasse");

        assertThat(result.getMdp()).isEqualTo("HASH_BCRYPT");
        assertThat(result.getMdp()).doesNotContain("motdepasse");
        verify(passwordEncoder).encode("motdepasse");
    }

    // ===== CONNEXION =====

    @Test
    @DisplayName("Connexion OK — identifiants valides → retourne l'utilisateur")
    void connexion_identifiantsValides_retourneUtilisateur() {
        when(utilisateurRepository.findByPseudo("alice")).thenReturn(Optional.of(utilisateurExistant));
        when(passwordEncoder.matches("secret", "$2a$10$hashedPassword")).thenReturn(true);

        Utilisateur result = authService.connexion("alice", "secret");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getPseudo()).isEqualTo("alice");
    }

    @Test
    @DisplayName("Connexion — pseudo inconnu → BusinessException")
    void connexion_pseudoInconnu_lanceException() {
        when(utilisateurRepository.findByPseudo("inconnu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.connexion("inconnu", "secret"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Connexion — mauvais mot de passe → BusinessException")
    void connexion_mauvaisMdp_lanceException() {
        when(utilisateurRepository.findByPseudo("alice")).thenReturn(Optional.of(utilisateurExistant));
        when(passwordEncoder.matches("mauvais", "$2a$10$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.connexion("alice", "mauvais"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Connexion — le message d'erreur est identique pour pseudo inconnu et mauvais mdp (sécurité)")
    void connexion_messageErreurGenerique() {
        when(utilisateurRepository.findByPseudo("inconnu")).thenReturn(Optional.empty());
        when(utilisateurRepository.findByPseudo("alice")).thenReturn(Optional.of(utilisateurExistant));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        String msgPseudoInconnu = null;
        String msgMauvaisMdp = null;

        try { authService.connexion("inconnu", "secret"); }
        catch (BusinessException e) { msgPseudoInconnu = e.getMessage(); }

        try { authService.connexion("alice", "mauvais"); }
        catch (BusinessException e) { msgMauvaisMdp = e.getMessage(); }

        assertThat(msgPseudoInconnu).isEqualTo(msgMauvaisMdp);
    }
}
