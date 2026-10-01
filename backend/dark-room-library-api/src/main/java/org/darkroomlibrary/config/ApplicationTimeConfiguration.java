package org.darkroomlibrary.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Owns the application's business clock. Persisted LocalDateTime values in this
 * project are wall-clock values and must therefore be produced in one explicit
 * zone rather than whichever zone happens to be configured on the host JVM.
 */
@Configuration
public class ApplicationTimeConfiguration {

    @Bean
    public ZoneId applicationZone(@Value("${app.time-zone:Asia/Shanghai}") String timeZone) {
        return ZoneId.of(timeZone);
    }

    @Bean
    public Clock applicationClock(ZoneId applicationZone) {
        return Clock.system(applicationZone);
    }
}
