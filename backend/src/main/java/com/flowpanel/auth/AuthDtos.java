package com.flowpanel.auth;

import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record DemoLoginRequest(@NotBlank String persona) {
    }

    public record NamedRef(Long id, String name) {
    }

    public record Me(Long userId, String persona, String displayName, String jobTitle, Role role,
                     NamedRef tenant, NamedRef supplier) {
    }

    public record Persona(String persona, String displayName, String jobTitle, Role role, String organization) {
    }
}
