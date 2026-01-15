package com.risk.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transaction request model matching txn-simulator format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequest {

    @NotBlank(message = "transactionId is required")
    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("userId")
    private String userId;

    @JsonProperty("deviceType")
    private String deviceType;

    @JsonProperty("card1")
    private Double card1;

    @JsonProperty("card2")
    private Double card2;

    @JsonProperty("card3")
    private Double card3;

    @JsonProperty("card4")
    private String card4;

    @JsonProperty("card5")
    private Double card5;

    @JsonProperty("card6")
    private String card6;

    @JsonProperty("addr1")
    private Double addr1;

    @JsonProperty("addr2")
    private Double addr2;

    @JsonProperty("deviceInfo")
    private String deviceInfo;

    @JsonProperty("transactionAmt")
    private Double transactionAmt;

    @JsonProperty("dist1")
    private Double dist1;

    @JsonProperty("dist2")
    private String dist2;

    @JsonProperty("productCd")
    private String productCd;

    @JsonProperty("transactionDt")
    private Long transactionDt;
}
