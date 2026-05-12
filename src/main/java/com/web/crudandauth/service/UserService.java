package com.web.crudandauth.service;

import com.web.crudandauth.dtos.requests.LoginRequestDto;
import com.web.crudandauth.dtos.requests.RegisterRequestDto;
import com.web.crudandauth.dtos.response.LoginResponseDto;
import com.web.crudandauth.dtos.response.PaginationResponseDto;
import com.web.crudandauth.dtos.response.RegisterResponseDto;
import com.web.crudandauth.entities.User;
import com.web.crudandauth.enums.Roles;
import com.web.crudandauth.exceptionHandler.EmailAlreadyExistsException;
import com.web.crudandauth.exceptionHandler.InvalidPasswordException;
import com.web.crudandauth.exceptionHandler.UserNotFoundException;
import com.web.crudandauth.repositories.UserRepositories;
import com.web.crudandauth.server.JwtServerClient;
import com.web.crudandauth.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepositories userRepositories;
    private final JwtServerClient jwtServerClient;

    // ── REGISTER ──────────────────────────────────────────────────────────────
    public RegisterResponseDto register(RegisterRequestDto dto) {
        ValidationUtil.validateEmail(dto.getEmail());
        ValidationUtil.validateName(dto.getFirstName());
        ValidationUtil.validateName(dto.getLastName());

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidPasswordException("Passwords don't match");
        }
        if (userRepositories.findByEmail(dto.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered: " + dto.getEmail());
        }

        User user = userRepositories.save(User.builder()
                .honor(dto.getHonor())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .gender(dto.getGender())
                .email(dto.getEmail())
                .password(dto.getPassword())
                .role(dto.getRole() != null ? dto.getRole() : Roles.USER)
                .build());

        log.info("Registered new user: {}", user.getEmail());

        return RegisterResponseDto.builder()
                .success(true).status("201")
                .message("Registration successful. Please log in.")
                .email(user.getEmail()).role(user.getRole())
                .build();
    }

    // ── LOGIN ─────────────────────────────────────────────────────────────────
    public LoginResponseDto login(LoginRequestDto dto) {
        ValidationUtil.validateEmail(dto.getEmail());

        User user = userRepositories.findByEmail(dto.getEmail()).orElseThrow(() -> new UserNotFoundException("No account found for: " + dto.getEmail()));

        if (!user.getPassword().equals(dto.getPassword())) {
            throw new InvalidPasswordException("Incorrect password.");
        }

        log.info("User credentials verified: {}", user.getEmail());

        return LoginResponseDto.builder()
                .success(true)
                .status("200")
                .message("Credentials verified.")
                .email(user.getEmail())
                .build();
    }

    public LoginResponseDto generateLoginToken(User user) {

        JwtServerClient.TokenResult tokenResult =
                jwtServerClient.generateToken(
                        user.getEmail(),
                        user.getPassword(),
                        user.getRole().name()
                );

        return LoginResponseDto.builder()
                .success(true)
                .status("200")
                .message("Login successful.")
                .email(user.getEmail())
                .token(tokenResult.token())
                .expiresInMs(tokenResult.expiresInMs())
                .build();
    }

    // ── FIND BY EMAIL ─────────────────────────────────────────────────────────
    public User findByEmail(String email) {
        return userRepositories.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + email));
    }

    // ── FIND BY ID ────────────────────────────────────────────────────────────
    public User findById(String id) {
        return userRepositories.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    // ── UPDATE PROFILE ────────────────────────────────────────────────────────
    public User updateProfile(String email, RegisterRequestDto dto) {
        User user = findByEmail(email);
        user.setHonor(dto.getHonor());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setGender(dto.getGender());
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPassword(dto.getPassword());
        }
        userRepositories.save(user);
        log.info("Profile updated for: {}", email);
        return user;
    }

    // ── COUNT BY ROLE ─────────────────────────────────────────────────────────
    public long countByRole(Roles role) {
        return userRepositories.countByRole(role);
    }

    // ── ADMIN: PAGINATED + SEARCHABLE USER LIST ───────────────────────────────
    // q = null or blank → list all; q = non-blank → search
    // rawPage is 0-based (Spring Data convention)
    public PaginationResponseDto getUsers(int rawPage, int size, String q) {
        Pageable pageable = PageRequest.of(rawPage, size);

        Page<User> userPage = (q != null && !q.isBlank())
                ? userRepositories.searchByRoleNot(Roles.ADMIN, q.trim(), pageable)
                : userRepositories.findByRoleNot(Roles.ADMIN, pageable);

        return new PaginationResponseDto(
                userPage.getContent(),
                rawPage,
                size,
                (int) userPage.getTotalElements(),
                userPage.getTotalPages()
        );
    }

    // ── ADMIN: REGISTER USER ──────────────────────────────────────────────────
    public RegisterResponseDto adminRegisterUser(RegisterRequestDto dto) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidPasswordException("Passwords don't match");
        }
        if (userRepositories.findByEmail(dto.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered: " + dto.getEmail());
        }

        User user = userRepositories.save(User.builder()
                .honor(dto.getHonor())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .gender(dto.getGender())
                .email(dto.getEmail())
                .password(dto.getPassword())
                .role(dto.getRole() != null ? dto.getRole() : Roles.USER)
                .build());

        log.info("Admin registered new user: {}", user.getEmail());

        return RegisterResponseDto.builder()
                .success(true).status("201")
                .message("User registered successfully.")
                .email(user.getEmail()).role(user.getRole())
                .build();
    }

    // ── ADMIN: UPDATE USER ────────────────────────────────────────────────────
    public User adminUpdateUser(String id, RegisterRequestDto dto) {
        User user = findById(id);
        user.setHonor(dto.getHonor());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setGender(dto.getGender());
        user.setEmail(dto.getEmail());
        if (dto.getRole() != null) user.setRole(dto.getRole());
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPassword(dto.getPassword());
        }
        userRepositories.save(user);
        log.info("Admin updated user id: {}", id);
        return user;
    }

    // ── ADMIN: DELETE USER ────────────────────────────────────────────────────
    public void deleteById(String id) {
        if (!userRepositories.existsById(id)) {
            throw new UserNotFoundException("User not found with id: " + id);
        }
        userRepositories.deleteById(id);
        log.info("Admin deleted user id: {}", id);
    }
}