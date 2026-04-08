package fr.enseeiht.jeux.repository;

import fr.enseeiht.jeux.modele.Joueur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JoueurRepository extends JpaRepository<Joueur, Long> {
    List<Joueur> findByPartie_Id(Long partieId);
}
