package edu.sysu.museummeetingroom.legacyimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogEntity;
import edu.sysu.museummeetingroom.booking.mapper.BookingAuditLogMapper;
import edu.sysu.museummeetingroom.bootstrap.SchemaVerificationRunner;
import edu.sysu.museummeetingroom.maintenance.MaintenanceScheduler;
import edu.sysu.museummeetingroom.maintenance.MaintenanceSchedulingConfiguration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("legacy-import")
@TestPropertySource(properties = "legacy-import.enabled=false")
class LegacyImportServiceIntegrationTest {

    private static final long TECHNICAL_USER_ID = 990001L;

    private final LegacyImportService legacyImportService;
    private final ApplicationContext applicationContext;

    @MockBean
    private LegacyImportMapper legacyImportMapper;

    @MockBean
    private BookingAuditLogMapper bookingAuditLogMapper;

    @Autowired
    LegacyImportServiceIntegrationTest(
            LegacyImportService legacyImportService,
            ApplicationContext applicationContext) {
        this.legacyImportService = legacyImportService;
        this.applicationContext = applicationContext;
    }

    @BeforeEach
    void setUp() {
        when(legacyImportMapper.countBookings()).thenReturn(0);
        when(legacyImportMapper.findAllUsers()).thenReturn(List.of(
                new LegacyImportUserRow(801L, "历史预订人A"),
                new LegacyImportUserRow(802L, "历史预订人B")));
        when(legacyImportMapper.findAllRooms()).thenReturn(List.of(
                new LegacyImportRoomRow(901L, "历史会议室A")));
        doAnswer(invocation -> {
            LegacyImportTechnicalUser technicalUser = invocation.getArgument(0);
            technicalUser.setId(TECHNICAL_USER_ID);
            return 1;
        }).when(legacyImportMapper).insertTechnicalUser(any(LegacyImportTechnicalUser.class));
    }

