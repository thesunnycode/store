package com.ecommerce.dto.response;

import com.ecommerce.model.enums.Role;
import lombok.Builder;
import lombok.Data;

/**
 * AuthResponse — returned after successful register or login.
 * Contains the JWT token and basic user info the frontend needs to bootstrap the session.
 */
@Data
@Builder
public class AuthResponse {
    private String token;
    private String type;       // always "Bearer" — tells the client how to use the token
    private Long userId;
    private String name;
    private String email;
    private Role role;
}
