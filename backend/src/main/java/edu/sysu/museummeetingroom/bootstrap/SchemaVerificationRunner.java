package edu.sysu.museummeetingroom.bootstrap;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!pin-maintenance & !user-bootstrap & !legacy-import")
@RequiredArgsConstructor
public class SchemaVerificationRunner implements ApplicationRunner {

    private static final List<String> REQUIRED_TABLES = List.of(
            "sys_user",
            "meeting_room",
            "booking",
            "booking_slot",
            "booking_audit_log",
            "idempotency_record",
            "flyway_schema_history");

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN (?, ?, ?, ?, ?, ?, ?)",
                Integer.class,
                REQUIRED_TABLES.toArray());

        if (tableCount == null || tableCount != REQUIRED_TABLES.size()) {
            throw new IllegalStateException("Flyway schema verification failed: expected all core tables and flyway_schema_history");
        }
    }
}
