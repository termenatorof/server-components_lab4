package com.example.server_components.service;

import com.example.server_components.dto.AuthRequestDto;
import com.example.server_components.dto.AuthResponseDto;
import com.example.server_components.dto.UserResponseDto;
import com.example.server_components.exception.ApiException;
import com.example.server_components.model.User;
import com.example.server_components.repository.UserRepository;
import com.example.server_components.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponseDto register(AuthRequestDto request) {
        String normalizedName = request.getName().trim();
        if (userRepository.existsByName(normalizedName)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "user_already_exists",
                    "User with name '" + normalizedName + "' already exists");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User saved = userRepository.save(new User(normalizedName, encodedPassword, "user"));
        return UserResponseDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponseDto login(AuthRequestDto request) {
        User user = userRepository.findByName(request.getName().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials",
                        "Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials",
                    "Invalid username or password");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponseDto(token, "Bearer", UserResponseDto.fromEntity(user));
    }
}