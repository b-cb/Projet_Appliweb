package fr.enseeiht.jeux.dto;

import fr.enseeiht.jeux.modele.Invitation;

public class InvitationDTO {

    private Long id;
    private String statut;
    private String pseudoExpediteur;
    private Long expediteurId;
    private String pseudoDestinataire;
    private Long destinataireId;
    private Long partieId;

    public InvitationDTO() {
    }

    public InvitationDTO(Long id, String statut, String pseudoExpediteur, Long expediteurId,
                         String pseudoDestinataire, Long destinataireId, Long partieId) {
        this.id = id;
        this.statut = statut;
        this.pseudoExpediteur = pseudoExpediteur;
        this.expediteurId = expediteurId;
        this.pseudoDestinataire = pseudoDestinataire;
        this.destinataireId = destinataireId;
        this.partieId = partieId;
    }

    public static InvitationDTO fromEntity(Invitation inv) {
        return new InvitationDTO(
                inv.getId(),
                inv.getStatut(),
                inv.getExpediteur() != null ? inv.getExpediteur().getPseudo() : null,
                inv.getExpediteur() != null ? inv.getExpediteur().getId() : null,
                inv.getDestinataire() != null ? inv.getDestinataire().getPseudo() : null,
                inv.getDestinataire() != null ? inv.getDestinataire().getId() : null,
                inv.getPartie() != null ? inv.getPartie().getId() : null
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getPseudoExpediteur() {
        return pseudoExpediteur;
    }

    public void setPseudoExpediteur(String pseudoExpediteur) {
        this.pseudoExpediteur = pseudoExpediteur;
    }

    public Long getExpediteurId() {
        return expediteurId;
    }

    public void setExpediteurId(Long expediteurId) {
        this.expediteurId = expediteurId;
    }

    public String getPseudoDestinataire() {
        return pseudoDestinataire;
    }

    public void setPseudoDestinataire(String pseudoDestinataire) {
        this.pseudoDestinataire = pseudoDestinataire;
    }

    public Long getDestinataireId() {
        return destinataireId;
    }

    public void setDestinataireId(Long destinataireId) {
        this.destinataireId = destinataireId;
    }

    public Long getPartieId() {
        return partieId;
    }

    public void setPartieId(Long partieId) {
        this.partieId = partieId;
    }
}
