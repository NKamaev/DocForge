package com.docforge.user;

import com.docforge.common.exception.BusinessException;
import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.security.JwtTokenProvider;
import com.docforge.user.dto.LoginRequest;
import com.docforge.user.dto.LoginResponse;
import com.docforge.user.dto.RegisterRequest;
import com.docforge.user.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userRepository.existsByLogin(request.login())) {
            throw new BusinessException("Логин уже занят: " + request.login());
        }

        User user = new User();
        user.setLogin(request.login());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());

        user = userRepository.save(user);
        log.info("Зарегистрирован пользователь: {}", user.getLogin());

        String token = jwtTokenProvider.generateToken(user.getLogin());
        return toLoginResponse(token, user);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByLogin(request.login())
                .orElseThrow(() -> new BadCredentialsException("Неверный логин или пароль"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Неверный логин или пароль");
        }

        String token = jwtTokenProvider.generateToken(user.getLogin());
        log.info("Пользователь вошёл: {}", user.getLogin());
        return toLoginResponse(token, user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + id));
        return toDTO(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getCurrentUser(String login) {
        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + login));
        return toDTO(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getByLogin(String login) {
        User user = userRepository.findByLogin(login)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден: " + login));
        return toDTO(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDTO> findUser(String login) {
        if (login == null) {
            return Optional.empty();
        }
        return userRepository.findByLogin(login).map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDTO> findUserById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id).map(this::toDTO);
    }

    private LoginResponse toLoginResponse(String token, User user) {
        return new LoginResponse(token, user.getId(), user.getLogin(), user.getDisplayName(), user.isAdmin());
    }

    private UserDTO toDTO(User user) {
        return new UserDTO(user.getId(), user.getLogin(), user.getDisplayName(),
                user.getEmail(), user.isAdmin(), user.getCreatedAt());
    }
}
