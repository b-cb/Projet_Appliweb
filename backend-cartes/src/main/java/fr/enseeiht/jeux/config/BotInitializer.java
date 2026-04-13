package fr.enseeiht.jeux.config;

import fr.enseeiht.jeux.modele.Utilisateur;
import fr.enseeiht.jeux.repository.UtilisateurRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crée les comptes bot au démarrage si absents.
 * Pseudos : Bot_1, Bot_2, Bot_3 — marqués isBot=true.
 */
@Component
public class BotInitializer implements ApplicationRunner {

    public static final String[] BOT_PSEUDOS = {"Bot_1", "Bot_2", "Bot_3", "Bot_4"};

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    public BotInitializer(UtilisateurRepository utilisateurRepository,
                          PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String pseudo : BOT_PSEUDOS) {
            if (utilisateurRepository.findByPseudo(pseudo).isEmpty()) {
                Utilisateur bot = new Utilisateur();
                bot.setPseudo(pseudo);
                bot.setMdp(passwordEncoder.encode("bot-password-inutilisable"));
                bot.setBot(true);
                bot.setScoreGlobal(0);
                utilisateurRepository.save(bot);
            }
        }
    }
}
