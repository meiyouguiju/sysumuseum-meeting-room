package edu.sysu.museummeetingroom.booking.idempotency;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdempotencyRecordMapper {
    @Insert("""
            INSERT INTO idempotency_record (
                operation_type, user_id, idempotency_key, request_hash, processing_status,
                processing_started_at, created_at, expires_at, updated_at
            ) VALUES (
                'CREATE_BOOKING', #{userId}, #{idempotencyKey}, #{requestHash}, 'PROCESSING',
                #{now}, #{now}, #{expiresAt}, #{now}
            )
            """)
    int insert(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestHash") byte[] requestHash,
            @Param("now") LocalDateTime now,
            @Param("expiresAt") LocalDateTime expiresAt);

    @Select("""
            SELECT id, operation_type, user_id, idempotency_key, request_hash, processing_status,
                   booking_id, response_http_status, response_body, failure_code,
                   processing_started_at, completed_at, created_at, expires_at, updated_at
            FROM idempotency_record
            WHERE operation_type = 'CREATE_BOOKING'
              AND user_id = #{userId}
              AND idempotency_key = #{idempotencyKey}
            """)
    IdempotencyRecord find(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT id, operation_type, user_id, idempotency_key, request_hash, processing_status,
                   booking_id, response_http_status, response_body, failure_code,
                   processing_started_at, completed_at, created_at, expires_at, updated_at
            FROM idempotency_record
            WHERE operation_type = 'CREATE_BOOKING'
              AND user_id = #{userId}
              AND idempotency_key = #{idempotencyKey}
            FOR UPDATE
            """)
    IdempotencyRecord lock(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE idempotency_record
            SET processing_status = 'SUCCEEDED',
                booking_id = #{bookingId},
                response_http_status = 201,
                response_body = CAST(#{responseBody} AS JSON),
                failure_code = NULL,
                completed_at = #{now},
                updated_at = #{now}
            WHERE id = #{id}
              AND processing_status = 'PROCESSING'
            """)
    int succeed(
            @Param("id") Long id,
            @Param("bookingId") Long bookingId,
            @Param("responseBody") String responseBody,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE idempotency_record
            SET processing_status = 'FAILED',
                booking_id = NULL,
                response_http_status = #{responseHttpStatus},
                response_body = CAST(#{responseBody} AS JSON),
                failure_code = #{failureCode},
                completed_at = #{now},
                updated_at = #{now}
            WHERE id = #{id}
              AND processing_status = 'PROCESSING'
            """)
    int fail(
            @Param("id") Long id,
            @Param("responseHttpStatus") int responseHttpStatus,
            @Param("failureCode") String failureCode,
            @Param("responseBody") String responseBody,
            @Param("now") LocalDateTime now);
}
