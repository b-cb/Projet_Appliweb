package fr.enseeiht.jeux.dto;

public class AuthResponse {

    private UtilisateurDTO utilisateur;
    private String token;

    public AuthResponse() {
    }

    public AuthResponse(UtilisateurDTO utilisateur, String token) {
        this.utilisateur = utilisateur;
        this.token = token;
    }

    public UtilisateurDTO getUtilisateur() {
        return utilisateur;
    }

    public void setUtilisateur(UtilisateurDTO utilisateur) {
        this.utilisateur = utilisateur;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
