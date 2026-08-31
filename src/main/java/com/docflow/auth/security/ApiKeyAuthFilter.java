package com.docflow.auth.security;

import com.docflow.apikey.entity.ApiKey;
import com.docflow.apikey.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_KEY_PREFIX = "df_";
    private static final int STORED_PREFIX_LENGTH = 10;

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyAuthFilter(ApiKeyRepository apiKeyRepository, PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!rawKey.startsWith(API_KEY_PREFIX) || rawKey.length() < STORED_PREFIX_LENGTH) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        String keyPrefix = rawKey.substring(0, STORED_PREFIX_LENGTH);
        Optional<ApiKey> match = apiKeyRepository.findByKeyPrefixAndRevokedFalse(keyPrefix)
                .stream()
                .filter(key -> passwordEncoder.matches(rawKey, key.getKeyHash()))
                .findFirst();

        if (match.isEmpty()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
            return;
        }

        ApiKey apiKey = match.get();
        String jobPath = "/api/projects/" + apiKey.getProject().getId() + "/jobs";
        String requestPath = request.getRequestURI();
        if (!requestPath.equals(jobPath) && !requestPath.startsWith(jobPath + "/")) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    "This API key can only access jobs in its own project");
            return;
        }

        UserDetails principal = User.withUsername(apiKey.getProject().getOwner().getEmail())
                .password("")
                .authorities("ROLE_API_KEY")
                .build();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
