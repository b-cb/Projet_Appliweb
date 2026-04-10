package fr.enseeiht.jeux.controller;

import fr.enseeiht.jeux.dto.MessageChatDTO;
import fr.enseeiht.jeux.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/partie")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * POST /api/partie/{id}/chat?utilisateurId=X
     * Body : { "contenu": "Belote !" }
     * Enregistre le message et le pousse en WebSocket.
     */
    @PostMapping("/{id}/chat")
    public ResponseEntity<MessageChatDTO> envoyerMessage(
            @PathVariable Long id,
            @RequestParam Long utilisateurId,
            @RequestBody Map<String, String> body) {

        String contenu = body.get("contenu");
        return ResponseEntity.ok(chatService.envoyerMessage(id, utilisateurId, contenu));
    }

    /**
     * GET /api/partie/{id}/chat
     * Retourne l'historique des messages de la partie (pour les nouveaux arrivants).
     */
    @GetMapping("/{id}/chat")
    public ResponseEntity<List<MessageChatDTO>> getHistorique(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.getHistorique(id));
    }
}
