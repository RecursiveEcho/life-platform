package com.backend.lifeplatform.user.controller;

import com.backend.lifeplatform.common.result.Result;
import com.backend.lifeplatform.user.dto.LoginDTO;
import com.backend.lifeplatform.user.dto.RegisterDTO;
import com.backend.lifeplatform.user.service.UserService;
import com.backend.lifeplatform.user.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户认证接口，对外提供注册与登录两个能力。
 */
@Tag(name = "用户认证", description = "用户注册与登录接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@RequestBody @Valid RegisterDTO registerDTO){
        userService.register(registerDTO);
        return Result.success();
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody @Valid LoginDTO loginDTO,
                                 HttpServletRequest request) {
        return Result.success(userService.login(loginDTO, request.getRemoteAddr()));
    }
}
