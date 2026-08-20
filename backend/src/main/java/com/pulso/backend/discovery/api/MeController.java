package com.pulso.backend.discovery.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    @GetMapping
    public MeResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new MeResponse(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("role"),
                jwt.getAudience());
    }

    public record MeResponse(
            String subject,
            String email,
            String role,
            List<String> audience) {
    }
}
