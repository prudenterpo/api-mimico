package com.rpo.mimico.securities;

import com.rpo.mimico.configs.JwtProperties;
import com.rpo.mimico.exceptions.InvalidTokenException;
import com.rpo.mimico.exceptions.TokenExpiredException;
import com.rpo.mimico.exceptions.TokenValidationExcepiton;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    @PostConstruct
    private void init() {
        this.secretKey = new SecretKeySpec(
                jwtProperties.getSecret().getBytes(),
                SignatureAlgorithm.HS256.getJcaName()
        );
    }

    public String generateToken(UUID userId, String email, String sessionId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpiration() * 1000);

        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email", email)
                .claim("sessionId", sessionId)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("Authentication token has expired", e);
        } catch (SignatureException e) {
            throw new InvalidTokenException("Invalid token or incorrect signature", e);
        } catch (Exception e) {
            throw new TokenValidationExcepiton("Error processing authentication token", e);
        }
    }

    public UUID extractUserId(String token) {
        Claims claims = validateToken(token);
        return UUID.fromString(claims.getSubject());
    }

    public String extractEmail(String token) {
        Claims claims = validateToken(token);
        return claims.get("email", String.class);
    }

    public String extractSessionId(String token) {
        Claims claims = validateToken(token);
        return claims.get("sessionId", String.class);
    }
}