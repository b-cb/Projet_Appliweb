package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    List<Invitation> findByDestinataire_Id(Long destinataireId);
    List<Invitation> findByPartie_Id(Long partieId);
}
