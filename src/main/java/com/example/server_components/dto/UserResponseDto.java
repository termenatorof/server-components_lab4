package com.example.server_components.dto;

import com.example.server_components.model.User;

public record UserResponseDto(Long id, String name, String role) {
    public static UserResponseDto fromEntity(User user) {
        return new UserResponseDto(user.getId(), user.getName(), user.getRole());
    }
}