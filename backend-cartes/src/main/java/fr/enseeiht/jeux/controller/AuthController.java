package fr.enseeiht.jeux.controller;

import fr.enseeiht.jeux.dto.AuthRequest;
import fr.enseeiht.jeux.dto.AuthResponse;
import fr.enseeiht.jeux.dto.UtilisateurDTO;
import fr.enseeiht.jeux.modele.Utilisateur;
import fr.enseeiht.jeux.service.AuthService;
import fr.enseeiht.jeux.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/inscrire")
    public ResponseEntity<AuthResponse> inscrire(@Valid @RequestBody AuthRequest request) {
        Utilisateur utilisateur = authService.inscrire(
                request.getPseudo().trim(),
                request.getMotDePasse());
        String token = jwtService.generateToken(utilisateur.getId(), utilisateur.getPseudo());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(UtilisateurDTO.fromEntity(utilisateur), token));
    }

    @PostMapping("/connexion")
    public ResponseEntity<AuthResponse> connexion(@Valid @RequestBody AuthRequest request) {
        Utilisateur utilisateur = authService.connexion(
                request.getPseudo().trim(),
                request.getMotDePasse());
        String token = jwtService.generateToken(utilisateur.getId(), utilisateur.getPseudo());
        return ResponseEntity.ok(new AuthResponse(UtilisateurDTO.fromEntity(utilisateur), token));
    }
}
