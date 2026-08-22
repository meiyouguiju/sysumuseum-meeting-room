package edu.sysu.museummeetingroom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SchemaMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesAllCoreTables() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN (?, ?, ?, ?, ?, ?, ?)",
                Integer.class,
                "sys_user",
                "meeting_room",
                "booking",
                "booking_slot",
                "booking_audit_log",
                "idempotency_record",
                "flyway_schema_history");

        assertThat(tableCount).isEqualTo(7);
    }
}
