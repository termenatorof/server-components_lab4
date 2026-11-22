package com.example.server_components.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequestDto {

    @NotBlank(message = "Field 'name' (or 'username') is required")
    @Size(min = 2, max = 100, message = "Username must be between 2 and 100 characters")
    @JsonAlias({"username", "login"})
    private String name;

    @NotBlank(message = "Field 'password' is required")
    @Size(min = 4, max = 128, message = "Password must be at least 4 characters")
    private String password;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}