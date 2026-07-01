package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import com.smartlight.backend.dto.UserLoginDTO;
import com.smartlight.backend.entity.User;
import com.smartlight.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统用户管理 API
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<User> login(@RequestBody UserLoginDTO loginDTO) {
        User user = userService.login(loginDTO.getUsername(), loginDTO.getPassword());
        if (user == null) {
            return Result.unauthorized("用户名或密码错误");
        }
        user.setPassword(null);
        return Result.success("登录成功", user);
    }

    /**
     * 获取用户列表
     */
    @GetMapping
    public Result<List<User>> getAll() {
        List<User> users = userService.list();
        users.forEach(u -> u.setPassword(null));
        return Result.success(users);
    }

    /**
     * 新增用户
     */
    @PostMapping
    public Result<Boolean> add(@RequestBody User user) {
        return Result.success(userService.save(user));
    }

    /**
     * 更新用户
     */
    @PutMapping
    public Result<Boolean> update(@RequestBody User user) {
        return Result.success(userService.updateById(user));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(userService.removeById(id));
    }
}
