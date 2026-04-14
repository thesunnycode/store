package com.ecommerce.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JwtUtil — responsible for all JWT operations:
 * 1. Generating a signed JWT when a user logs in
 * 2. Extracting the username (email) from a JWT
 * 3. Validating that a JWT is authentic and not expired
 *
 * JWT structure:
 *   HEADER.PAYLOAD.SIGNATURE
 *   - Header: algorithm used (HS256)
 *   - Payload: claims (who, when, expiry)
 *   - Signature: HMAC-SHA256(header + payload, secret_key) — proves authenticity
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;             // loaded from application.properties

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;         // token lifetime in milliseconds (e.g., 86400000 = 24h)

    /**
     * Decodes the Base64-encoded secret from config into a usable HMAC key.
     * Keys.hmacShaKeyFor() creates a SecretKey that can sign and verify JWTs.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a JWT for the given user.
     * Claims are additional pieces of data embedded in the token payload.
     * Here we embed the user's role so we can extract it without hitting the DB.
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        // Extract the role string (e.g., "ROLE_CUSTOMER") and embed it in the token
        extraClaims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());

        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())       // subject = email (the "who")
                .issuedAt(new Date())                     // when the token was created
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs)) // expiry time
                .signWith(getSigningKey())                // sign with our secret key
                .compact();                               // serialize to a compact string
    }

    /** Extracts the email (subject) from the token payload */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Checks if the token belongs to this user and has not expired */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Generic method to extract any claim from the token.
     * claimsResolver is a function that takes Claims and returns the desired field.
     * Example: extractClaim(token, Claims::getSubject) returns the subject (email).
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses and verifies the JWT signature.
     * If the signature is invalid or the token is tampered with, this throws JwtException.
     * If the token is expired, this throws ExpiredJwtException.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
