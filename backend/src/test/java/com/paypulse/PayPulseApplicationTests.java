package com.paypulse;

import com.paypulse.dto.PaymentDtos;
import com.paypulse.repository.PaymentRepository;
import com.paypulse.repository.RefreshTokenRepository;
import com.paypulse.repository.TransactionRepository;
import com.paypulse.repository.UserRepository;
import com.paypulse.repository.WalletRepository;
import com.paypulse.service.TotpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PayPulseApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.data.redis.timeout=100ms",
    "app.payment.min-delay-ms=60000",
    "app.payment.max-delay-ms=60000",
    "app.payment.webhook-secret=test-webhook-secret"
})
class PayPulseApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TotpService totpService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
        walletRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void rootEndpointResponds() throws Exception {
        mockMvc.perform(get("/api"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.msg").value("working properly"))
            .andExpect(jsonPath("$.maintenance").value(false));
    }

    @Test
    void signupLoginWalletTransferAndTopupFlowWorks() throws Exception {
        String senderEmail = "alice@example.com";
        String receiverEmail = "bob@example.com";

        String senderOtp = totpService.generateOtp(senderEmail);
        JsonNode senderSignup = objectMapper.readTree(mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Alice",
                    "email", senderEmail,
                    "password", "password123",
                    "username", "alice_user",
                    "pin", "123456",
                    "otp", senderOtp
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());

        String receiverOtp = totpService.generateOtp(receiverEmail);
        JsonNode receiverSignup = objectMapper.readTree(mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Bob",
                    "email", receiverEmail,
                    "password", "password123",
                    "username", "bob_user",
                    "pin", "654321",
                    "otp", receiverOtp
                ))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());

        String senderToken = senderSignup.get("accessToken").asText();
        String receiverToken = receiverSignup.get("accessToken").asText();

        mockMvc.perform(get("/api/wallet")
                .header("Authorization", "Bearer " + senderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currency").value("INR"))
            .andExpect(jsonPath("$.status").value("Active"));

        mockMvc.perform(patch("/api/auth/change-pin")
                .header("Authorization", "Bearer " + senderToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "oldPin", "123456",
                    "newPin", "123456"
                ))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/transaction/transfer")
                .header("Authorization", "Bearer " + senderToken)
                .header("Idempotency-Key", "transfer-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "receiverUsername", "bob_user",
                    "amount", 250,
                    "pin", "123456",
                    "note", "Lunch"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.msg").value("Money sent successfully"));

        mockMvc.perform(get("/api/wallet/transactions")
                .header("Authorization", "Bearer " + senderToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("debit"));

        String paymentResponse = mockMvc.perform(post("/api/wallet/topup")
                .header("Authorization", "Bearer " + receiverToken)
                .header("Idempotency-Key", "topup-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("amount", 500))))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String paymentId = objectMapper.readTree(paymentResponse).get("paymentId").asText();
        PaymentDtos.PaymentWebhookRequest webhookRequest = new PaymentDtos.PaymentWebhookRequest(
            paymentId,
            receiverSignup.get("user").get("id").asText(),
            new BigDecimal("500"),
            "SUCCESS",
            "MOCK_GATEWAY",
            "mock_txn_123",
            null
        );
        String rawBody = objectMapper.writeValueAsString(webhookRequest);

        mockMvc.perform(post("/api/webhook/payment")
                .header("x-payment-signature", sign(rawBody, "test-webhook-secret"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(rawBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(get("/api/wallet/topup/{paymentId}", paymentId)
                .header("Authorization", "Bearer " + receiverToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUCCESS"));

        String receiverWallet = mockMvc.perform(get("/api/wallet")
                .header("Authorization", "Bearer " + receiverToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(objectMapper.readTree(receiverWallet).get("balance").decimalValue())
            .isGreaterThan(new BigDecimal("500000"));
    }

    private String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
