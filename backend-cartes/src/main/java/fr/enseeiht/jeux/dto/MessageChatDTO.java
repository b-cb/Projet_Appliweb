package fr.enseeiht.jeux.dto;

import fr.enseeiht.jeux.modele.MessageChat;

import java.time.LocalDateTime;

public class MessageChatDTO {

    private Long id;
    private String pseudo;
    private String contenu;
    private LocalDateTime date;

    public MessageChatDTO() {}

    public static MessageChatDTO fromEntity(MessageChat m) {
        MessageChatDTO dto = new MessageChatDTO();
        dto.id = m.getId();
        dto.pseudo = m.getUtilisateur().getPseudo();
        dto.contenu = m.getContenu();
        dto.date = m.getDate();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPseudo() { return pseudo; }
    public void setPseudo(String pseudo) { this.pseudo = pseudo; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }
}
