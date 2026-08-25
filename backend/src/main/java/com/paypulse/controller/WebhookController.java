package com.paypulse.controller;

import com.paypulse.dto.PaymentDtos;
import com.paypulse.exception.ApiException;
import com.paypulse.service.WebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/webhook", "/webhook"})
public class WebhookController {

    //    /api/webhook for internal consistency
    //webhook for easier configuration in payment gateways

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;    // Used for JSON String -> Java object
    // ModelMapper is used for Java object ->  Java object

    public WebhookController(WebhookService webhookService , ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }


    @PostMapping("/payment")
    public PaymentDtos.WebhookResponse handlePaymentWebhook(@RequestBody String rawBody,
                                                            @RequestHeader(value = "x-payment-signature", required = false) String signature,
                                                            HttpServletRequest request) {

        //Instead of directly deserializing to a DTO, I first receive the exact raw JSON string.
        //Because webhook signatures are usually calculated over the exact raw payload bytes.
        //If Spring deserialized it first, formatting could change and signature verification might fail.

        if (!webhookService.verifySignature(rawBody, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid payment webhook signature");
        }
        try {

            PaymentDtos.PaymentWebhookRequest payload =
                    objectMapper.readValue(rawBody, PaymentDtos.PaymentWebhookRequest.class);

            return webhookService.processPaymentWebhook(payload);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment webhook payload");
        }
    }
}
