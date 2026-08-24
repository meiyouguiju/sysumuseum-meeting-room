package edu.sysu.museummeetingroom.booking.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BookingAuditLogMapper {

    @Insert("""
            INSERT INTO booking_audit_log (
                booking_id, operation_type, actor_user_id, actor_role_snapshot, target_owner_user_id,
                version_before, version_after, before_json, after_json, slot_change_json, occurred_at
            ) VALUES (
                #{bookingId}, 'CREATE', #{actorUserId}, #{actorRoleSnapshot}, #{targetOwnerUserId},
                NULL, #{versionAfter}, NULL, CAST(#{afterJson} AS JSON), CAST(#{slotChangeJson} AS JSON), #{occurredAt}
            )
            """)
    int insertCreateAudit(BookingAuditLogEntity auditLog);

    @Insert("""
            INSERT INTO booking_audit_log (
                booking_id, operation_type, actor_user_id, actor_role_snapshot, target_owner_user_id,
                version_before, version_after, before_json, after_json, slot_change_json, occurred_at
            ) VALUES (
                #{bookingId}, 'UPDATE', #{actorUserId}, #{actorRoleSnapshot}, #{targetOwnerUserId},
                #{versionBefore}, #{versionAfter}, CAST(#{beforeJson} AS JSON),
                CAST(#{afterJson} AS JSON), CAST(#{slotChangeJson} AS JSON), #{occurredAt}
            )
            """)
    int insertUpdateAudit(BookingUpdateAuditLogEntity auditLog);

    @Insert("""
            INSERT INTO booking_audit_log (booking_id,operation_type,actor_user_id,actor_role_snapshot,target_owner_user_id,version_before,version_after,before_json,after_json,slot_change_json,occurred_at)
            VALUES (#{bookingId},'CANCEL',#{actorUserId},#{actorRoleSnapshot},#{targetOwnerUserId},#{versionBefore},#{versionAfter},CAST(#{beforeJson} AS JSON),CAST(#{afterJson} AS JSON),CAST(#{slotChangeJson} AS JSON),#{occurredAt})
            """)
    int insertCancelAudit(BookingUpdateAuditLogEntity auditLog);
}
