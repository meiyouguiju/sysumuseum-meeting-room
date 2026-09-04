package edu.sysu.museummeetingroom.legacyimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class LegacyImportOfficialWorkbookDryRunTest {

    private static final String OFFICIAL_FILE_PROPERTY = "legacy.import.official-file";

    private final LegacyImportWorkbookParser parser = new LegacyImportWorkbookParser();

    @Test
    @EnabledIfSystemProperty(named = OFFICIAL_FILE_PROPERTY, matches = ".+")
    void parsesTheOfficialWorkbookWithoutWritingToDatabase() {
        Path workbookPath = Path.of(System.getProperty(OFFICIAL_FILE_PROPERTY));

        List<LegacyImportRecord> records = parser.parse(workbookPath);

        assertThat(records).hasSize(842);
        assertThat(records.stream().filter(record -> "2026年".equals(record.sheetName())).count()).isEqualTo(168);
        assertThat(records.stream().filter(record -> "2025".equals(record.sheetName())).count()).isEqualTo(235);
        assertThat(records.stream().filter(record -> "2024".equals(record.sheetName())).count()).isEqualTo(439);
        assertThat(records.stream().filter(record -> "ACTIVE".equals(record.status())).count()).isEqualTo(840);
        assertThat(records.stream().filter(record -> "CANCELLED".equals(record.status())).count()).isEqualTo(2);
        assertThat(records.stream()
                .filter(record -> record.startTime().getMinute() % 30 != 0
                        || record.endTime().getMinute() % 30 != 0)
                .count())
                .isEqualTo(23);
        assertThat(records.stream()
                .filter(record -> Duration.between(record.startTime(), record.endTime())
                        .compareTo(Duration.ofHours(5)) > 0)
                .count())
                .isEqualTo(21);
        assertThat(records.stream().map(LegacyImportRecord::organizerName).collect(Collectors.toSet()))
                .hasSize(32);
        assertThat(records.stream().map(LegacyImportRecord::roomName).collect(Collectors.toSet()))
                .hasSize(3);
        assertThat(records.stream().map(LegacyImportRecord::bookingNo).collect(Collectors.toSet()))
                .hasSize(842);
    }
}
