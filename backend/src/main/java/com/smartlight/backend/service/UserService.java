package com.smartlight.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.User;

public interface UserService extends IService<User> {

    /**
     * 用户登录
     */
    User login(String username, String password);

    /**
     * 根据用户名查询用户
     */
    User getByUsername(String username);

    /**
     * 修改密码（校验旧密码 + 加密新密码）
     */
    boolean changePassword(Long userId, String oldPassword, String newPassword);
}