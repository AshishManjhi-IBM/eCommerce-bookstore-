package com.bookstore.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.bookstore.common.ApiException;

public final class CurrentUser {

    private CurrentUser() {
    }

    /** The logged in user, or null for guests. */
    public static AuthUser orNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthUser u ? u : null;
    }

    public static AuthUser require() {
        AuthUser user = orNull();
        if (user == null) {
            throw ApiException.unauthorized("Authentication required");
        }
        return user;
    }

    public static boolean isAdmin() {
        AuthUser user = orNull();
        return user != null && user.isAdmin();
    }
}
