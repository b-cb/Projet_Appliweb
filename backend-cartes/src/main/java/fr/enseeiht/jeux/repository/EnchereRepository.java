package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.Enchere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnchereRepository extends JpaRepository<Enchere, Long> {
}
