package com.demo.accessiblenav.auth;

import com.demo.accessiblenav.auth.dto.AuthResponse;
import com.demo.accessiblenav.auth.dto.TokenPair;
import com.demo.accessiblenav.exception.AuthenticationException;
import com.demo.accessiblenav.exception.BusinessException;
import com.demo.accessiblenav.exception.ErrorCode;
import com.demo.accessiblenav.security.PasswordPolicy;
import com.demo.accessiblenav.security.XssSanitizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountService userAccountService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(String username, String password) {
        // 净化用户名输入
        String sanitizedUsername = XssSanitizer.sanitizeUsername(username);

        UserAccount user = userAccountService.findByUsername(sanitizedUsername);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw AuthenticationException.invalidCredentials();
        }
        user = userAccountService.recordSuccessfulLogin(user);
        return buildAuthResponse(user);
    }

    public AuthResponse register(String username, String password) {
        // 净化用户名输入
        String sanitizedUsername = XssSanitizer.sanitizeUsername(username);

        // 验证用户名
        if (sanitizedUsername == null || sanitizedUsername.length() < 3) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户名至少需要3个字符");
        }
        if (sanitizedUsername.length() > 32) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "用户名不能超过32个字符");
        }

        // 验证密码策略
        PasswordPolicy.ValidationResult passwordValidation = PasswordPolicy.validate(password);
        if (!passwordValidation.isValid()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, passwordValidation.getErrorMessage());
        }

        // 检查用户名是否已存在
        if (userAccountService.findByUsername(sanitizedUsername) != null) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "用户名已存在: " + sanitizedUsername);
        }

        UserAccount user = userAccountService.register(sanitizedUsername, password);
        return buildAuthResponse(user);
    }

    /**
     * 刷新访问令牌
     */
    public AuthResponse refreshToken(String refreshToken) {
        TokenPair tokenPair = jwtService.refreshAccessToken(refreshToken);

        // 从新的访问令牌中获取用户信息
        io.jsonwebtoken.Claims claims = jwtService.parseToken(tokenPair.getAccessToken());
        String username = claims.getSubject();
        String roleStr = claims.get("role", String.class);
        UserRole role = UserRole.valueOf(roleStr);

        // 获取用户信用分
        UserAccount user = userAccountService.findByUsername(username);
        Integer creditScore = user != null ? user.getCreditScore() : 0;

        return new AuthResponse(
                tokenPair.getAccessToken(),
                tokenPair.getRefreshToken(),
                username,
                role,
                tokenPair.getAccessTokenExpiresIn(),
                tokenPair.getRefreshTokenExpiresIn(),
                creditScore != null ? creditScore : 0
        );
    }

    private AuthResponse buildAuthResponse(UserAccount user) {
        TokenPair tokenPair = jwtService.generateTokenPair(user.getUsername(), user.getRole());
        Integer score = user.getCreditScore();

        return new AuthResponse(
                tokenPair.getAccessToken(),
                tokenPair.getRefreshToken(),
                user.getUsername(),
                user.getRole(),
                tokenPair.getAccessTokenExpiresIn(),
                tokenPair.getRefreshTokenExpiresIn(),
                score == null ? 0 : score
        );
    }
}
