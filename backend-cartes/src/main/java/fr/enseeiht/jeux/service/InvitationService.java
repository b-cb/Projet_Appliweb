package fr.enseeiht.jeux.service;

import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.modele.Invitation;
import fr.enseeiht.jeux.modele.Joueur;
import fr.enseeiht.jeux.modele.Partie;
import fr.enseeiht.jeux.modele.Utilisateur;
import fr.enseeiht.jeux.repository.InvitationRepository;
import fr.enseeiht.jeux.repository.PartieRepository;
import fr.enseeiht.jeux.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PartieRepository partieRepository;
    private final PartieService partieService;

    public InvitationService(InvitationRepository invitationRepository,
                             UtilisateurRepository utilisateurRepository,
                             PartieRepository partieRepository,
                             PartieService partieService) {
        this.invitationRepository = invitationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.partieRepository = partieRepository;
        this.partieService = partieService;
    }

    public Invitation envoyerInvitation(Long expediteurId, Long destinataireId, Long partieId) {
        Utilisateur expediteur = utilisateurRepository.findById(expediteurId)
                .orElseThrow(() -> new ResourceNotFoundException("Expéditeur #" + expediteurId + " introuvable."));
        Utilisateur destinataire = utilisateurRepository.findById(destinataireId)
                .orElseThrow(() -> new ResourceNotFoundException("Destinataire #" + destinataireId + " introuvable."));
        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));

        if (expediteurId.equals(destinataireId)) {
            throw new BusinessException("Impossible de s'inviter soi-même.");
        }

        Invitation invitation = new Invitation();
        invitation.setExpediteur(expediteur);
        invitation.setDestinataire(destinataire);
        invitation.setPartie(partie);
        invitation.setStatut("EN_ATTENTE");

        return invitationRepository.save(invitation);
    }

    public List<Invitation> getInvitationsRecues(Long utilisateurId) {
        return invitationRepository.findByDestinataire_Id(utilisateurId);
    }

    public Joueur accepterInvitation(Long invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation #" + invitationId + " introuvable."));

        if (!"EN_ATTENTE".equals(invitation.getStatut())) {
            throw new BusinessException("Cette invitation a déjà été traitée.");
        }

        invitation.setStatut("ACCEPTEE");
        invitationRepository.save(invitation);

        return partieService.rejoindrePartie(
                invitation.getPartie().getId(),
                invitation.getDestinataire().getId()
        );
    }

    public Invitation refuserInvitation(Long invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation #" + invitationId + " introuvable."));

        if (!"EN_ATTENTE".equals(invitation.getStatut())) {
            throw new BusinessException("Cette invitation a déjà été traitée.");
        }

        invitation.setStatut("REFUSEE");
        return invitationRepository.save(invitation);
    }

    public List<Invitation> getInvitationsPartie(Long partieId) {
        return invitationRepository.findByPartie_Id(partieId);
    }
}
