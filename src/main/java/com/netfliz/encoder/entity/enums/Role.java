package com.netfliz.encoder.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netfliz.encoder.entity.enums.Permission.*;

@Getter
@RequiredArgsConstructor
public enum Role {

    USER(Collections.emptySet(), "USER"),
    ADMIN(
            Set.of(
                    ADMIN_READ,
                    ADMIN_UPDATE,
                    ADMIN_DELETE,
                    ADMIN_CREATE,
                    MANAGER_READ,
                    MANAGER_UPDATE,
                    MANAGER_DELETE,
                    MANAGER_CREATE
            ),
            "ADMIN"
    ),
    MANAGER(
            Set.of(
                    MANAGER_READ,
                    MANAGER_UPDATE,
                    MANAGER_DELETE,
                    MANAGER_CREATE
            ),
            "MANAGER"
    );

    private final Set<Permission> permissions;
    private final String name;

    public Set<SimpleGrantedAuthority> getAuthorities() {
        var authorities = getPermissions()
                .stream()
                .map(permission -> new SimpleGrantedAuthority(permission.getPermission()))
                .collect(Collectors.toSet());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + this.name()));
        return authorities;
    }

    public Set<Permission> getPermissions(String name) {
        if (Strings.isBlank(name)) {
            return Collections.emptySet();
        }

        for (var role : values()) {
            if (role.getName().equals(name)) {
                return role.getPermissions();
            }
        }

        return Collections.emptySet();
    }
}
