package com.finanzero.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String icon;
    private String color;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyLimit = BigDecimal.ZERO;

    @JsonIgnore
    @ManyToOne
    private AppUser owner;
}
