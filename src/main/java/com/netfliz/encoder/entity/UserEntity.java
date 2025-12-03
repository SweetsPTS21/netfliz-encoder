package com.netfliz.encoder.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.netfliz.encoder.entity.converter.UserRoleConverter;
import com.netfliz.encoder.entity.converter.UserStatusConverter;
import com.netfliz.encoder.entity.converter.UserTypeConverter;
import com.netfliz.encoder.entity.enums.Role;
import com.netfliz.encoder.entity.enums.UserStatus;
import com.netfliz.encoder.entity.enums.UserType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class UserEntity implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String avatar;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName = "";

    @Column(name = "last_name")
    private String lastName = "";

    @Column(name = "password")
    private String password = "";

    private String username = "";

    private String phone = "";

    @Convert(converter = UserRoleConverter.class)
    private Role role = Role.USER;

    @Convert(converter = UserStatusConverter.class)
    private UserStatus status = UserStatus.ACTIVE;

    @Convert(converter = UserTypeConverter.class)
    private UserType type = UserType.COMMON;

    @Column(name = "created_at", insertable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private Instant updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }
}
