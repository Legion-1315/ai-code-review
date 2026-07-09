package com.codereview.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** Spring Security principal that also carries the application user id. */
public class AuthenticatedUser extends User {

    private final Long id;

    public AuthenticatedUser(Long id, String email, String passwordHash,
                             Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, authorities);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
