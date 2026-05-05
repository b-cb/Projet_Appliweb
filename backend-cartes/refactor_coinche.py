import re

with open("src/main/java/fr/enseeiht/jeux/coinche/CoincheService.java", "r") as f:
    content = f.read()

# Replace constructor
old_constructor = r"""    public CoincheService\(PartieRepository partieRepository,
            JoueurRepository joueurRepository,
            UtilisateurRepository utilisateurRepository,
            EnchereRepository enchereRepository,
            PliRepository pliRepository,
            SimpMessagingTemplate messagingTemplate,
            @Lazy CoincheBotService botService,
            CarteRepository carteRepository\) \{
        this\.partieRepository = partieRepository;
        this\.joueurRepository = joueurRepository;
        this\.utilisateurRepository = utilisateurRepository;
        this\.enchereRepository = enchereRepository;
        this\.pliRepository = pliRepository;
        this\.messagingTemplate = messagingTemplate;
        this\.botService = botService;
        this\.carteRepository = carteRepository;
    \}"""

new_constructor = """    private final CoincheEtatService coincheEtatService;
    private final CoincheEnchereService coincheEnchereService;
    private final CoincheReglesService coincheReglesService;

    public CoincheService(PartieRepository partieRepository,
            JoueurRepository joueurRepository,
            UtilisateurRepository utilisateurRepository,
            EnchereRepository enchereRepository,
            PliRepository pliRepository,
            SimpMessagingTemplate messagingTemplate,
            @Lazy CoincheBotService botService,
            CarteRepository carteRepository,
            CoincheEtatService coincheEtatService,
            CoincheEnchereService coincheEnchereService,
            CoincheReglesService coincheReglesService) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.enchereRepository = enchereRepository;
        this.pliRepository = pliRepository;
        this.messagingTemplate = messagingTemplate;
        this.botService = botService;
        this.carteRepository = carteRepository;
        this.coincheEtatService = coincheEtatService;
        this.coincheEnchereService = coincheEnchereService;
        this.coincheReglesService = coincheReglesService;
    }"""

content = re.sub(old_constructor, new_constructor, content, flags=re.MULTILINE)

# Replace getEtatJeu (remove it entirely and delegate)
# We find public EtatCoincheDTO getEtatJeu
# and replace the whole method body
content = re.sub(r'public EtatCoincheDTO getEtatJeu\(Long partieId, Long utilisateurId\) \{.*?\n    \}', 
                 'public EtatCoincheDTO getEtatJeu(Long partieId, Long utilisateurId) {\n        return coincheEtatService.getEtatJeu(partieId, utilisateurId);\n    }', 
                 content, flags=re.DOTALL)

# Replace encherir
content = re.sub(r'public EtatCoincheDTO encherir\(Long partieId, Long utilisateurId, Integer contrat, String couleur, boolean passe\) \{.*?\n    \}',
                 """public EtatCoincheDTO encherir(Long partieId, Long utilisateurId, Integer contrat, String couleur, boolean passe) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur joueurActif = joueurs.stream().filter(j -> j.getPosition() == partie.getTourJoueurIndex()).findFirst()
                .orElseThrow(() -> new BusinessException("Joueur actif introuvable."));
        if (!joueurActif.getUtilisateur().getId().equals(utilisateurId)) {
            throw new BusinessException("Ce n'est pas votre tour d'enchérir.");
        }

        boolean finEncheres = coincheEnchereService.traiterEnchere(partie, joueurActif, contrat, couleur, passe, joueurs);
        if (finEncheres) {
            coincheEnchereService.demarrerJeuDepuisEnchere(partie);
        }
        
        partieRepository.save(partie);
        EvenementJeuDTO.Type typeEvt = "EN_JEU".equals(partie.getStatut()) ? EvenementJeuDTO.Type.CARTE_JOUEE : EvenementJeuDTO.Type.ENCHERE;
        pushEtatATous(partieId, joueurs, typeEvt);
        final Long pId = partieId;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override
            public void afterCommit() { botService.jouerSiTourDuBot(pId); }
        });
        return getEtatJeu(partieId, utilisateurId);
    }""", content, flags=re.DOTALL)

# Replace coincher
content = re.sub(r'public EtatCoincheDTO coincher\(Long partieId, Long utilisateurId, boolean surcoinche\) \{.*?\n    \}',
                 """public EtatCoincheDTO coincher(Long partieId, Long utilisateurId, boolean surcoinche) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));
        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);
        Joueur monJoueur = joueurs.stream().filter(j -> j.getUtilisateur().getId().equals(utilisateurId)).findFirst()
                .orElseThrow(() -> new BusinessException("Vous n'êtes pas dans cette partie."));

        boolean finEncheres = coincheEnchereService.traiterCoinche(partie, monJoueur, surcoinche, joueurs);
        if (finEncheres) {
            coincheEnchereService.demarrerJeuDepuisEnchere(partie);
        }
        
        partieRepository.save(partie);
        EvenementJeuDTO.Type typeEvt = "EN_JEU".equals(partie.getStatut()) ? EvenementJeuDTO.Type.CARTE_JOUEE : EvenementJeuDTO.Type.ENCHERE;
        pushEtatATous(partieId, joueurs, typeEvt);
        final Long pId = partieId;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override
            public void afterCommit() { botService.jouerSiTourDuBot(pId); }
        });
        return getEtatJeu(partieId, utilisateurId);
    }""", content, flags=re.DOTALL)

# Delete verifierReglesCouleur, estPartenaireLeGagnantActuel, demarrerJeuDepuisEnchere, doitCommencerJeu, capitalise, buildResultat
content = re.sub(r'private void verifierReglesCouleur.*?\}\n\n    /\*\*', '/**', content, flags=re.DOTALL)
content = re.sub(r'private boolean estPartenaireLeGagnantActuel.*?\}\n\n    /\*\*', '/**', content, flags=re.DOTALL)
content = re.sub(r'private void demarrerJeuDepuisEnchere.*?\}\n\n    /\*\*', '/**', content, flags=re.DOTALL)
content = re.sub(r'private boolean doitCommencerJeu.*?\}\n\n    // ====', '// ====', content, flags=re.DOTALL)
content = re.sub(r'private String capitalise.*?\}\n\n    private ResultatDTO', 'private ResultatDTO', content, flags=re.DOTALL)
content = re.sub(r'private ResultatDTO buildResultat.*?\n    \}\n\}\n', '}\n', content, flags=re.DOTALL)

with open("src/main/java/fr/enseeiht/jeux/coinche/CoincheService.java", "w") as f:
    f.write(content)
