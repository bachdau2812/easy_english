package com.bachdauduc.vocab_app.configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@Slf4j
@Configuration
public class TimeZoneConfig {
    @Value("${app.time-zone:Asia/Ho_Chi_Minh}")
    private String appTimeZone;

    @PostConstruct
    public void configureDefaultTimeZone() {
        TimeZone timeZone = TimeZone.getTimeZone(appTimeZone);
        TimeZone.setDefault(timeZone);
        log.info("Application default timezone configured: id={}, offsetHours={}",
                timeZone.getID(), timeZone.getRawOffset() / 3_600_000);
    }

    @Bean
    public Clock applicationClock() {
        return Clock.system(ZoneId.of(appTimeZone));
    }
}
