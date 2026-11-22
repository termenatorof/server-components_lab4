package com.example.server_components.repository;

import com.example.server_components.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByName(String name);
    boolean existsByName(String name);
}