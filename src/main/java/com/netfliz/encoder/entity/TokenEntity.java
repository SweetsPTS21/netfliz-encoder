package com.netfliz.encoder.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.netfliz.encoder.entity.enums.TokenType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tokens")
public class TokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", length = 50, nullable = false)
    private TokenType tokenType = TokenType.BEARER;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false)
    private boolean expired = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_token_user"))
    private UserEntity user;

    @Column(name = "created_at", insertable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Ho_Chi_Minh")
    private Instant updatedAt;
}
