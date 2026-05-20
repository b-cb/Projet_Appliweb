package fr.enseeiht.jeux.tarot;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import fr.enseeiht.jeux.modele.Carte;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Partie;
import fr.enseeiht.jeux.repository.JoueurRepository;
import fr.enseeiht.jeux.repository.PartieRepository;

// Joue automatiquement les coups des bots en mode Tarot. S'auto-chaîne jusqu'au prochain joueur humain.
@Service
public class TarotBotService {

    private static final Logger log = LoggerFactory.getLogger(TarotBotService.class);

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final TarotService     tarotService;

    public TarotBotService(PartieRepository partieRepository,
                           JoueurRepository joueurRepository,
                           @Lazy TarotService tarotService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.tarotService     = tarotService;
    }

    @Async
    public void jouerSiTourDuBot(Long partieId) {
        try { Thread.sleep(900); } catch (InterruptedException ignored) { return; }

        Partie partie = partieRepository.findById(partieId).orElse(null);
        if (partie == null) return;

        String statut = partie.getStatut();
        if ("TERMINEE".equals(statut) || "OUVERTE".equals(statut)) return;

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur joueurActif = joueurs.stream()
                .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                .findFirst().orElse(null);

        if (joueurActif == null || !joueurActif.getUtilisateur().isBot()) return;

        String botPseudo = joueurActif.getUtilisateur().getPseudo();
        Long   botUserId = joueurActif.getUtilisateur().getId();

        boolean aJoue = false;
        try {
            if ("EN_ENCHERE".equals(statut) && partie.getPhaseJeu() == null) {
                tarotService.encherirTarot(partieId, botUserId, "PASSE");
                aJoue = true;
            } else if ("EN_JEU".equals(statut)) {
                Joueur joueurFrais = joueurRepository.findById(joueurActif.getId()).orElse(null);
                if (joueurFrais != null) {
                    jouerCarteTarot(partieId, botUserId, joueurFrais, botPseudo);
                    aJoue = true;
                }
            }
        } catch (Exception e) {
            log.error("TarotBot {} dans partie {} : {}", botPseudo, partieId, e.getMessage());
        }

        if (aJoue) jouerSiTourDuBot(partieId);
    }

    private void jouerCarteTarot(Long partieId, Long botUserId, Joueur joueur, String botPseudo) {
        List<Carte> main = new ArrayList<>(joueur.getCartesEnMain());
        if (main.isEmpty()) return;

        for (Carte c : main) {
            try {
                tarotService.jouerCarte(partieId, botUserId, c.getId());
                log.info("TarotBot {} joue {} {} dans partie {}", botPseudo, c.getValeur(), c.getCouleur(), partieId);
                return;
            } catch (Exception e) {
                log.debug("TarotBot {} : {} {} rejetée", botPseudo, c.getValeur(), c.getCouleur());
            }
        }
        log.error("TarotBot {} BLOQUÉ dans partie {} — aucune carte jouable", botPseudo, partieId);
    }
}
