package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.Partie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PartieRepository extends JpaRepository<Partie, Long> {
}
