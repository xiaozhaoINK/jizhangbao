package com.jizhangbao.module.user.service;

import com.jizhangbao.module.user.dto.ChangePasswordRequest;
import com.jizhangbao.module.user.dto.LoginRequest;
import com.jizhangbao.module.user.dto.LoginResponse;
import com.jizhangbao.module.user.dto.RegisterRequest;
import com.jizhangbao.module.user.dto.UpdateProfileRequest;
import com.jizhangbao.module.user.dto.UserVO;
import com.jizhangbao.module.user.entity.User;

/**
 * 用户服务。
 */
public interface UserService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout(String token);

    UserVO me(Long userId);

    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    User getById(Long userId);

    UserVO toVO(User user);

    /** 系统初始化时创建账号（幂等） */
    User createIfAbsent(String username, String rawPassword, String nickname, String role);
}
