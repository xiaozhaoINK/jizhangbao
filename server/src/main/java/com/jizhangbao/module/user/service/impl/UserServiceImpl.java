package com.jizhangbao.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jizhangbao.common.enums.UserRole;
import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;
import com.jizhangbao.common.util.CacheKeys;
import com.jizhangbao.common.util.CacheHelper;
import com.jizhangbao.module.category.service.CategoryService;
import com.jizhangbao.module.user.dto.ChangePasswordRequest;
import com.jizhangbao.module.user.dto.LoginRequest;
import com.jizhangbao.module.user.dto.LoginResponse;
import com.jizhangbao.module.user.dto.RegisterRequest;
import com.jizhangbao.module.user.dto.UpdateProfileRequest;
import com.jizhangbao.module.user.dto.UserVO;
import com.jizhangbao.module.user.entity.User;
import com.jizhangbao.module.user.mapper.UserMapper;
import com.jizhangbao.module.user.service.UserService;
import com.jizhangbao.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 用户服务实现。
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserMapper userMapper;
    private final CategoryService categoryService;
    private final JwtUtils jwtUtils;
    private final CacheHelper cacheHelper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(UserMapper userMapper, CategoryService categoryService,
                           JwtUtils jwtUtils, CacheHelper cacheHelper) {
        this.userMapper = userMapper;
        this.categoryService = categoryService;
        this.jwtUtils = jwtUtils;
        this.cacheHelper = cacheHelper;
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username().trim()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        User user = new User();
        user.setUsername(request.username().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname())
                ? request.nickname().trim() : request.username().trim());
        user.setRole(UserRole.USER.name());
        user.setStatus(1);
        userMapper.insert(user);

        // 新用户自动初始化内置分类，注册完即可记账
        categoryService.initDefaultCategories(user.getId());
        log.info("新用户注册成功: {}", user.getUsername());

        String token = jwtUtils.generate(user.getId(), user.getUsername(), user.getRole());
        return LoginResponse.of(token, jwtUtils.expireSeconds(), toVO(user));
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.username().trim())
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        user.setLastLogin(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtUtils.generate(user.getId(), user.getUsername(), user.getRole());
        cacheHelper.set(CacheKeys.userProfile(user.getId()), toVO(user), Duration.ofHours(1));
        return LoginResponse.of(token, jwtUtils.expireSeconds(), toVO(user));
    }

    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        long remain = jwtUtils.remainSeconds(token);
        if (remain > 0) {
            // 把 token 加入黑名单，剩余有效期过后自动清理
            cacheHelper.set(CacheKeys.tokenBlacklist(token), "1", Duration.ofSeconds(remain));
        }
    }

    @Override
    public UserVO me(Long userId) {
        return toVO(getById(userId));
    }

    @Override
    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getById(userId);
        if (StringUtils.hasText(request.nickname())) {
            user.setNickname(request.nickname().trim());
        }
        if (request.email() != null) {
            user.setEmail(request.email().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().trim());
        }
        if (StringUtils.hasText(request.avatar())) {
            user.setAvatar(request.avatar());
        }
        userMapper.updateById(user);
        cacheHelper.delete(CacheKeys.userProfile(userId));
        return toVO(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
    }

    @Override
    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public UserVO toVO(User user) {
        return new UserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getLastLogin(),
                user.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public User createIfAbsent(String username, String rawPassword, String nickname, String role) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(1);
        userMapper.insert(user);
        categoryService.initDefaultCategories(user.getId());
        return user;
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
