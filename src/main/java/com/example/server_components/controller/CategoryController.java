package com.example.server_components.controller;

import com.example.server_components.dto.CategoryRequestDto;
import com.example.server_components.dto.CategoryResponseDto;
import com.example.server_components.security.AuthenticatedUser;
import com.example.server_components.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CategoryController {

    private final ExpenseService expenseService;

    public CategoryController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/category")
    public ResponseEntity<List<CategoryResponseDto>> getCategories(
            @RequestParam(value = "user_id", required = false) Long userId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(expenseService.getCategories(userId, currentUser));
    }

    @PostMapping("/category")
    public ResponseEntity<CategoryResponseDto> createPersonalCategory(
            @Valid @RequestBody CategoryRequestDto request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.createPersonalCategory(request, currentUser));
    }

    @PostMapping("/category/global")
    public ResponseEntity<CategoryResponseDto> createGlobalCategory(
            @Valid @RequestBody CategoryRequestDto request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.createGlobalCategory(request, currentUser));
    }

    @DeleteMapping("/category/{category_id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable("category_id") Long categoryId,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        expenseService.deleteCategory(categoryId, currentUser);
        return ResponseEntity.noContent().build();
    }
}