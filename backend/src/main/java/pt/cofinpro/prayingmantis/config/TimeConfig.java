package pt.cofinpro.prayingmantis.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock the app asks for "today". It's in Lisbon time, not the server's zone, so the default year
 * (T-2.1) doesn't depend on where the app runs; Render's containers are on UTC. Tests can replace it.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId ZONE = ZoneId.of("Europe/Lisbon");

    @Bean
    Clock clock() {
        return Clock.system(ZONE);
    }
}
