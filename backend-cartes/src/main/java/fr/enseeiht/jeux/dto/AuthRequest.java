package fr.enseeiht.jeux.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequest {

    @NotBlank(message = "Le pseudo est obligatoire.")
    @Size(min = 3, max = 20, message = "Le pseudo doit faire entre 3 et 20 caractères.")
    private String pseudo;

    @NotBlank(message = "Le mot de passe est obligatoire.")
    @Size(min = 4, max = 100, message = "Le mot de passe doit faire entre 4 et 100 caractères.")
    private String motDePasse;

    public AuthRequest() {
    }

    public AuthRequest(String pseudo, String motDePasse) {
        this.pseudo = pseudo;
        this.motDePasse = motDePasse;
    }

    public String getPseudo() {
        return pseudo;
    }

    public void setPseudo(String pseudo) {
        this.pseudo = pseudo;
    }

    public String getMotDePasse() {
        return motDePasse;
    }

    public void setMotDePasse(String motDePasse) {
        this.motDePasse = motDePasse;
    }
}
