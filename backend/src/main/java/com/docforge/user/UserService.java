package com.docforge.user;

import com.docforge.user.dto.LoginRequest;
import com.docforge.user.dto.LoginResponse;
import com.docforge.user.dto.RegisterRequest;
import com.docforge.user.dto.UserDTO;

import java.util.UUID;

public interface UserService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    UserDTO getById(UUID id);

    UserDTO getCurrentUser(String login);

    UserDTO getByLogin(String login);
}
