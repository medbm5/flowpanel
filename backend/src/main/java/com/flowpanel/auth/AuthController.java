package com.flowpanel.auth;

import com.flowpanel.auth.AuthDtos.DemoLoginRequest;
import com.flowpanel.auth.AuthDtos.Me;
import com.flowpanel.auth.AuthDtos.Persona;
import com.flowpanel.config.FlowpanelProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "auth")
public class AuthController {

    private final AuthService authService;
    private final FlowpanelProperties.Jwt jwt;

    public AuthController(AuthService authService, FlowpanelProperties props) {
        this.authService = authService;
        this.jwt = props.jwt();
    }

    @GetMapping("/personas")
    public List<Persona> personas() {
        return authService.personas();
    }

    @PostMapping("/demo-login")
    public ResponseEntity<Me> demoLogin(@Valid @RequestBody DemoLoginRequest request) {
        AuthService.LoginResult result = authService.demoLogin(request.persona());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(result.token(), jwt.ttl()).toString())
                .body(result.me());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    public Me me() {
        return authService.me();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(jwt.cookieName(), value)
                .httpOnly(true)
                .secure(jwt.secureCookie())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
