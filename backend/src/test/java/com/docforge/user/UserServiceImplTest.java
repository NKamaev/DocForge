package com.docforge.user;

import com.docforge.security.JwtTokenProvider;
import com.docforge.user.dto.UserDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BUG-061 regression: не бросающие пользовательские lookup-ы {@code findUser()} /
 * {@code findUserById()}.
 * <p>
 * Раньше бизнес-методы вызывали бросающий {@code getCurrentUser(login)} и глушили
 * исключение. Вложенный {@code @Transactional}-вызов присоединялся к транзакции
 * вызывающего метода, помечал её как rollback-only, и на коммите бизнес-метод падал
 * с {@code UnexpectedRollbackException}. Поэтому новые методы обязаны:
 * <ul>
 *   <li>не бросать исключений для несуществующего пользователя;</li>
 *   <li>возвращать {@link Optional#empty()} вместо исключения.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserServiceImpl userService;

    // ── findUser(login) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("findUser() returns empty Optional for unknown login")
    void findUser_unknownLogin_returnsEmpty() {
        // given
        when(userRepository.findByLogin("ghost-user")).thenReturn(Optional.empty());

        // when — вызов не бросает исключений (иначе транзакция вызывающего
        // метода станет rollback-only → UnexpectedRollbackException, BUG-061);
        // транзакционная семантика проверяется в интеграционных тестах
        Optional<UserDTO> result = userService.findUser("ghost-user");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findUser() returns the DTO of an existing user")
    void findUser_existingLogin_returnsDto() {
        // given
        User user = user("real-user", "Real User");
        when(userRepository.findByLogin("real-user")).thenReturn(Optional.of(user));

        // when
        Optional<UserDTO> result = userService.findUser("real-user");

        // then
        assertThat(result).isPresent();
        UserDTO dto = result.orElseThrow();
        assertThat(dto.id()).isEqualTo(user.getId());
        assertThat(dto.login()).isEqualTo("real-user");
        assertThat(dto.displayName()).isEqualTo("Real User");
        assertThat(dto.email()).isEqualTo("real-user@example.com");
        assertThat(dto.admin()).isFalse();
        assertThat(dto.createdAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    @DisplayName("findUser(null) returns empty Optional without touching the repository")
    void findUser_nullLogin_returnsEmpty() {
        assertThat(userService.findUser(null)).isEmpty();

        verifyNoInteractions(userRepository);
    }

    // ── findUserById(id) ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findUserById() returns empty Optional for a random UUID")
    void findUserById_unknownId_returnsEmpty() {
        // given
        UUID randomId = UUID.randomUUID();
        when(userRepository.findById(randomId)).thenReturn(Optional.empty());

        // when — вызов не бросает исключений (иначе транзакция вызывающего
        // метода станет rollback-only → UnexpectedRollbackException, BUG-061)
        Optional<UserDTO> result = userService.findUserById(randomId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findUserById() returns the DTO of an existing user")
    void findUserById_existingId_returnsDto() {
        // given
        User user = user("real-user", "Real User");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        // when
        Optional<UserDTO> result = userService.findUserById(user.getId());

        // then
        assertThat(result).isPresent();
        UserDTO dto = result.orElseThrow();
        assertThat(dto.id()).isEqualTo(user.getId());
        assertThat(dto.login()).isEqualTo("real-user");
        assertThat(dto.displayName()).isEqualTo("Real User");
    }

    @Test
    @DisplayName("findUserById(null) returns empty Optional without touching the repository")
    void findUserById_nullId_returnsEmpty() {
        assertThat(userService.findUserById(null)).isEmpty();

        verifyNoInteractions(userRepository);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static User user(String login, String displayName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setLogin(login);
        user.setDisplayName(displayName);
        user.setEmail(login + "@example.com");
        user.setPasswordHash("hash");
        user.setAdmin(false);
        user.setCreatedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        return user;
    }
}
