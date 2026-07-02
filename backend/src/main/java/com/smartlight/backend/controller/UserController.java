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

    /**
     * 获取当前用户个人信息
     */
    @GetMapping("/profile")
    public Result<User> getProfile(@RequestHeader("X-Username") String username) {
        if (username == null || username.isEmpty()) {
            return Result.unauthorized("未登录");
        }
        User user = userService.getByUsername(username);
        if (user == null) {
            return Result.unauthorized("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 更新当前用户个人信息
     */
    @PutMapping("/profile")
    public Result<Boolean> updateProfile(@RequestHeader("X-Username") String username, @RequestBody User update) {
        if (username == null || username.isEmpty()) {
            return Result.unauthorized("未登录");
        }
        User user = userService.getByUsername(username);
        if (user == null) {
            return Result.unauthorized("用户不存在");
        }
        if (update.getPhone() != null) user.setPhone(update.getPhone());
        if (update.getEmail() != null) user.setEmail(update.getEmail());
        if (update.getRealName() != null) user.setRealName(update.getRealName());
        return Result.success(userService.updateById(user));
    }
}
