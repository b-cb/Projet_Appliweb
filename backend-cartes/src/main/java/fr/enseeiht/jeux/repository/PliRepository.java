package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.Pli;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PliRepository extends JpaRepository<Pli, Long> {
}
