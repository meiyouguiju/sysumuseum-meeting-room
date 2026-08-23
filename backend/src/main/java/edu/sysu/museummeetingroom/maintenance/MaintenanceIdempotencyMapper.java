package edu.sysu.museummeetingroom.maintenance;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MaintenanceIdempotencyMapper {

    @Select("""
            SELECT id, operation_type, user_id, processing_status, processing_started_at, expires_at
            FROM idempotency_record
            WHERE processing_status = 'PROCESSING'
              AND processing_started_at < #{recoveryCutoff}
            ORDER BY processing_started_at, id
            LIMIT #{batchSize}
            """)
    List<MaintenanceIdempotencyRecord> findStaleProcessingCandidates(
            @Param("recoveryCutoff") LocalDateTime recoveryCutoff,
            @Param("batchSize") int batchSize);

    @Select("""
            SELECT id, operation_type, user_id, processing_status, processing_started_at, expires_at
            FROM idempotency_record
            WHERE id = #{id}
            FOR UPDATE
            """)
    MaintenanceIdempotencyRecord lockById(@Param("id") Long id);

    @Update("""
            UPDATE idempotency_record
            SET processing_status = 'FAILED',
                booking_id = NULL,
                response_http_status = 500,
                response_body = CAST(#{responseBody} AS JSON),
                failure_code = 'INTERNAL_ERROR',
                completed_at = #{now},
                updated_at = #{now}
            WHERE id = #{id}
              AND processing_status = 'PROCESSING'
            """)
    int recoverAsInternalError(
            @Param("id") Long id,
            @Param("responseBody") String responseBody,
            @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM idempotency_record
            WHERE processing_status IN ('SUCCEEDED', 'FAILED')
              AND expires_at < #{now}
            ORDER BY expires_at, id
            LIMIT #{batchSize}
            """)
    int deleteExpiredTerminalRecords(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);
}
