package com.example.server_components.service;

import com.example.server_components.dto.*;
import com.example.server_components.exception.ApiException;
import com.example.server_components.model.Category;
import com.example.server_components.model.ExpenseRecord;
import com.example.server_components.model.User;
import com.example.server_components.repository.CategoryRepository;
import com.example.server_components.repository.ExpenseRecordRepository;
import com.example.server_components.repository.UserRepository;
import com.example.server_components.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ExpenseService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRecordRepository recordRepository;

    public ExpenseService(UserRepository userRepository,
                          CategoryRepository categoryRepository,
                          ExpenseRecordRepository recordRepository) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.recordRepository = recordRepository;
    }

    // --- USERS ---
    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsers(AuthenticatedUser currentUser) {
        if (currentUser.isUser()) {
            throw forbidden("Only auditor or admin can view all users");
        }
        return userRepository.findAll().stream()
                .map(UserResponseDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id, AuthenticatedUser currentUser) {
        if (currentUser.isUser() && !currentUser.id().equals(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "User with id " + id + " not found");
        }
        return UserResponseDto.fromEntity(findUserEntity(id));
    }

    @Transactional
    public UserResponseDto updateUserRole(Long id, String newRole, AuthenticatedUser currentUser) {
        if (!currentUser.isAdmin()) {
            throw forbidden("Only admin can change user roles");
        }
        User user = findUserEntity(id);
        user.setRole(newRole);
        return UserResponseDto.fromEntity(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(Long id, AuthenticatedUser currentUser) {
        if (!currentUser.isAdmin()) {
            throw forbidden("Only admin can delete users");
        }
        userRepository.delete(findUserEntity(id));
    }

    // --- CATEGORIES (Глобальні та персональні + RBAC) ---
    @Transactional(readOnly = true)
    public List<CategoryResponseDto> getCategories(Long userId, AuthenticatedUser currentUser) {
        List<Category> categories;
        if (currentUser.isUser()) {
            if (userId != null && !userId.equals(currentUser.id())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "User categories not found");
            }
            categories = categoryRepository.findVisibleForUser(currentUser.id());
        } else if (userId == null) {
            categories = currentUser.isAdmin()
                    ? categoryRepository.findAll()
                    : categoryRepository.findByUserIsNull();
        } else {
            findUserEntity(userId);
            categories = categoryRepository.findVisibleForUser(userId);
        }
        return categories.stream()
                .map(CategoryResponseDto::fromEntity)
                .toList();
    }

    @Transactional
    public CategoryResponseDto createGlobalCategory(CategoryRequestDto request, AuthenticatedUser currentUser) {
        if (!currentUser.isAdmin()) {
            throw forbidden("Only admin can create global categories");
        }
        Category saved = categoryRepository.save(new Category(request.getName().trim(), null));
        return CategoryResponseDto.fromEntity(saved);
    }

    @Transactional
    public CategoryResponseDto createPersonalCategory(CategoryRequestDto request, AuthenticatedUser currentUser) {
        if (currentUser.isAuditor()) {
            throw forbidden("Auditor has read-only access");
        }

        User owner;
        if (currentUser.isUser()) {
            if (request.getUserId() != null && !request.getUserId().equals(currentUser.id())) {
                throw forbidden("Regular user can create personal categories only for themselves");
            }
            owner = findUserEntity(currentUser.id());
        } else {
            // admin
            owner = request.getUserId() != null ? findUserEntity(request.getUserId()) : null;
        }

        Category saved = categoryRepository.save(new Category(request.getName().trim(), owner));
        return CategoryResponseDto.fromEntity(saved);
    }

    @Transactional
    public void deleteCategory(Long categoryId, AuthenticatedUser currentUser) {
        if (currentUser.isAuditor()) {
            throw forbidden("Auditor has read-only access");
        }

        Category category = findCategoryEntity(categoryId);
        // Глобальна категорія (user_id == null) — видаляє лише admin
        if (category.getUser() == null) {
            if (!currentUser.isAdmin()) {
                throw forbidden("Only admin can delete global categories");
            }
        } else {
            // Персональна категорія — user може видалити лише свою (інакше 404), admin — будь-яку
            if (currentUser.isUser() && !category.getUser().getId().equals(currentUser.id())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "not_found",
                        "Category with id " + categoryId + " not found");
            }
        }
        categoryRepository.delete(category);
    }

    // --- RECORDS ---
    @Transactional
    public RecordResponseDto createRecord(RecordRequestDto request, AuthenticatedUser currentUser) {
        if (currentUser.isAuditor()) {
            throw forbidden("Auditor has read-only access");
        }

        if (currentUser.isUser() && !request.getUserId().equals(currentUser.id())) {
            throw forbidden("Regular user can create expense records only for themselves");
        }

        User targetUser = findUserEntity(request.getUserId());
        Category category = findCategoryEntity(request.getCategoryId());

        if (category.getUser() != null && !category.getUser().getId().equals(targetUser.getId())) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "not_found",
                    "Category with id " + request.getCategoryId() + " not found"
            );
        }

        String createdAt = (request.getCreatedAt() != null && !request.getCreatedAt().isBlank())
                ? request.getCreatedAt()
                : Instant.now().toString();

        ExpenseRecord saved = recordRepository.save(
                new ExpenseRecord(targetUser, category, createdAt, request.getAmount())
        );
        return RecordResponseDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public RecordResponseDto getRecordById(Long id, AuthenticatedUser currentUser) {
        ExpenseRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found",
                        "Record with id " + id + " not found"));

        if (currentUser.isUser() && !record.getUser().getId().equals(currentUser.id())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found",
                    "Record with id " + id + " not found");
        }
        return RecordResponseDto.fromEntity(record);
    }

    @Transactional
    public void deleteRecord(Long id, AuthenticatedUser currentUser) {
        if (currentUser.isAuditor()) {
            throw forbidden("Auditor has read-only access");
        }

        ExpenseRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found",
                        "Record with id " + id + " not found"));

        if (currentUser.isUser() && !record.getUser().getId().equals(currentUser.id())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found",
                    "Record with id " + id + " not found");
        }
        recordRepository.delete(record);
    }

    @Transactional(readOnly = true)
    public List<RecordResponseDto> filterRecords(Long userId, Long categoryId, AuthenticatedUser currentUser) {
        if (userId == null && categoryId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_request",
                    "At least one query parameter ('user_id' or 'category_id') is required for filtration"
            );
        }

        Long effectiveUserId = userId;
        if (currentUser.isUser()) {
            if (userId != null && !userId.equals(currentUser.id())) {
                throw forbidden("Regular user can filter records only by their own user_id");
            }
            effectiveUserId = currentUser.id();
        }

        return recordRepository.filterRecords(effectiveUserId, categoryId).stream()
                .map(RecordResponseDto::fromEntity)
                .toList();
    }

    private ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", message);
    }

    private User findUserEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found",
                        "User with id " + id + " not found"));
    }

    private Category findCategoryEntity(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found",
                        "Category with id " + id + " not found"));
    }
}