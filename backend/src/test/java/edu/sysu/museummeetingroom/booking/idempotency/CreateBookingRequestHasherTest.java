package edu.sysu.museummeetingroom.booking.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CreateBookingRequestHasherTest {

    private final CreateBookingRequestHasher hasher = new CreateBookingRequestHasher(new ObjectMapper());

    @Test
    void producesTheSameHashForTheSameNormalizedCommand() {
        CreateBookingCommand command = command(1L, "  校史馆会议  ", " 张三\n李四 ", "  说明  ", 5);

        assertThat(hasher.hash(command)).containsExactly(hasher.hash(command));
        assertThat(hasher.hash(command)).containsExactly(hasher.hash(command(1L, "校史馆会议", "张三\n李四", "说明", 5)));
    }

    @Test
    void treatsBlankOptionalTextAsNull() {
        byte[] nullHash = hasher.hash(command(1L, "会议", null, null, 5));

        assertThat(hasher.hash(command(1L, "会议", "", "", 5))).containsExactly(nullHash);
        assertThat(hasher.hash(command(1L, "会议", "   ", "\t", 5))).containsExactly(nullHash);
    }

    @Test
    void changesWhenInternalTextOrBusinessFieldsChange() {
        byte[] baseline = hasher.hash(command(1L, "会议", "张三 李四", "说明", 5));

        assertThat(hasher.hash(command(1L, "会议", "张三\n李四", "说明", 5))).isNotEqualTo(baseline);
        assertThat(hasher.hash(command(2L, "会议", "张三 李四", "说明", 5))).isNotEqualTo(baseline);
        assertThat(hasher.hash(commandWithTimes(at(11, 30), at(12, 30)))).isNotEqualTo(baseline);
        assertThat(hasher.hash(command(1L, "会议", "张三 李四", "说明", 6))).isNotEqualTo(baseline);
    }

    private CreateBookingCommand command(
            Long roomId,
            String subject,
            String participantsText,
            String description,
            Integer attendeeCount) {
        return new CreateBookingCommand(roomId, subject, at(11, 0), at(12, 0), attendeeCount, participantsText, description);
    }

    private CreateBookingCommand commandWithTimes(LocalDateTime startTime, LocalDateTime endTime) {
        return new CreateBookingCommand(1L, "会议", startTime, endTime, 5, "张三 李四", "说明");
    }

    private LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 8, 22, hour, minute);
    }
}
