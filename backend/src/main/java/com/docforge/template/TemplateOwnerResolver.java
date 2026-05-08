package com.docforge.template;

import com.docforge.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Резолвит UUID текущего пользователя по логину из SecurityContext.
 * Инкапсулирует зависимость от UserService, чтобы не тянуть её напрямую в контроллер.
 */
@Component
@RequiredArgsConstructor
public class TemplateOwnerResolver {

    private final UserService userService;

    public UUID resolve(String login) {
        return userService.getCurrentUser(login).id();
    }
}
