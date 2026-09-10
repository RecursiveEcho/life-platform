package com.backend.lifeplatform.user.service;

import com.backend.lifeplatform.user.dto.LoginDTO;
import com.backend.lifeplatform.user.dto.RegisterDTO;
import com.backend.lifeplatform.user.vo.LoginVO;

/** 用户认证业务接口。 */
public interface UserService {
    /** 用户注册。 */
    void register(RegisterDTO registerDTO);

    /** 用户登录，clientIp 用于登录限流。 */
    LoginVO login(LoginDTO loginDTO, String clientIp);
}
