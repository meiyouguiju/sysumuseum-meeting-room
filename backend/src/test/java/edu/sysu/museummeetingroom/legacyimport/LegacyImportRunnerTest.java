package edu.sysu.museummeetingroom.legacyimport;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class LegacyImportRunnerTest {

    @Test
    void closesContextOnlyAfterLegacyImportServiceReturns() throws Exception {
        ConfigurableApplicationContext applicationContext = Mockito.mock(ConfigurableApplicationContext.class);
        LegacyImportWorkbookParser legacyImportWorkbookParser = Mockito.mock(LegacyImportWorkbookParser.class);
        LegacyImportService legacyImportService = Mockito.mock(LegacyImportService.class);
        LegacyImportRunner runner = new LegacyImportRunner(
                applicationContext,
                legacyImportWorkbookParser,
                legacyImportService);
        ReflectionTestUtils.setField(runner, "importFile", "history.xlsx");
        List<LegacyImportRecord> records = List.of();
        when(legacyImportWorkbookParser.parse(Path.of("history.xlsx"))).thenReturn(records);

        runner.run(new DefaultApplicationArguments());

        InOrder inOrder = inOrder(legacyImportWorkbookParser, legacyImportService, applicationContext);
        inOrder.verify(legacyImportWorkbookParser).parse(Path.of("history.xlsx"));
        inOrder.verify(legacyImportService).importRecords(records);
        inOrder.verify(applicationContext).close();
    }
}
