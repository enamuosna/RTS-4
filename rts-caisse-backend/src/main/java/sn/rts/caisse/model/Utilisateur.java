package sn.rts.caisse.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Agent RTS habilité à utiliser le système (administrateur, superviseur, caissier).
 * Implémente {@link UserDetails} pour être directement consommable par Spring Security.
 */
@Entity
@Table(name = "utilisateurs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_utilisateur_login", columnNames = "login"),
                @UniqueConstraint(name = "uk_utilisateur_matricule", columnNames = "matricule")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur extends Auditable implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Matricule interne RTS (ex : RTS-A1234). */
    @NotBlank
    @Column(nullable = false, length = 30)
    private String matricule;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String login;

    @NotBlank
    @Column(nullable = false)
    private String motDePasse;

    @NotBlank
    @Column(nullable = false, length = 80)
    private String prenom;

    @NotBlank
    @Column(nullable = false, length = 80)
    private String nom;

    @Email
    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telephone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean actif = true;

    // ------------------------------------------------------------------
    //  Spring Security : UserDetails
    // ------------------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return motDePasse;
    }

    @Override
    public String getUsername() {
        return login;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return actif;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }

    public String getNomComplet() {
        return prenom + " " + nom;
    }
}
