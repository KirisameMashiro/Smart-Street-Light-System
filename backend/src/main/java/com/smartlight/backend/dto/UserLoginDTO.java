package com.smartlight.backend.dto;

import lombok.Data;

/**
 * 用户登录 DTO
 */
@Data
public class UserLoginDTO {
    private String username;
    private String password;
}
