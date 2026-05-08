package com.docforge.document;

import com.docforge.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Резолвит UUID текущего пользователя по логину из SecurityContext. */
@Component
@RequiredArgsConstructor
public class DocumentOwnerResolver {

    private final UserService userService;

    public UUID resolve(String login) {
        return userService.getCurrentUser(login).id();
    }
}
