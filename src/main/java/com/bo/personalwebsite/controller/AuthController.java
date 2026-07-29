package com.bo.personalwebsite.controller;

import com.bo.personalwebsite.common.ApiResponse;
import com.bo.personalwebsite.dto.LoginRequest;
import com.bo.personalwebsite.dto.LoginResponse;
import jakarta.validation.Valid;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        Subject subject = SecurityUtils.getSubject();
        try {
            subject.login(new UsernamePasswordToken(request.username(), request.password()));
            return ApiResponse.success(new LoginResponse(request.username(), "管理员"));
        } catch (AuthenticationException exception) {
            return ApiResponse.failed("用户名或密码错误");
        }
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Subject subject = SecurityUtils.getSubject();
        return ApiResponse.success(Map.of(
                "authenticated", subject.isAuthenticated(),
                "principal", subject.getPrincipal() == null ? "" : subject.getPrincipal()
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        SecurityUtils.getSubject().logout();
        return ApiResponse.success(true);
    }
}

