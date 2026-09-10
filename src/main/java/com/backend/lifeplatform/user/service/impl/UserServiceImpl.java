package com.backend.lifeplatform.user.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.common.utils.JwtUtils;
import com.backend.lifeplatform.common.utils.RedisRateLimiter;
import com.backend.lifeplatform.user.dto.LoginDTO;
import com.backend.lifeplatform.user.dto.RegisterDTO;
import com.backend.lifeplatform.user.entity.User;
import com.backend.lifeplatform.user.mapper.UserMapper;
import com.backend.lifeplatform.user.service.UserService;
import com.backend.lifeplatform.user.vo.LoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户认证业务实现。
 *
 * <p>注册和登录各自是一条完整流程，相关校验和数据操作直接放在对应方法中，
 * 不再在多个小方法之间来回跳转。</p>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RedisRateLimiter redisRateLimiter;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    // ==================== 注册 ====================

    /** 注册用户，并保存 BCrypt 密码密文。 */
    @Override
    public void register(RegisterDTO registerDTO) {
        String phone = registerDTO.getPhone();
        boolean exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone)) > 0;
        if (exists) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_REGISTERED, "手机号已被注册");
        }

        User user = new User();
        user.setPhone(phone);
        user.setStatus(1);
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setRole("USER");

        if (userMapper.insert(user) != 1) {
            throw new BusinessException(ErrorCode.DB_ERROR, "注册失败");
        }
    }

    // ==================== 登录 ====================

    /** 登录：限流 -> 查询账号 -> 校验状态和密码 -> 签发 JWT。 */
    @Override
    public LoginVO login(LoginDTO loginDTO, String clientIp) {
        String phone = loginDTO.getPhone();
        // 限流 key 同时绑定 IP 和手机号，兼顾防单 IP 暴力破解与针对账号的撞库。
        redisRateLimiter.check("rate_limit:login:" + clientIp + ":" + phone, 5);

        if (!StringUtils.hasText(phone) || !StringUtils.hasText(loginDTO.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 手机号应有唯一约束，LIMIT 1 作为脏数据场景下的兜底。
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 数据库存的是 BCrypt 密文，必须用 matches 校验，不能直接比较明文。
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 老数据可能没有 role 字段，兜底成 USER，避免签发没有角色的令牌。
        String role = StringUtils.hasText(user.getRole()) ? user.getRole() : "USER";
        String token = jwtUtils.generateToken(user.getId(), user.getPhone(), role);
        return new LoginVO(user.getId(), user.getPhone(), token);
    }
}
