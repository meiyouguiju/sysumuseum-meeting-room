package edu.sysu.museummeetingroom.user.service;

import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

class UserPinMaintenanceRunnerTest {

    @Test
    void closesContextOnlyAfterMaintenanceServiceReturns() throws Exception {
        ConfigurableApplicationContext applicationContext = Mockito.mock(ConfigurableApplicationContext.class);
        UserPinMaintenanceService userPinMaintenanceService = Mockito.mock(UserPinMaintenanceService.class);
        UserPinMaintenanceRunner runner = new UserPinMaintenanceRunner(applicationContext, userPinMaintenanceService);
        ReflectionTestUtils.setField(runner, "action", "create");
        ReflectionTestUtils.setField(runner, "name", "测试用户");
        ReflectionTestUtils.setField(runner, "pin", "0376");
        ReflectionTestUtils.setField(runner, "userId", 0L);
        ReflectionTestUtils.setField(runner, "roleCode", "USER");

        runner.run(new DefaultApplicationArguments());

        InOrder inOrder = inOrder(userPinMaintenanceService, applicationContext);
        inOrder.verify(userPinMaintenanceService).execute(
                new UserPinMaintenanceCommand("create", "测试用户", "0376", 0L, "USER"));
        inOrder.verify(applicationContext).close();
    }
}
