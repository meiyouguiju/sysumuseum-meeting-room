package edu.sysu.museummeetingroom.legacyimport;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LegacyImportMapper {

    @Select("SELECT COUNT(*) FROM booking")
    int countBookings();

    @Select("SELECT id, display_name FROM sys_user ORDER BY id")
    List<LegacyImportUserRow> findAllUsers();

    @Select("SELECT id, name FROM meeting_room ORDER BY id")
    List<LegacyImportRoomRow> findAllRooms();

    @Insert("""
            INSERT INTO sys_user(auth_provider, external_subject, login_name, display_name, pin_hash, role_code, status)
            VALUES ('PIN_TRIAL', #{externalSubject}, #{loginName}, '历史数据导入系统', NULL, 'ADMIN', 'DISABLED')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTechnicalUser(LegacyImportTechnicalUser technicalUser);

    @Insert("""
            INSERT INTO booking (
                booking_no, room_id, organizer_user_id, organizer_name_snapshot, subject,
                attendee_count, participants_text, description, start_time, end_time, status,
                cancelled_at, cancelled_by_user_id, cancel_reason,
                version, last_modified_at, last_modified_by_user_id, created_at, updated_at
            ) VALUES (
                #{bookingNo}, #{roomId}, #{organizerUserId}, #{organizerNameSnapshot}, #{subject},
                #{attendeeCount}, #{participantsText}, #{description}, #{startTime}, #{endTime}, #{status},
                NULL, NULL, NULL,
                1, #{occurredAt}, #{lastModifiedByUserId}, #{occurredAt}, #{occurredAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBooking(LegacyImportBooking booking);
}
