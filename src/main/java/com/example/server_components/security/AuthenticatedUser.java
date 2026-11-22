package com.example.server_components.security;

public record AuthenticatedUser(Long id, String username, String role) {
    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public boolean isAuditor() {
        return "auditor".equals(role);
    }

    public boolean isUser() {
        return "user".equals(role);
    }
}