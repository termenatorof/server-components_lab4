package com.example.server_components.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RoleUpdateRequestDto {

    @NotBlank(message = "Field 'role' is required")
    @Pattern(regexp = "user|auditor|admin", message = "Role must be one of: user, auditor, admin")
    private String role;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}