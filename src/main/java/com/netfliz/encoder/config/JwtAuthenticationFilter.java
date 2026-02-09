package com.netfliz.encoder.config;

import com.netfliz.encoder.constant.CommonConfig;
import com.netfliz.encoder.entity.enums.Role;
import com.netfliz.encoder.exception.BadCredentialException;
import com.netfliz.encoder.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (Arrays.stream(CommonConfig.WHITE_LIST_URL)
                .anyMatch(pattern -> pathMatcher.match(pattern, request.getServletPath()))) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeJsonForbidden(response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String username;

        try {
            username = jwtService.extractUsername(jwt);
        } catch (BadCredentialException e) {
            writeJsonForbidden(response);
            return;
        }

        if (Strings.isBlank(username)) {
            writeJsonForbidden(response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            // Validate JWT signature and expiry only (no DB query)
            if (jwtService.isTokenExpired(jwt)) {
                writeJsonForbidden(response);
                return;
            }

            // Extract role from JWT claims - trust gateway has validated
            String roleStr = jwtService.extractRole(jwt);
            Role role = Role.valueOf(roleStr);
            Set<SimpleGrantedAuthority> authorities = role.getAuthorities();

            // Create lightweight principal from JWT claims (no DB lookup)
            JwtPrincipal principal = new JwtPrincipal(
                    jwtService.extractClaim(jwt, claims -> claims.get("id", Long.class)),
                    username,
                    jwtService.extractClaim(jwt, claims -> claims.get("email", String.class)),
                    roleStr);

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities);
            authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        filterChain.doFilter(request, response);
    }

    private void writeJsonForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String json = """
                {
                  "code": 403,
                  "message": "Token không hợp lệ hoặc đã hết hạn!"
                }
                """;

        try (PrintWriter writer = response.getWriter()) {
            writer.write(json);
            writer.flush();
        }
    }

    public record JwtPrincipal(Long id, String username, String email, String role) {
    }
}
