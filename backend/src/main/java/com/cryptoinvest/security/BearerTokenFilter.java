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
    public BearerTokenFilter(AppTokenService tokenService) { this.tokenService = tokenService; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && !header.isBlank()) {
            if (!header.startsWith("Bearer ")) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED); return; }
            try {
                var authentication = new UsernamePasswordAuthenticationToken(tokenService.verify(header.substring(7)), null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED); return;
            }
        }
        chain.doFilter(request, response);
    }
}
