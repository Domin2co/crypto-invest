package com.cryptoinvest.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** 가입·로그인에서 비밀번호 원문을 저장하지 않고 서명 토큰만 반환한다. */
@Service
public class AuthService {
    private final UserAuthRepository users;
    private final AppTokenService tokens;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    public AuthService(UserAuthRepository users, AppTokenService tokens) { this.users = users; this.tokens = tokens; }
    public String register(String email, String password) {
        if (users.existsByEmail(email)) throw new IllegalArgumentException("Email is already registered");
        return tokens.issue(users.create(email, passwordEncoder.encode(password)));
    }
    public String login(String email, String password) {
        var user = users.findEnabledByEmail(email).filter(value -> passwordEncoder.matches(password, value.passwordHash()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        return tokens.issue(user.id());
    }
}
