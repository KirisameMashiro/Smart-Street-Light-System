package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.User;
import com.smartlight.backend.mapper.UserMapper;
import com.smartlight.backend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User login(String username, String password) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
               .eq(User::getPassword, DigestUtils.md5DigestAsHex(password.getBytes()))
               .eq(User::getStatus, 1);
        return this.getOne(wrapper);
    }

    @Override
    public User getByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return this.getOne(wrapper);
    }

    @Override
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        User user = this.getById(userId);
        if (user == null) {
            return false;
        }
        // 校验旧密码
        String oldMd5 = DigestUtils.md5DigestAsHex(oldPassword.getBytes());
        if (!oldMd5.equals(user.getPassword())) {
            return false;
        }
        // 加密新密码并更新
        user.setPassword(DigestUtils.md5DigestAsHex(newPassword.getBytes()));
        return this.updateById(user);
    }

    @Override
    public boolean save(User user) {
        // 新增用户时自动加密密码
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes()));
        }
        return super.save(user);
    }
}