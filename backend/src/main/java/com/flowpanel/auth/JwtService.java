package com.flowpanel.auth;

import com.flowpanel.config.FlowpanelProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey key;
    private final FlowpanelProperties.Jwt config;

    public JwtService(FlowpanelProperties props) {
        this.config = props.jwt();
        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("name", user.getDisplayName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(config.ttl())));
        if (user.getTenantId() != null) {
            builder.claim("tid", user.getTenantId());
        }
        if (user.getSupplierId() != null) {
            builder.claim("sid", user.getSupplierId());
        }
        return builder.signWith(key).compact();
    }

    public Optional<CurrentUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new CurrentUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("name", String.class),
                    Role.valueOf(claims.get("role", String.class)),
                    toLong(claims.get("tid")),
                    toLong(claims.get("sid"))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
