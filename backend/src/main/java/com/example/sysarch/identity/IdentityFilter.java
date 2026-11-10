package com.example.sysarch.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Objects;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Attaches the request's principal (resolved lazily by {@link Identities}) for {@link CurrentUser} (P2 T8). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdentityFilter extends OncePerRequestFilter {

    private final Identities identities;

    public IdentityFilter(Identities identities) {
        this.identities = Objects.requireNonNull(identities);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        CurrentUser.attach(request, () -> this.identities.resolve(request));
        chain.doFilter(request, response);
    }
}
