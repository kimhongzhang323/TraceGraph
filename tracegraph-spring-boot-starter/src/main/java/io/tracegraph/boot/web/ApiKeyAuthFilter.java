package io.tracegraph.boot.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects requests under {@code pathPrefix} that do not carry the configured API key.
 *
 * <p>Accepted header forms:
 * <ul>
 *   <li>{@code X-Api-Key: <key>}</li>
 *   <li>{@code Authorization: Bearer <key>}</li>
 * </ul>
 *
 * <p>OPTIONS (CORS preflight) requests are always passed through. A {@code null}
 * {@code expectedKey} denies every request — used for endpoints that must not be
 * reachable until a key is explicitly configured.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_X_API_KEY = "X-Api-Key";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String expectedKey;
    private final String pathPrefix;

    ApiKeyAuthFilter(String expectedKey) {
        this(expectedKey, "/tracegraph");
    }

    public ApiKeyAuthFilter(String expectedKey, String pathPrefix) {
        this.expectedKey = expectedKey;
        this.pathPrefix = pathPrefix;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String provided = extractKey(request);
        if (expectedKey == null || provided == null || !MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), expectedKey.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized — missing or invalid API key\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private static String extractKey(HttpServletRequest request) {
        String xApiKey = request.getHeader(HEADER_X_API_KEY);
        if (xApiKey != null && !xApiKey.isBlank()) {
            return xApiKey.strip();
        }
        String auth = request.getHeader(HEADER_AUTHORIZATION);
        if (auth != null && auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length()).strip();
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(pathPrefix);
    }
}
