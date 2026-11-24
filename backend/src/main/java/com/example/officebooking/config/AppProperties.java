package com.example.officebooking.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        int officeCapacity,
        boolean allowSameDayBooking,
        int cancellationDeadlineHours,
        Jwt jwt,
        Cors cors,
        Bootstrap bootstrap) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Bootstrap(String managerEmail, String managerPassword, String managerFirstName,
                            String managerLastName, boolean demoData, String demoPassword) {}
}
