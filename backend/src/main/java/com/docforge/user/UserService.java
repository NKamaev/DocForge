package com.docforge.user;

import com.docforge.user.dto.LoginRequest;
import com.docforge.user.dto.LoginResponse;
import com.docforge.user.dto.RegisterRequest;
import com.docforge.user.dto.UserDTO;

import java.util.Optional;
import java.util.UUID;

public interface UserService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    UserDTO getById(UUID id);

    UserDTO getCurrentUser(String login);

    UserDTO getByLogin(String login);

    /**
     * Пользователь по логину или пусто, если такого нет.
     * Исключение не бросает — важно для вызовов внутри транзакции:
     * бросок исключения из вложенного {@code @Transactional}-метода помечает
     * общую транзакцию как rollback-only, и внешний метод падает с
     * {@code UnexpectedRollbackException} при попытке закоммитить.
     *
     * @param login логин; {@code null} — вернётся пусто, репозиторий не
     *              запрашивается
     */
    Optional<UserDTO> findUser(String login);

    /**
     * Пользователь по id или пусто, если такого нет. Исключение не бросает —
     * см. {@link #findUser(String)}.
     *
     * @param id идентификатор; {@code null} — вернётся пусто, репозиторий не
     *           запрашивается
     */
    Optional<UserDTO> findUserById(UUID id);
}
