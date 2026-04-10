package fr.enseeiht.jeux.service;

import fr.enseeiht.jeux.dto.EvenementJeuDTO;
import fr.enseeiht.jeux.dto.MessageChatDTO;
import fr.enseeiht.jeux.exception.BusinessException;
import fr.enseeiht.jeux.exception.ResourceNotFoundException;
import fr.enseeiht.jeux.modele.MessageChat;
import fr.enseeiht.jeux.modele.Partie;
import fr.enseeiht.jeux.modele.Utilisateur;
import fr.enseeiht.jeux.repository.MessageChatRepository;
import fr.enseeiht.jeux.repository.PartieRepository;
import fr.enseeiht.jeux.repository.UtilisateurRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final MessageChatRepository messageChatRepository;
    private final PartieRepository partieRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatService(MessageChatRepository messageChatRepository,
                       PartieRepository partieRepository,
                       UtilisateurRepository utilisateurRepository,
                       SimpMessagingTemplate messagingTemplate) {
        this.messageChatRepository = messageChatRepository;
        this.partieRepository = partieRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Enregistre un message en base et le pousse en temps réel via WebSocket.
     */
    public MessageChatDTO envoyerMessage(Long partieId, Long utilisateurId, String contenu) {
        if (contenu == null || contenu.isBlank()) {
            throw new BusinessException("Le message ne peut pas être vide.");
        }
        if (contenu.length() > 300) {
            throw new BusinessException("Message trop long (300 caractères max).");
        }

        Partie partie = partieRepository.findById(partieId)
                .orElseThrow(() -> new ResourceNotFoundException("Partie #" + partieId + " introuvable."));
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur #" + utilisateurId + " introuvable."));

        MessageChat message = new MessageChat();
        message.setPartie(partie);
        message.setUtilisateur(utilisateur);
        message.setContenu(contenu.trim());
        message.setDate(LocalDateTime.now());
        messageChatRepository.save(message);

        MessageChatDTO dto = MessageChatDTO.fromEntity(message);

        // Push WebSocket sur le topic de la partie
        messagingTemplate.convertAndSend(
                "/topic/partie/" + partieId,
                EvenementJeuDTO.of(EvenementJeuDTO.Type.CHAT, dto)
        );

        return dto;
    }

    /**
     * Retourne l'historique des messages d'une partie (ordre chronologique).
     */
    public List<MessageChatDTO> getHistorique(Long partieId) {
        if (!partieRepository.existsById(partieId)) {
            throw new ResourceNotFoundException("Partie #" + partieId + " introuvable.");
        }
        return messageChatRepository.findByPartie_IdOrderByDateAsc(partieId)
                .stream()
                .map(MessageChatDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
