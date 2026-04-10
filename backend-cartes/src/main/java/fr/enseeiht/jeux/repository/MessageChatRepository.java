package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.MessageChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageChatRepository extends JpaRepository<MessageChat, Long> {

    List<MessageChat> findByPartie_IdOrderByDateAsc(Long partieId);
}
