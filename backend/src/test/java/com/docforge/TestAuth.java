package com.docforge;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Helpers for the security context in tests.
 *
 * <p>Since BUG-068 the {@code template} module resolves the caller from the security
 * context ({@code TemplateServiceImpl.assertCanModify}): only the owner of a template
 * ({@code template_registry.owner_id}) or an administrator may change it, everybody else
 * gets {@code 403 «Нет прав на изменение шаблона»}.</p>
 *
 * <p>Tests that call the service <em>directly</em> (not through HTTP) therefore have to
 * put an authentication into the context themselves — {@code @WithMockUser} would work
 * only if the login really exists in the table {@code users}, because the service
 * resolves the login through {@code UserService.findUser}.</p>
 *
 * <p>The helpers below imitate what the real security chain does
 * ({@code JwtAuthFilter} → {@code UserDetailsServiceImpl}): the principal is the login,
 * the authorities are {@code ROLE_USER} (plus {@code ROLE_ADMIN} for administrators).</p>
 *
 * <p><b>Both holders are managed.</b> Spring Security's test support keeps the
 * authentication in {@link TestSecurityContextHolder} as well, and the MockMvc setup
 * ({@code springSecurity()}) applies that authentication to <em>every</em> request — so a
 * leftover authentication would silently authenticate requests that carry no token at all.
 * Clearing only {@code SecurityContextHolder} is therefore not enough.</p>
 */
public final class TestAuth {

    /** Ordinary user authority (see {@code UserDetailsServiceImpl}). */
    public static final String ROLE_USER = "ROLE_USER";

    /** Administrator authority (see {@code UserDetailsServiceImpl}). */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private TestAuth() {
    }

    /** Authenticates the current thread as {@code login} with {@code ROLE_USER}. */
    public static void authenticateAs(String login) {
        authenticateAs(login, ROLE_USER);
    }

    /**
     * Authenticates the current thread as {@code login} with the given authorities.
     *
     * @param login login of the caller — it is resolved in the table {@code users}
     * @param roles authorities of the caller, e.g. {@link #ROLE_USER}, {@link #ROLE_ADMIN}
     */
    public static void authenticateAs(String login, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(login, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TestSecurityContextHolder.setContext(SecurityContextHolder.getContext());
    }

    /**
     * Executes {@code action} as {@code login} and restores the previous context afterwards.
     *
     * <p>Needed for the direct service calls: the security context of a test thread is
     * global, so a stray authentication would leak into the next call (and — through
     * {@code TestSecurityContextHolder} — into the next MockMvc request).</p>
     */
    public static void runAs(String login, Runnable action) {
        callAs(login, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Executes {@code action} as {@code login} and returns its result; the previous
     * security context is restored afterwards.
     */
    public static <T> T callAs(String login, Supplier<T> action) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        Authentication previousTest = TestSecurityContextHolder.getContext().getAuthentication();
        authenticateAs(login);
        try {
            return action.get();
        } finally {
            restore(previous, previousTest);
        }
    }

    /** Drops the authentication of the current thread (both holders). */
    public static void clear() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    private static void restore(Authentication previous, Authentication previousTest) {
        if (previous == null) {
            SecurityContextHolder.clearContext();
        } else {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
        if (previousTest == null) {
            TestSecurityContextHolder.clearContext();
        } else {
            TestSecurityContextHolder.getContext().setAuthentication(previousTest);
        }
    }
}
