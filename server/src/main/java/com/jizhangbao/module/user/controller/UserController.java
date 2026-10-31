package com.jizhangbao.module.user.controller;

import com.jizhangbao.common.api.Result;
import com.jizhangbao.module.user.dto.ChangePasswordRequest;
import com.jizhangbao.module.user.dto.UpdateProfileRequest;
import com.jizhangbao.module.user.dto.UserVO;
import com.jizhangbao.module.user.service.UserService;
import com.jizhangbao.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人资料接口。
 */
@Tag(name = "个人资料")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "获取个人资料")
    @GetMapping("/profile")
    public Result<UserVO> profile() {
        return Result.ok(userService.me(AuthContext.userId()));
    }

    @Operation(summary = "修改个人资料")
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok("资料已更新", userService.updateProfile(AuthContext.userId(), request));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(AuthContext.userId(), request);
        return Result.ok("密码修改成功", null);
    }
}
