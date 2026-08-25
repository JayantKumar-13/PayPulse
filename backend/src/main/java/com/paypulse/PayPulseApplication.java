package com.paypulse;

import com.paypulse.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class PayPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayPulseApplication.class, args);
    }
}