    @Test
    void importsEightHundredFortyTwoBookingsAndCreateAuditsWithoutSlotsOrIdempotency() throws Exception {
        List<LegacyImportBooking> bookings = new ArrayList<>();
        List<BookingAuditLogEntity> audits = new ArrayList<>();
        AtomicLong bookingId = new AtomicLong(1000L);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            LegacyImportBooking booking = invocation.getArgument(0);
            booking.setId(bookingId.getAndIncrement());
            bookings.add(booking);
            return 1;
        }).when(legacyImportMapper).insertBooking(any(LegacyImportBooking.class));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            audits.add(invocation.getArgument(0));
            return 1;
        }).when(bookingAuditLogMapper).insertCreateAudit(any(BookingAuditLogEntity.class));

        legacyImportService.importRecords(records());

        assertThat(bookings).hasSize(842);
        assertThat(bookings).filteredOn(booking -> "ACTIVE".equals(booking.getStatus())).hasSize(840);
        assertThat(bookings).filteredOn(booking -> "CANCELLED".equals(booking.getStatus())).hasSize(2);
        assertThat(bookings)
                .filteredOn(booking -> "CANCELLED".equals(booking.getStatus()))
                .allSatisfy(booking -> {
                    assertThat(booking.getLastModifiedByUserId()).isEqualTo(TECHNICAL_USER_ID);
                    assertThat(booking.getStartTime()).isBefore(booking.getEndTime());
                });
        assertThat(bookings)
                .anySatisfy(booking -> assertThat(booking.getStartTime().toLocalTime().toString()).isEqualTo("14:20"));
        assertThat(bookings)
                .anySatisfy(booking -> assertThat(booking.getEndTime().minusHours(6)).isAfter(booking.getStartTime()));

        assertThat(audits).hasSize(842);
        assertThat(audits).allSatisfy(audit -> {
            assertThat(audit.actorUserId()).isEqualTo(TECHNICAL_USER_ID);
            assertThat(audit.actorRoleSnapshot()).isEqualTo("ADMIN");
            assertThat(audit.versionAfter()).isEqualTo(1);
            assertThat(audit.slotChangeJson()).isNull();
            assertThat(audit.afterJson()).isNotBlank();
        });
        assertCreateAuditSnapshotContract(audits.getFirst().afterJson());
        verify(legacyImportMapper).insertTechnicalUser(any(LegacyImportTechnicalUser.class));
    }

    private void assertCreateAuditSnapshotContract(String afterJson) throws Exception {
        JsonNode snapshot = new ObjectMapper().readTree(afterJson);
        Set<String> fieldNames = new LinkedHashSet<>();
        snapshot.fieldNames().forEachRemaining(fieldNames::add);

        assertThat(fieldNames).containsExactly(
                "id",
                "bookingNo",
                "roomId",
                "organizerUserId",
                "organizerNameSnapshot",
                "subject",
                "attendeeCount",
                "participantsText",
                "description",
                "startTime",
                "endTime",
                "status",
                "version",
                "createdAt");
        assertThat(snapshot.has("organizerName")).isFalse();
        assertThat(snapshot.has("cancelledAt")).isFalse();
        assertThat(snapshot.has("cancelledByUserId")).isFalse();
        assertThat(snapshot.has("cancelReason")).isFalse();
        assertThat(snapshot.has("updatedAt")).isFalse();
        assertThat(snapshot.has("lastModifiedAt")).isFalse();
    }

    @Test
    void rejectsNonEmptyBookingTableWithoutCreatingTechnicalUserOrBookings() {
        when(legacyImportMapper.countBookings()).thenReturn(1);

        assertThatThrownBy(() -> legacyImportService.importRecords(records()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("legacy-import 只能在空 booking 表上执行。");

        verify(legacyImportMapper, never()).insertTechnicalUser(any(LegacyImportTechnicalUser.class));
        verify(legacyImportMapper, never()).insertBooking(any(LegacyImportBooking.class));
        verify(bookingAuditLogMapper, never()).insertCreateAudit(any(BookingAuditLogEntity.class));
    }

    @Test
    void rejectsMissingOrDuplicateMappingsBeforeAnyWrite() {
        when(legacyImportMapper.findAllUsers()).thenReturn(List.of(
                new LegacyImportUserRow(801L, "历史预订人A"),
                new LegacyImportUserRow(802L, "历史预订人A")));

        assertThatThrownBy(() -> legacyImportService.importRecords(records()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("sys_user 存在重复 display_name，无法执行 legacy-import。");

        verify(legacyImportMapper, never()).insertTechnicalUser(any(LegacyImportTechnicalUser.class));
        verify(legacyImportMapper, never()).insertBooking(any(LegacyImportBooking.class));

        when(legacyImportMapper.findAllUsers()).thenReturn(List.of(new LegacyImportUserRow(801L, "历史预订人A")));
        assertThatThrownBy(() -> legacyImportService.importRecords(records()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("找不到预订人。");
    }

    @Test
    void propagatesBookingAndAuditInsertFailuresToTransactionalBoundary() {
        doThrow(new IllegalStateException("booking insert failure"))
                .when(legacyImportMapper)
                .insertBooking(any(LegacyImportBooking.class));

        assertThatThrownBy(() -> legacyImportService.importRecords(records()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("booking insert failure");

        doAnswer(invocation -> {
            LegacyImportBooking booking = invocation.getArgument(0);
            booking.setId(1000L);
            return 1;
        }).when(legacyImportMapper).insertBooking(any(LegacyImportBooking.class));
        doThrow(new IllegalStateException("audit insert failure"))
                .when(bookingAuditLogMapper)
                .insertCreateAudit(any(BookingAuditLogEntity.class));

        assertThatThrownBy(() -> legacyImportService.importRecords(records()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit insert failure");
    }

    @Test
    void legacyImportProfileExcludesNormalStartupRunnerAndScheduler() {
        assertThat(applicationContext.getBeansOfType(SchemaVerificationRunner.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(MaintenanceSchedulingConfiguration.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(MaintenanceScheduler.class)).isEmpty();
    }

    private List<LegacyImportRecord> records() {
        List<LegacyImportRecord> records = new ArrayList<>();
        for (int index = 1; index <= 842; index++) {
            boolean cancelled = index <= 2;
            LocalDateTime startTime = index == 3
                    ? LocalDateTime.of(2024, 1, 1, 14, 20)
                    : LocalDateTime.of(2024, 1, 1, 9, 0);
            LocalDateTime endTime = index == 3
                    ? LocalDateTime.of(2024, 1, 1, 14, 50)
                    : index == 4 ? LocalDateTime.of(2024, 1, 1, 16, 0) : startTime.plusHours(1);
            records.add(new LegacyImportRecord(
                    "测试Sheet",
                    index + 1,
                    "LEGACY-2024-%06d".formatted(index + 1),
                    "历史会议室A",
                    index % 2 == 0 ? "历史预订人A" : "历史预订人B",
                    "虚构历史会议" + index,
                    index == 5 ? null : index,
                    cancelled ? "CANCELLED" : "ACTIVE",
                    startTime,
                    endTime,
                    null,
                    null));
        }
        return records;
    }
}
