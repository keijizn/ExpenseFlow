package com.finanzero.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
    public TimeConfig(@Value("${app.time-zone:America/Sao_Paulo}") String zone) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(java.time.ZoneId.of(zone)));
    }
}
