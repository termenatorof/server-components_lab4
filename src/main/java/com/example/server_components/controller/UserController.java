package com.example.server_components.controller;

import com.example.server_components.dto.RoleUpdateRequestDto;
import com.example.server_components.dto.UserResponseDto;
import com.example.server_components.security.AuthenticatedUser;
import com.example.server_components.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class UserController {

    private final ExpenseService expenseService;

    public UserController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponseDto>> getAllUsers(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(expenseService.getAllUsers(currentUser));
    }

    @GetMapping("/user/{user_id}")
    public ResponseEntity<UserResponseDto> getUserById(
            @PathVariable("user_id") Long userId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(expenseService.getUserById(userId, currentUser));
    }

    @PatchMapping("/user/{user_id}/role")
    public ResponseEntity<UserResponseDto> updateUserRole(
            @PathVariable("user_id") Long userId,
            @Valid @RequestBody RoleUpdateRequestDto request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(expenseService.updateUserRole(userId, request.getRole(), currentUser));
    }

    @DeleteMapping("/user/{user_id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable("user_id") Long userId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        expenseService.deleteUser(userId, currentUser);
        return ResponseEntity.noContent().build();
    }
}