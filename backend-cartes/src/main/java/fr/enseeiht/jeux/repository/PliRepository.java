package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.Pli;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PliRepository extends JpaRepository<Pli, Long> {

    List<Pli> findByPartie_IdOrderByNumTourAsc(Long partieId);

    Optional<Pli> findByPartie_IdAndNumTour(Long partieId, int numTour);

    int countByPartie_Id(Long partieId);
}
