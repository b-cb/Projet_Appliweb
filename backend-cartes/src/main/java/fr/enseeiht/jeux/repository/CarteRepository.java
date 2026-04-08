package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.Carte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarteRepository extends JpaRepository<Carte, Long> {
}
