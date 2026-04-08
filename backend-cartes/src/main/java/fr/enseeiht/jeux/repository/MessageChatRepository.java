package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.MessageChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageChatRepository extends JpaRepository<MessageChat, Long> {
}
