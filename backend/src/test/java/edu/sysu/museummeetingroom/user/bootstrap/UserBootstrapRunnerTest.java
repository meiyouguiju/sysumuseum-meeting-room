package edu.sysu.museummeetingroom.user.bootstrap;

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

class UserBootstrapRunnerTest {

    @Test
    void closesContextOnlyAfterBootstrapServiceReturns() throws Exception {
        ConfigurableApplicationContext applicationContext = Mockito.mock(ConfigurableApplicationContext.class);
        UserBootstrapFileParser userBootstrapFileParser = Mockito.mock(UserBootstrapFileParser.class);
        UserBootstrapService userBootstrapService = Mockito.mock(UserBootstrapService.class);
        UserBootstrapRunner runner = new UserBootstrapRunner(
                applicationContext,
                userBootstrapFileParser,
                userBootstrapService);
        ReflectionTestUtils.setField(runner, "bootstrapFile", "accounts.txt");
        List<UserBootstrapAccount> accounts = List.of(new UserBootstrapAccount("测试用户", "USER", "0123", 1));
        when(userBootstrapFileParser.parse(Path.of("accounts.txt"))).thenReturn(accounts);

        runner.run(new DefaultApplicationArguments());

        InOrder inOrder = inOrder(userBootstrapFileParser, userBootstrapService, applicationContext);
        inOrder.verify(userBootstrapFileParser).parse(Path.of("accounts.txt"));
        inOrder.verify(userBootstrapService).bootstrap(accounts);
        inOrder.verify(applicationContext).close();
    }
}
