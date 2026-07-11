package com.talentos.shared.security;

import com.talentos.shared.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final JwtConfig jwtConfig;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return generateToken(userPrincipal);
    }

    public String generateToken(UserPrincipal userPrincipal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getExpirationMs());

        // Extract role (take the first authority and strip ROLE_ prefix if needed)
        String role = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .findFirst()
                .orElse("CANDIDATE");

        return Jwts.builder()
                .subject(userPrincipal.getId().toString())
                .claim("email", userPrincipal.getEmail())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSignKey())
                .compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getSubject());
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getSignKey()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Invalid JWT token validation: {}", ex.getMessage());
        }
        return false;
    }
}
