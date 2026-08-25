package com.paypulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {     //bind everything under app: in application.yml into this class.

    private String jwtSecret;
    private String otpSecret;
    private String corsOrigins;
    private boolean maintenanceMode;
    private long initialWalletBalance;
    private final Payment payment = new Payment();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getOtpSecret() {
        return otpSecret;
    }

    public void setOtpSecret(String otpSecret) {
        this.otpSecret = otpSecret;
    }

    public String getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(String corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public void setMaintenanceMode(boolean maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
    }

    public long getInitialWalletBalance() {
        return initialWalletBalance;
    }

    public void setInitialWalletBalance(long initialWalletBalance) {
        this.initialWalletBalance = initialWalletBalance;
    }

    public Payment getPayment() {
        return payment;
    }

    public static class Payment {
        private String webhookSecret;
        private String webhookUrl;
        private int minDelayMs;
        private int maxDelayMs;
        private double successRate;
        private double gatewayFailureRate;
        private final CircuitBreaker circuitBreaker = new CircuitBreaker();

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public int getMinDelayMs() {
            return minDelayMs;
        }

        public void setMinDelayMs(int minDelayMs) {
            this.minDelayMs = minDelayMs;
        }

        public int getMaxDelayMs() {
            return maxDelayMs;
        }

        public void setMaxDelayMs(int maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
        }

        public double getSuccessRate() {
            return successRate;
        }

        public void setSuccessRate(double successRate) {
            this.successRate = successRate;
        }

        public double getGatewayFailureRate() {
            return gatewayFailureRate;
        }

        public void setGatewayFailureRate(double gatewayFailureRate) {
            this.gatewayFailureRate = gatewayFailureRate;
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }
    }

    public static class CircuitBreaker {
        private float failureRateThreshold;
        private float slowCallRateThreshold;
        private long slowCallDurationThresholdMs;
        private int slidingWindowSize;
        private int minimumNumberOfCalls;
        private int permittedNumberOfCallsInHalfOpenState;
        private long waitDurationOpenSeconds;

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        public long getSlowCallDurationThresholdMs() {
            return slowCallDurationThresholdMs;
        }

        public void setSlowCallDurationThresholdMs(long slowCallDurationThresholdMs) {
            this.slowCallDurationThresholdMs = slowCallDurationThresholdMs;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }

        public long getWaitDurationOpenSeconds() {
            return waitDurationOpenSeconds;
        }

        public void setWaitDurationOpenSeconds(long waitDurationOpenSeconds) {
            this.waitDurationOpenSeconds = waitDurationOpenSeconds;
        }
    }
}
