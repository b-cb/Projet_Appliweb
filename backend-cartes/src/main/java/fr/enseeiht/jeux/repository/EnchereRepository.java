package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.Enchere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnchereRepository extends JpaRepository<Enchere, Long> {

    List<Enchere> findByPartie_IdOrderByIdAsc(Long partieId);

    int countByPartie_Id(Long partieId);
}
