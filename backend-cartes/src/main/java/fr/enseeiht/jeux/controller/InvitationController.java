package fr.enseeiht.jeux.controller;

import fr.enseeiht.jeux.dto.InvitationDTO;
import fr.enseeiht.jeux.dto.JoueurDTO;
import fr.enseeiht.jeux.service.InvitationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invitation")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/envoyer")
    public ResponseEntity<InvitationDTO> envoyerInvitation(
            @RequestParam Long expediteurId,
            @RequestParam Long destinataireId,
            @RequestParam Long partieId) {
        return ResponseEntity.ok(InvitationDTO.fromEntity(
                invitationService.envoyerInvitation(expediteurId, destinataireId, partieId)));
    }

    @GetMapping("/recues")
    public List<InvitationDTO> getInvitationsRecues(@RequestParam Long utilisateurId) {
        return invitationService.getInvitationsRecues(utilisateurId).stream()
                .map(InvitationDTO::fromEntity)
                .toList();
    }

    @PostMapping("/{id}/accepter")
    public ResponseEntity<JoueurDTO> accepterInvitation(@PathVariable Long id) {
        return ResponseEntity.ok(JoueurDTO.fromEntity(
                invitationService.accepterInvitation(id)));
    }

    @PostMapping("/{id}/refuser")
    public ResponseEntity<InvitationDTO> refuserInvitation(@PathVariable Long id) {
        return ResponseEntity.ok(InvitationDTO.fromEntity(
                invitationService.refuserInvitation(id)));
    }

    @GetMapping("/partie/{partieId}")
    public List<InvitationDTO> getInvitationsPartie(@PathVariable Long partieId) {
        return invitationService.getInvitationsPartie(partieId).stream()
                .map(InvitationDTO::fromEntity)
                .toList();
    }
}
