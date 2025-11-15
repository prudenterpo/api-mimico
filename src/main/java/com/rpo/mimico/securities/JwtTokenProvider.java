package com.rpo.mimico.securities;

import com.rpo.mimico.exceptions.InvalidTokenException;
import com.rpo.mimico.exceptions.TokenExpiredException;
import com.rpo.mimico.exceptions.TokenValidationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    @PostConstruct
    private void init() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 32) {
            log.error("JWT secret key is too short! Current: {} bytes, Required: 32 bytes minimum", keyBytes.length);
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());

        log.info("JWT TokenProvider initialized successfully with HS256 algorithm");
    }

    public String generateToken(UUID userId, String email, String sessionId, List<String> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpiration() * 1000);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("sessionId", sessionId)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (ExpiredJwtException e) {
            log.debug("Token expired for user: {}", e.getClaims().getSubject());
            throw new TokenExpiredException("Authentication token has expired", e);

        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            throw new InvalidTokenException("Invalid token or incorrect signature", e);

        } catch (MalformedJwtException e) {
           log.error("Malformed JWT token: {}", e.getMessage());
           throw new InvalidTokenException("Malformed token", e);

        }catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
            throw new InvalidTokenException("Empty token", e);

        } catch (Exception e) {
            log.error("Unexpected error validating token: {}", e.getMessage(), e);
            throw new TokenValidationException("Error processing authentication token", e);
        }
    }

    public List<String> extractRoles(String token) {
        Claims claims = validateToken(token);

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);

        return roles != null ? roles : List.of();
    }
}