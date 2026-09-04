package edu.sysu.museummeetingroom.legacyimport;

import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("legacy-import")
@ConditionalOnProperty(name = "legacy-import.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LegacyImportRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext applicationContext;
    private final LegacyImportWorkbookParser legacyImportWorkbookParser;
    private final LegacyImportService legacyImportService;

    @Value("${legacy-import.file}")
    private String importFile;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<LegacyImportRecord> records = legacyImportWorkbookParser.parse(Path.of(importFile));
            legacyImportService.importRecords(records);
        } finally {
            applicationContext.close();
        }
    }
}
