package com.example.officebooking.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    /** Единые «часы» приложения в часовом поясе Asia/Bishkek. */
    @Bean
    public Clock clock(AppProperties props) {
        return Clock.system(ZoneId.of(props.timezone()));
    }
}
