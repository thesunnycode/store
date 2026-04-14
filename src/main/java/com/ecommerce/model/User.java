package com.ecommerce.model;

import com.ecommerce.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * User entity — maps to the "users" table in MySQL.
 *
 * This class also implements UserDetails, which is Spring Security's interface
 * for a logged-in user. By implementing it here, we avoid creating a separate
 * wrapper class — Spring Security can work with User objects directly.
 */
@Entity
@Table(name = "users")                     // maps to the "users" table (avoid reserved word "user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto-increment primary key
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true) // email must be unique — used as login username
    private String email;

    @Column(nullable = false)
    private String password;               // stored as bcrypt hash, NEVER plain text

    @Enumerated(EnumType.STRING)           // stores "ADMIN" or "CUSTOMER" as text in DB (not 0/1)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, updatable = false) // set once at creation, never updated
    private LocalDateTime createdAt;

    @PrePersist                            // JPA lifecycle hook — called before INSERT
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Spring Security UserDetails methods ─────────────────────────────────

    /**
     * Returns the authorities (permissions) granted to this user.
     * Spring Security checks these to decide if a user can access a secured endpoint.
     * We prefix with "ROLE_" because Spring Security's @PreAuthorize("hasRole('ADMIN')")
     * internally looks for "ROLE_ADMIN" in the authorities list.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;                      // we use email as the username for login
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
