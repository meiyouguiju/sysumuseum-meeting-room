package edu.sysu.museummeetingroom.admin.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = "local.current-user-id=989001")
class AdminRoomManagementIntegrationTest {

    private static final long ADMIN_ID = 989001L;
    private static final long OWNER_ID = 989002L;
    private static final long ROOM_A = 989010L;
    private static final long ROOM_B = 989011L;
    private static final long BOOKING_ID = 989101L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AdminRoomManagementIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        removeFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user(id, auth_provider, external_subject, login_name, display_name, role_code, status)
                VALUES (989001, 'TEST', 'room-admin', 'room-admin', '会议室管理员', 'ADMIN', 'ACTIVE'),
                       (989002, 'TEST', 'room-owner', 'room-owner', '预约人', 'USER', 'ACTIVE')
                """);
        jdbcTemplate.update("""
                INSERT INTO meeting_room(id, name, location, capacity, facilities_text, usage_notice, status, sort_order)
                VALUES (989010, 'D5会议室A', '一层', 10, '屏幕', '须知A', 'ENABLED', 20),
                       (989011, 'D5会议室B', '二层', 20, NULL, NULL, 'DISABLED', 30)
                """);
    }

    @AfterEach
    void tearDown() {
        removeFixture();
    }

    @Test
    void adminCreatesNormalizedEnabledRoomWithDefaultSortOrder() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  新会议室  ","location":"  三层  ","capacity":30,
                                 "facilitiesText":"  智慧屏  ","usageNotice":"   "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("新会议室"))
                .andExpect(jsonPath("$.location").value("三层"))
                .andExpect(jsonPath("$.facilitiesText").value("智慧屏"))
                .andExpect(jsonPath("$.usageNotice").isEmpty())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.sortOrder").value(0));
    }

    @Test
    void rejectsInvalidCreateFieldsAndFrozenLengths() throws Exception {
        assertInvalidCreate("{\"name\":\"   \",\"location\":\"位置\",\"capacity\":1}");
        assertInvalidCreate("{\"name\":\"名称\",\"location\":\"位置\",\"capacity\":0}");
        assertInvalidCreate("{\"name\":\"" + "名".repeat(121)
                + "\",\"location\":\"位置\",\"capacity\":1}");
        assertInvalidCreate("{\"name\":\"名称\",\"location\":\"" + "位".repeat(201)
                + "\",\"capacity\":1}");
    }

    @Test
    void duplicateNameConflictsOnCreateAndRenameWithoutPartialUpdate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"D5会议室A\",\"location\":\"三层\",\"capacity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEETING_ROOM_NAME_CONFLICT"));
        mockMvc.perform(patch("/api/v1/admin/rooms/{roomId}", ROOM_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"D5会议室A\",\"capacity\":99}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MEETING_ROOM_NAME_CONFLICT"));
        assertThat(value("SELECT capacity FROM meeting_room WHERE id = ?", ROOM_B)).isEqualTo("20");
    }

    @Test
    void partialUpdateNormalizesFieldsClearsOptionalTextAndChangesReadOrdering() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/rooms/{roomId}", ROOM_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  D5会议室A新名  ","facilitiesText":"  ","usageNotice":null,"sortOrder":-10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("D5会议室A新名"))
                .andExpect(jsonPath("$.facilitiesText").isEmpty())
                .andExpect(jsonPath("$.usageNotice").isEmpty())
                .andExpect(jsonPath("$.sortOrder").value(-10));
        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ROOM_A));
    }

    @Test
    void enableDisableAreRepeatableAndDisabledRoomRemainsVisible() throws Exception {
        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/disable", ROOM_A))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/disable", ROOM_A))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 989010)].status").value("DISABLED"));
        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/enable", ROOM_A))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ENABLED"));
    }

    @Test
    void disablingRoomDoesNotChangeExistingBookingOrSlotsAndDetailRemainsReadable() throws Exception {
        insertFutureBooking();
        mockMvc.perform(post("/api/v1/admin/rooms/{roomId}/disable", ROOM_A))
                .andExpect(status().isOk());
        assertThat(value("SELECT CONCAT(status, ':', version, ':', room_id) FROM booking WHERE id = ?", BOOKING_ID))
                .isEqualTo("ACTIVE:1:" + ROOM_A);
        assertThat(value("SELECT COUNT(*) FROM booking_slot WHERE booking_id = ?", BOOKING_ID)).isEqualTo("2");
        mockMvc.perform(get("/api/v1/bookings/{bookingId}", BOOKING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.id").value(ROOM_A));
    }

    @Test
    void missingRoomAndEmptyPatchReturnFrozenErrors() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/rooms/999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"capacity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEETING_ROOM_NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/admin/rooms/{roomId}", ROOM_A)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    private void assertInvalidCreate(String body) throws Exception {
        mockMvc.perform(post("/api/v1/admin/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_VALIDATION_ERROR"));
    }

    private void insertFutureBooking() {
        jdbcTemplate.update("""
                INSERT INTO booking(id, booking_no, room_id, organizer_user_id, organizer_name_snapshot,
                    subject, start_time, end_time, status, version)
                VALUES (989101, 'D5-989101', 989010, 989002, '预约人', '停用保留预约',
                    '2026-08-25 10:00:00', '2026-08-25 11:00:00', 'ACTIVE', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO booking_slot(booking_id, room_id, slot_start, occupancy_state)
                VALUES (989101, 989010, '2026-08-25 10:00:00', 'ACTIVE'),
                       (989101, 989010, '2026-08-25 10:30:00', 'ACTIVE')
                """);
    }

    private String value(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, String.class, arguments);
    }

    private void removeFixture() {
        jdbcTemplate.update("DELETE FROM booking_audit_log WHERE booking_id = ?", BOOKING_ID);
        jdbcTemplate.update("DELETE FROM booking_slot WHERE booking_id = ?", BOOKING_ID);
        jdbcTemplate.update("DELETE FROM booking WHERE id = ?", BOOKING_ID);
        jdbcTemplate.update("DELETE FROM meeting_room WHERE id IN (?, ?)", ROOM_A, ROOM_B);
        jdbcTemplate.update("DELETE FROM meeting_room WHERE name = '新会议室'");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", ADMIN_ID, OWNER_ID);
    }
}
