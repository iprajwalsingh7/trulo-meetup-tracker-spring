package com.trulo.trulomeetuptracker.dto;

import com.trulo.trulomeetuptracker.model.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String message;
    private String token;
    private User user;
}
