package edu.sysu.museummeetingroom.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import edu.sysu.museummeetingroom.booking.idempotency.CreateBookingRequestHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=985001")
@Import(BookingHttpIntegrationTest.FixedClockConfiguration.class)
class BookingHttpIntegrationTest {

    private static final long USER_ID = 985001L;
    private static final long OTHER_USER_ID = 985002L;
    private static final long ROOM_ID = 995001L;
    private static final long OTHER_ROOM_ID = 995002L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    BookingHttpIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (985001, 'TEST', 'http-user', 'http-user', 'HTTP测试用户', 'USER', 'ACTIVE'),
                       (985002, 'TEST', 'other-http-user', 'other-http-user', '其他HTTP测试用户', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, status, sort_order)
                VALUES (995001, 'HTTP测试会议室', '测试地点', 10, 'ENABLED', 1),
                       (995002, '第二HTTP测试会议室', '测试地点', 10, 'ENABLED', 2)
                """);
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void createsAndReplaysAStableBookingWithCurrentRequestIds() throws Exception {
        MvcResult first = postBooking("http-success", "request-one", requestBody("部门例会", "张三\n李四", "说明"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", "request-one"))
                .andExpect(jsonPath("$.subject").value("部门例会"))
                .andReturn();
        MvcResult replay = postBooking("http-success", "request-two", requestBody("部门例会", "张三\n李四", "说明"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", "request-two"))
                .andReturn();

        assertThat(json(first).get("id")).isEqualTo(json(replay).get("id"));
        assertThat(json(first).get("bookingNo")).isEqualTo(json(replay).get("bookingNo"));
        assertThat(json(first).get("createdAt")).isEqualTo(json(replay).get("createdAt"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking WHERE room_id = ?", Integer.class, ROOM_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_slot WHERE room_id = ?", Integer.class, ROOM_ID)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking_audit_log", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT response_body LIKE '%requestId%' FROM idempotency_record WHERE idempotency_key = 'http-success'", Boolean.class)).isFalse();
    }

    @Test
    void normalizesTextBeforeHashingAndReplaysTheFirstBooking() throws Exception {
        postBooking("http-normalized", "normalize-one", requestBody("  部门例会  ", " 张三\n李四 ", " 说明 "))
                .andExpect(status().isCreated());

        postBooking("http-normalized", "normalize-two", requestBody("部门例会", "张三\n李四", "说明"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subject").value("部门例会"));

        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM booking WHERE room_id = ?", String.class, ROOM_ID)).isEqualTo("部门例会");
        assertThat(jdbcTemplate.queryForObject("SELECT participants_text FROM booking WHERE room_id = ?", String.class, ROOM_ID)).isEqualTo("张三\n李四");
        assertThat(jdbcTemplate.queryForObject("SELECT description FROM booking WHERE room_id = ?", String.class, ROOM_ID)).isEqualTo("说明");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM booking WHERE room_id = ?", Integer.class, ROOM_ID)).isEqualTo(1);
    }

    @Test
    void mapsKeyReuseProcessingAndDeterministicFailureToHttpResponses() throws Exception {
        postBooking("key-reused", "reuse-one", requestBodyAt("首次内容", null, null, "13:00:00", "14:00:00"))
                .andExpect(status().isCreated());
        postBooking("key-reused", "reuse-two", requestBodyAt("不同内容", null, null, "13:00:00", "14:00:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));

        insertProcessingRecord(USER_ID, "key-processing", false, requestHashFor("处理中", "14:00:00", "15:00:00"));
        postBooking("key-processing", "processing", requestBodyAt("处理中", null, null, "14:00:00", "15:00:00"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_PROCESSING"));

        postBooking("key-occupied", "occupied", requestBody("已占用", null, null)).andExpect(status().isCreated());
        MvcResult firstFailure = postBooking("key-conflict", "failure-one", requestBody("冲突", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_SLOT_CONFLICT"))
                .andExpect(jsonPath("$.requestId").value("failure-one"))
                .andReturn();
        postBooking("key-conflict", "failure-two", requestBody("冲突", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_SLOT_CONFLICT"))
                .andExpect(jsonPath("$.requestId").value("failure-two"));

        assertThat(jdbcTemplate.queryForObject("SELECT processing_status FROM idempotency_record WHERE idempotency_key = 'key-conflict'", String.class)).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT response_body LIKE '%failure-one%' FROM idempotency_record WHERE idempotency_key = 'key-conflict'", Boolean.class)).isFalse();
        assertThat(json(firstFailure).get("requestId").asText()).isEqualTo("failure-one");
    }

    @Test
    void rejectsProtocolAndValidationErrorsBeforeClaimingAKey() throws Exception {
        postBooking(null, "missing-key", requestBody("会议", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REQUIRED"));
        postBooking("invalid key", "invalid-key", requestBody("会议", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_INVALID"));
        postBooking("bad-json", "bad-json", "{not-json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_BODY_INVALID"));
        postBooking("unknown-field", "unknown-field", requestBody("会议", null, null).replace("}", ",\"organizerUserId\":123}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_BODY_INVALID"));
        postBooking("blank-subject", "blank-subject", requestBody("   ", null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        postBooking("missing-room", "missing-room", "{\"subject\":\"会议\",\"startTime\":\"2026-08-22T11:00:00\",\"endTime\":\"2026-08-22T12:00:00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
        postBooking("bad-time", "bad-time", requestBody("会议", null, null).replace("2026-08-22T11:00:00", "not-a-time"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_BODY_INVALID"));
        postBooking("bad-count", "bad-count", requestBody("会议", null, null).replace("\"attendeeCount\":2", "\"attendeeCount\":-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE user_id = ?", Integer.class, USER_ID)).isZero();
    }

    @Test
    void acceptsParticipantsTextAtItsBusinessLengthLimitAndRejectsOneCharacterMore() throws Exception {
        postBooking("participants-at-limit", "participants-at-limit", requestBody("人员边界", "p".repeat(2000), null))
                .andExpect(status().isCreated());
        postBooking("participants-over-limit", "participants-over-limit", requestBodyAt("人员超限", "p".repeat(2001), null, "13:00:00", "14:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'participantsText')]").exists());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = 'participants-over-limit'", Integer.class)).isZero();
    }

    @Test
    void acceptsDescriptionAtItsBusinessLengthLimitAndRejectsOneCharacterMore() throws Exception {
        postBooking("description-at-limit", "description-at-limit", requestBody("说明边界", null, "d".repeat(4000)))
                .andExpect(status().isCreated());
        postBooking("description-over-limit", "description-over-limit", requestBodyAt("说明超限", null, "d".repeat(4001), "13:00:00", "14:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'description')]").exists());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = 'description-over-limit'", Integer.class)).isZero();
    }

    @Test
    void returnsTheCurrentUsersUnexpiredIdempotencyResultOnly() throws Exception {
        postBooking("result-success", "result-success-request", requestBody("查询成功", null, null)).andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/bookings/idempotency-result").header("Idempotency-Key", "result-success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.originalHttpStatus").value(201))
                .andExpect(jsonPath("$.response.subject").value("查询成功"));

        postBooking("result-failed", "result-failed-request", requestBody("查询失败", null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BOOKING_SLOT_CONFLICT"));
        mockMvc.perform(get("/api/v1/bookings/idempotency-result").header("Idempotency-Key", "result-failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.originalHttpStatus").value(409))
                .andExpect(jsonPath("$.failureCode").value("BOOKING_SLOT_CONFLICT"))
                .andExpect(jsonPath("$.response.errorCode").value("BOOKING_SLOT_CONFLICT"));

        insertProcessingRecord(USER_ID, "result-processing", false, new byte[32]);
        mockMvc.perform(get("/api/v1/bookings/idempotency-result").header("Idempotency-Key", "result-processing"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.response").doesNotExist());

        insertProcessingRecord(USER_ID, "result-expired", true, new byte[32]);
        mockMvc.perform(get("/api/v1/bookings/idempotency-result").header("Idempotency-Key", "result-expired"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_RESULT_NOT_FOUND_OR_EXPIRED"));
        insertProcessingRecord(OTHER_USER_ID, "other-user-key", false, new byte[32]);
        mockMvc.perform(get("/api/v1/bookings/idempotency-result").header("Idempotency-Key", "other-user-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_RESULT_NOT_FOUND_OR_EXPIRED"));
    }

    private org.springframework.test.web.servlet.ResultActions postBooking(
            String idempotencyKey,
            String requestId,
            String body) throws Exception {
        var request = post("/api/v1/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", requestId)
                .content(body);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private String requestBody(String subject, String participantsText, String description) {
        return requestBodyAt(subject, participantsText, description, "11:00:00", "12:00:00");
    }

    private String requestBodyAt(
            String subject,
            String participantsText,
            String description,
            String startTime,
            String endTime) {
        return """
                {"roomId":995001,"subject":"%s","startTime":"2026-08-22T%s","endTime":"2026-08-22T%s","attendeeCount":2,"participantsText":%s,"description":%s}
                """.formatted(subject, startTime, endTime, jsonString(participantsText), jsonString(description));
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return '"' + value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"") + '"';
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private byte[] requestHashFor(String subject, String startTime, String endTime) {
        CreateBookingCommand command = new CreateBookingCommand(
                ROOM_ID,
                subject,
                java.time.LocalDateTime.parse("2026-08-22T" + startTime),
                java.time.LocalDateTime.parse("2026-08-22T" + endTime),
                2,
                null,
                null);
        return new CreateBookingRequestHasher(objectMapper).hash(command);
    }

    private void insertProcessingRecord(long userId, String idempotencyKey, boolean expired, byte[] requestHash) {
        String expiresAt = expired ? "2026-08-22 10:14:59" : "2026-08-23 10:15:00";
        jdbcTemplate.update("""
                INSERT INTO idempotency_record(
                    operation_type, user_id, idempotency_key, request_hash, processing_status,
                    processing_started_at, created_at, expires_at, updated_at
                ) VALUES ('CREATE_BOOKING', ?, ?, ?, 'PROCESSING',
                    '2026-08-22 10:15:00', '2026-08-22 10:15:00', ?, '2026-08-22 10:15:00')
                """, userId, idempotencyKey, requestHash, expiresAt);
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM idempotency_record WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id IN (SELECT id FROM booking WHERE room_id IN (?, ?))", ROOM_ID, OTHER_ROOM_ID);
        jdbcTemplate.update("DELETE FROM booking_slot WHERE room_id IN (?, ?)", ROOM_ID, OTHER_ROOM_ID);
        jdbcTemplate.update("DELETE FROM booking WHERE room_id IN (?, ?)", ROOM_ID, OTHER_ROOM_ID);
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id IN (?, ?)", ROOM_ID, OTHER_ROOM_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID);
    }

    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(Instant.parse("2026-08-22T02:15:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
