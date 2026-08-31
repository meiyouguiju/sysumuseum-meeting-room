package edu.sysu.museummeetingroom.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.booking.idempotency.CreateBookingCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class BookingUnexpectedExceptionHttpTest {

    private final MockMvc mockMvc;

    @MockBean
    private CreateBookingCoordinator createBookingCoordinator;

    @Autowired
    BookingUnexpectedExceptionHttpTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void hidesInternalIllegalStateExceptionBehindInternalErrorResponse() throws Exception {
        when(createBookingCoordinator.create(any(CreateBookingCommand.class), anyString()))
                .thenThrow(new IllegalStateException("internal-state-corruption"));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "unexpected-error-key")
                        .header("X-Request-Id", "unexpected-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId":1,"subject":"测试会议","startTime":"2026-08-23T11:00:00","endTime":"2026-08-23T12:00:00"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "unexpected-request"))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.requestId").value("unexpected-request"))
                .andExpect(jsonPath("$.message").value("服务暂时无法处理请求。"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-state-corruption"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("IllegalStateException"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-state-corruption"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("IllegalStateException"))));
    }
}
