package fr.enseeiht.jeux.coinche;

import fr.enseeiht.jeux.dto.CarteDTO;
import fr.enseeiht.jeux.dto.EnchereDTO;
import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.modele.Carte;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Partie;
import fr.enseeiht.jeux.modele.Pli;
import fr.enseeiht.jeux.repository.EnchereRepository;
import fr.enseeiht.jeux.repository.JoueurRepository;
import fr.enseeiht.jeux.repository.PartieRepository;
import fr.enseeiht.jeux.repository.PliRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service gérant la construction de l'état (DTO) pour la Coinche.
 */
@Service
@Transactional
public class CoincheEtatService {

    private final PartieRepository partieRepository;
    private final JoueurRepository joueurRepository;
    private final EnchereRepository enchereRepository;
    private final PliRepository pliRepository;

    public CoincheEtatService(PartieRepository partieRepository,
                              JoueurRepository joueurRepository,
                              EnchereRepository enchereRepository,
                              PliRepository pliRepository) {
        this.partieRepository = partieRepository;
        this.joueurRepository = joueurRepository;
        this.enchereRepository = enchereRepository;
        this.pliRepository = pliRepository;
    }

    public EtatCoincheDTO getEtatJeu(Long partieId, Long utilisateurId) {
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        List<Joueur> joueurs = joueurRepository.findByPartie_Id(partieId);

        Joueur monJoueur = joueurs.stream()
                .filter(j -> j.getUtilisateur().getId().equals(utilisateurId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Vous n'êtes pas dans cette partie."));

        EtatCoincheDTO dto = new EtatCoincheDTO();
        dto.setPartieId(partieId);
        dto.setStatut(partie.getStatut());
        dto.setAtout(partie.getAtout());
        dto.setContratValeur(partie.getContratValeur());
        dto.setContratCouleur(partie.getContratCouleur());
        dto.setScoreA(partie.getScoreA());
        dto.setScoreB(partie.getScoreB());
        dto.setNumPliCourant(partie.getNumPliCourant());
        dto.setMonJoueurId(monJoueur.getId());
        dto.setMonEquipe(monJoueur.getEquipe());

        // Ma main
        dto.setMaMain(monJoueur.getCartesEnMain().stream()
                .map(CarteDTO::fromEntity)
                .collect(Collectors.toList()));

        // Joueur dont c'est le tour
        if (joueurs.size() == 4) {
            Joueur joueurTour = joueurs.stream()
                    .filter(j -> j.getPosition() == partie.getTourJoueurIndex())
                    .findFirst().orElse(null);
            if (joueurTour != null) {
                dto.setTourJoueurId(joueurTour.getId());
                dto.setTourPseudo(joueurTour.getUtilisateur().getPseudo());
            }
        }

        // Pli courant
        Optional<Pli> pliOpt = pliRepository.findByPartie_IdAndNumTour(partieId, partie.getNumPliCourant());
        if (pliOpt.isPresent()) {
            Pli pli = pliOpt.get();
            List<Carte> cartesJouees = pli.getCartesJouees();
            int ouvreurIndex = pli.getJoueurOuvreurIndex();
            List<EtatCoincheDTO.CartePliDTO> pliCourant = new ArrayList<>();
            for (int i = 0; i < cartesJouees.size(); i++) {
                int idx = (ouvreurIndex + i) % 4;
                Joueur j = joueurs.stream()
                        .filter(jj -> jj.getPosition() == idx)
                        .findFirst().orElse(null);
                if (j != null) {
                    pliCourant.add(new EtatCoincheDTO.CartePliDTO(
                            CarteDTO.fromEntity(cartesJouees.get(i)),
                            j.getUtilisateur().getPseudo(),
                            j.getEquipe()));
                }
            }
            dto.setPliCourant(pliCourant);
        } else {
            dto.setPliCourant(new ArrayList<>());
        }

        // Dernier pli terminé (pli précédent)
        if (partie.getNumPliCourant() > 1 || "TERMINEE".equals(partie.getStatut())) {
            int numDernier = "TERMINEE".equals(partie.getStatut())
                    ? partie.getNumPliCourant()
                    : partie.getNumPliCourant() - 1;
            Optional<Pli> dernierOpt = pliRepository.findByPartie_IdAndNumTour(partieId, numDernier);
            if (dernierOpt.isPresent()) {
                Pli dp = dernierOpt.get();
                List<Carte> dpCartes = dp.getCartesJouees();
                int dpOuvreur = dp.getJoueurOuvreurIndex();
                List<EtatCoincheDTO.CartePliDTO> dernierPliList = new ArrayList<>();
                for (int i = 0; i < dpCartes.size(); i++) {
                    int idx = (dpOuvreur + i) % 4;
                    final int idxFinal = idx;
                    Joueur j = joueurs.stream().filter(jj -> jj.getPosition() == idxFinal).findFirst().orElse(null);
                    if (j != null) {
                        dernierPliList.add(new EtatCoincheDTO.CartePliDTO(
                                CarteDTO.fromEntity(dpCartes.get(i)),
                                j.getUtilisateur().getPseudo(),
                                j.getEquipe()));
                    }
                }
                dto.setDernierPli(dernierPliList);
                dto.setDernierPliGagnantEquipe(dp.getGagnantEquipe());
            }
        }

        // Historique des enchères
        dto.setEncheres(enchereRepository.findByPartie_IdOrderByIdAsc(partieId).stream()
                .map(EnchereDTO::fromEntity)
                .collect(Collectors.toList()));

        // Multi-manche
        dto.setDonneActuelle(partie.getDonneActuelle());
        dto.setMaxDonnes(partie.getMaxDonnes());
        dto.setMaxPoints(partie.getMaxPoints());
        dto.setScoreGlobalA(partie.getScoreGlobalA());
        dto.setScoreGlobalB(partie.getScoreGlobalB());

        // Coinche/Surcoinche
        dto.setCoinche(partie.getCoinche());
        if (partie.getPreneurId() != null) {
            dto.setPreneurId(partie.getPreneurId());
            joueurs.stream()
                    .filter(j -> j.getId().equals(partie.getPreneurId()))
                    .findFirst()
                    .ifPresent(p -> dto.setPreneurEquipe(p.getEquipe()));
        }

        // Résultat si terminée
        if ("TERMINEE".equals(partie.getStatut())) {
            dto.setResultat(buildResultat(partie, joueurs));
        }

        return dto;
    }

    private fr.enseeiht.jeux.dto.ResultatDTO buildResultat(Partie partie, List<Joueur> joueurs) {
        fr.enseeiht.jeux.dto.ResultatDTO r = new fr.enseeiht.jeux.dto.ResultatDTO();
        r.setScoreA(partie.getScoreA());
        r.setScoreB(partie.getScoreB());
        r.setContratValeur(partie.getContratValeur());
        r.setContratCouleur(partie.getContratCouleur());

        Long preneurId = partie.getPreneurId();
        if (preneurId != null) {
            joueurs.stream()
                    .filter(j -> j.getId().equals(preneurId))
                    .findFirst()
                    .ifPresent(preneur -> {
                        int equipePreneur = preneur.getEquipe();
                        int scorePreneur = (equipePreneur == 1) ? partie.getScoreA() : partie.getScoreB();
                        r.setContratRempli(scorePreneur >= partie.getContratValeur());
                        r.setPseudoPreneur(preneur.getUtilisateur().getPseudo());
                        r.setGagnantEquipe(partie.getScoreA() > partie.getScoreB() ? 1 : 2);
                    });
        }
        return r;
    }
}
