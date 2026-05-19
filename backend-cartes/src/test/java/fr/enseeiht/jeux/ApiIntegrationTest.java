package fr.enseeiht.jeux;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.enseeiht.jeux.dto.AuthRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration des endpoints REST.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ApiIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy securityFilterChain;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // addFilters(FilterChainProxy) applique Spring Security (JWT, autorisation)
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
    }

    // ===== HELPERS =====

    private String inscrireEtObtenirToken(String pseudo, String mdp) throws Exception {
        AuthRequest req = new AuthRequest();
        req.setPseudo(pseudo);
        req.setMotDePasse(mdp);

        MvcResult result = mockMvc.perform(post("/api/auth/inscrire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private Long creerPartieEtObtenirId(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/partie/creer")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private Long obtenirUserId(String token) throws Exception {
        // Décoder le payload JWT (base64)
        String payload = token.split("\\.")[1];
        // padding Base64
        int pad = payload.length() % 4;
        if (pad != 0) payload += "=".repeat(4 - pad);
        String json = new String(java.util.Base64.getDecoder().decode(payload));
        return objectMapper.readTree(json).get("sub").asLong();
    }

    // ===== AUTH — INSCRIPTION =====

    @Test
    @DisplayName("POST /api/auth/inscrire — inscription valide → 201 + token JWT")
    void inscrire_identifiantsValides_retourne201() throws Exception {
        AuthRequest req = new AuthRequest();
        req.setPseudo("alice");
        req.setMotDePasse("secret");

        mockMvc.perform(post("/api/auth/inscrire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.utilisateur.pseudo").value("alice"));
    }

    @Test
    @DisplayName("POST /api/auth/inscrire — pseudo déjà pris → 400")
    void inscrire_pseudoDuplique_retourne400() throws Exception {
        inscrireEtObtenirToken("alice", "secret");

        AuthRequest req = new AuthRequest();
        req.setPseudo("alice");
        req.setMotDePasse("autresecret");

        mockMvc.perform(post("/api/auth/inscrire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/inscrire — corps vide → 400")
    void inscrire_corpsVide_retourne400() throws Exception {
        mockMvc.perform(post("/api/auth/inscrire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ===== AUTH — CONNEXION =====

    @Test
    @DisplayName("POST /api/auth/connexion — identifiants corrects → 200 + token")
    void connexion_identifiantsValides_retourne200() throws Exception {
        inscrireEtObtenirToken("bob", "pass123");

        AuthRequest req = new AuthRequest();
        req.setPseudo("bob");
        req.setMotDePasse("pass123");

        mockMvc.perform(post("/api/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.utilisateur.pseudo").value("bob"));
    }

    @Test
    @DisplayName("POST /api/auth/connexion — mauvais mot de passe → 400")
    void connexion_mauvaisMdp_retourne400() throws Exception {
        inscrireEtObtenirToken("charlie", "bonmotdepasse");

        AuthRequest req = new AuthRequest();
        req.setPseudo("charlie");
        req.setMotDePasse("mauvais");

        mockMvc.perform(post("/api/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/connexion — pseudo inexistant → 400")
    void connexion_pseudoInexistant_retourne400() throws Exception {
        AuthRequest req = new AuthRequest();
        req.setPseudo("fantome");
        req.setMotDePasse("secret");

        mockMvc.perform(post("/api/auth/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ===== SÉCURITÉ — ENDPOINTS PROTÉGÉS =====

    @Test
    @DisplayName("GET /api/parties — sans token → 401")
    void getParties_sansToken_retourne401() throws Exception {
        mockMvc.perform(get("/api/parties"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/partie/creer — sans token → 401")
    void creerPartie_sansToken_retourne401() throws Exception {
        mockMvc.perform(post("/api/partie/creer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/partie/{id}/etat — sans token → 401")
    void getEtat_sansToken_retourne401() throws Exception {
        mockMvc.perform(get("/api/partie/1/etat").param("utilisateurId", "1"))
                .andExpect(status().isUnauthorized());
    }

    // ===== PARTIES =====

    @Test
    @DisplayName("GET /api/parties — avec token valide → 200 + liste JSON")
    void getParties_avecToken_retourne200() throws Exception {
        String token = inscrireEtObtenirToken("dave", "pass");

        mockMvc.perform(get("/api/parties")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("POST /api/partie/creer — avec token → 200 + partie OUVERTE")
    void creerPartie_avecToken_retourne200() throws Exception {
        String token = inscrireEtObtenirToken("eve", "pass");

        mockMvc.perform(post("/api/partie/creer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("OUVERTE"));
    }

    @Test
    @DisplayName("POST /api/partie/{id}/rejoindre — rejoindre une partie existante → 200")
    void rejoindrePartie_partieExistante_retourne200() throws Exception {
        String tokenCreateur = inscrireEtObtenirToken("frank", "pass");
        Long partieId = creerPartieEtObtenirId(tokenCreateur);

        String tokenJoueur = inscrireEtObtenirToken("grace", "pass");
        Long userId = obtenirUserId(tokenJoueur);

        mockMvc.perform(post("/api/partie/" + partieId + "/rejoindre")
                        .header("Authorization", "Bearer " + tokenJoueur)
                        .param("utilisateurId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pseudo").value("grace"));
    }

    @Test
    @DisplayName("POST /api/partie/{id}/demarrer — moins de 4 joueurs → 400")
    void demarrerPartie_moinsDe4Joueurs_retourne400() throws Exception {
        String token = inscrireEtObtenirToken("henry", "pass");
        Long partieId = creerPartieEtObtenirId(token);

        // Seulement 1 joueur, on tente de démarrer
        mockMvc.perform(post("/api/partie/" + partieId + "/demarrer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/partie/{id} — partie inexistante → 404")
    void getPartie_inexistante_retourne404() throws Exception {
        String token = inscrireEtObtenirToken("ivan", "pass");

        mockMvc.perform(get("/api/partie/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/partie/{id} — supprimer une partie ouverte → 204")
    void supprimerPartie_partieOuverte_retourne204() throws Exception {
        String token = inscrireEtObtenirToken("julia", "pass");
        Long userId = obtenirUserId(token);
        Long partieId = creerPartieEtObtenirId(token);

        // Rejoindre pour être membre
        mockMvc.perform(post("/api/partie/" + partieId + "/rejoindre")
                .header("Authorization", "Bearer " + token)
                .param("utilisateurId", userId.toString()));

        mockMvc.perform(delete("/api/partie/" + partieId)
                        .header("Authorization", "Bearer " + token)
                        .param("utilisateurId", userId.toString()))
                .andExpect(status().isNoContent());
    }

    // ===== JEU =====

    @Test
    @DisplayName("POST /api/partie/{id}/encherir — enchérir en dehors d'une partie EN_ENCHERE → 400")
    void encherir_partieNonExistante_retourne404() throws Exception {
        String token = inscrireEtObtenirToken("kevin", "pass");
        Long userId = obtenirUserId(token);

        mockMvc.perform(post("/api/partie/99999/encherir")
                        .header("Authorization", "Bearer " + token)
                        .param("utilisateurId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("passe", true))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/partie/{id}/jouer — partie inexistante → 404")
    void jouerCarte_partieInexistante_retourne404() throws Exception {
        String token = inscrireEtObtenirToken("lena", "pass");
        Long userId = obtenirUserId(token);

        mockMvc.perform(post("/api/partie/99999/jouer")
                        .header("Authorization", "Bearer " + token)
                        .param("utilisateurId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("carteId", 1))))
                .andExpect(status().isNotFound());
    }
}
