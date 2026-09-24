package com.cryptoinvest.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authorization 값을 로그에 남기지 않고 유효한 Bearer 토큰만 SecurityContext에 넣는다. */
@Component
public class BearerTokenFilter extends OncePerRequestFilter {
    private final AppTokenService tokenService;
    private final UserAuthRepository users;
    public BearerTokenFilter(AppTokenService tokenService, UserAuthRepository users) { this.tokenService = tokenService; this.users = users; }

    /** 공개 endpoint에는 전달 경로의 불필요한 Authorization 값이 가입·공개 시세를 막지 않게 한다. */
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/") || path.startsWith("/api/markets/") || path.startsWith("/api/recommendations/") || path.equals("/api/health") || path.equals("/actuator/health");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && !header.isBlank()) {
            if (!header.startsWith("Bearer ")) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED); return; }
            final java.util.UUID userId;
            try {
                userId = tokenService.verify(header.substring(7));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED); return;
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            String path = request.getServletPath();
            boolean nicknameSetupPath = request.getRequestURI().endsWith("/api/account/profile") || request.getRequestURI().endsWith("/api/account/nickname") || request.getRequestURI().endsWith("/api/account/nickname/availability");
            if (!nicknameSetupPath && !users.hasNickname(userId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"NICKNAME_REQUIRED\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}