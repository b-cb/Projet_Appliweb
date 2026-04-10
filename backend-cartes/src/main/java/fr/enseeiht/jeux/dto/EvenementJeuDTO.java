package fr.enseeiht.jeux.dto;

/**
 * Message WebSocket générique poussé sur /topic/partie/{id}.
 * Le champ "type" permet au frontend de dispatcher le bon traitement.
 */
public class EvenementJeuDTO {

    public enum Type {
        JOUEUR_REJOINT,
        ENCHERE,
        CARTE_JOUEE,
        PLI_TERMINE,
        PARTIE_TERMINEE,
        CHAT
    }

    private Type type;
    private Object payload;  // dépend du type : EtatJeuDTO, MessageChatDTO, etc.

    public EvenementJeuDTO() {}

    public EvenementJeuDTO(Type type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public static EvenementJeuDTO of(Type type, Object payload) {
        return new EvenementJeuDTO(type, payload);
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }
}
