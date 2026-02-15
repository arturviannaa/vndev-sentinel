package com.vndev.sentinel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDTO {
    private String cardToken;
    private String userId;
    private BigDecimal amount;
    private String merchantCategory;
    private Double latitude;
    private Double longitude;
}