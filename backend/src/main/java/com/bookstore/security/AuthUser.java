package com.bookstore.security;

import com.bookstore.member.Role;

/** Authenticated principal taken from the JWT. */
public record AuthUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
