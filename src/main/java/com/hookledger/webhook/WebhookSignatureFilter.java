package com.hookledger.webhook;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebhookSignatureFilter extends OncePerRequestFilter {

    private final WebhookSignatureVerifier verifier;

    public WebhookSignatureFilter(WebhookSignatureVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().equals("/api/webhooks");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!verifier.isConfigured()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Webhook signing is not configured");
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        String signature = request.getHeader(WebhookSignatureHeaders.SIGNATURE);
        if (!verifier.verify(body, signature)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"invalid signature\"}");
            return;
        }

        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }
}
