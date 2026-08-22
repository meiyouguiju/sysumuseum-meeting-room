-- Meeting room booking system: initial MySQL 8 schema.
-- Flyway executes this against an already-created, empty target database.

CREATE TABLE `sys_user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `auth_provider` VARCHAR(32) NOT NULL DEFAULT 'SYSU_SSO' COMMENT '认证来源',
    `external_subject` VARCHAR(128) NOT NULL COMMENT '统一身份认证稳定标识',
    `login_name` VARCHAR(64) NOT NULL COMMENT '展示和排查账号，不作为唯一身份',
    `display_name` VARCHAR(100) NOT NULL COMMENT '当前姓名',
    `department_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '同步的部门名称',
    `role_code` VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '系统角色',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态',
    `last_synced_at` DATETIME(0) NULL DEFAULT NULL COMMENT '最近身份同步时间',
    `created_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建档时间',
    `updated_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_sys_user_auth_subject` UNIQUE (`auth_provider`, `external_subject`),
    CONSTRAINT `ck_sys_user_role_code` CHECK (`role_code` IN ('USER', 'ADMIN')),
    CONSTRAINT `ck_sys_user_status` CHECK (`status` IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一身份认证用户映射';

CREATE TABLE `meeting_room` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `name` VARCHAR(120) NOT NULL COMMENT '会议室名称',
    `location` VARCHAR(200) NOT NULL COMMENT '所在位置',
    `capacity` SMALLINT UNSIGNED NOT NULL COMMENT '会议室容量，仅用于警告',
    `facilities_text` TEXT NULL COMMENT '设备说明',
    `usage_notice` TEXT NULL COMMENT '使用须知',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '启用状态',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '日程展示排序',
    `created_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `created_by_user_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建管理员ID，逻辑关联sys_user',
    `updated_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `updated_by_user_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '修改管理员ID，逻辑关联sys_user',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_meeting_room_name` UNIQUE (`name`),
    KEY `idx_meeting_room_status_sort` (`status`, `sort_order`, `id`),
    CONSTRAINT `ck_meeting_room_status` CHECK (`status` IN ('ENABLED', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议室配置';

CREATE TABLE `booking` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `booking_no` VARCHAR(32) NOT NULL COMMENT '对外预约编号',
    `room_id` BIGINT UNSIGNED NOT NULL COMMENT '会议室ID，逻辑关联meeting_room',
    `organizer_user_id` BIGINT UNSIGNED NOT NULL COMMENT '预约人ID，逻辑关联sys_user',
    `organizer_name_snapshot` VARCHAR(100) NOT NULL COMMENT '创建时预约人姓名快照',
    `subject` VARCHAR(200) NOT NULL COMMENT '会议主题',
    `attendee_count` SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT '预计参会人数',
    `participants_text` TEXT NULL COMMENT '非结构化参会人员信息',
    `description` TEXT NULL COMMENT '会议说明',
    `start_time` DATETIME(0) NOT NULL COMMENT '预约开始时间，Asia/Shanghai业务时间',
    `end_time` DATETIME(0) NOT NULL COMMENT '预约结束时间，Asia/Shanghai业务时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '预约状态',
    `cancelled_at` DATETIME(0) NULL DEFAULT NULL COMMENT '取消生效时间',
    `cancelled_by_user_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '取消操作人ID，逻辑关联sys_user',
    `cancel_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '取消原因',
    `version` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    `last_modified_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改或取消时间',
    `last_modified_by_user_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最后操作人ID，逻辑关联sys_user',
    `created_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后持久化更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_booking_no` UNIQUE (`booking_no`),
    KEY `idx_booking_room_start` (`room_id`, `start_time`, `id`),
    KEY `idx_booking_organizer_start` (`organizer_user_id`, `start_time`, `id`),
    KEY `idx_booking_start_room` (`start_time`, `room_id`, `id`),
    KEY `idx_booking_status_start` (`status`, `start_time`, `id`),
    CONSTRAINT `ck_booking_status` CHECK (`status` IN ('ACTIVE', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议室预约主记录';

CREATE TABLE `booking_slot` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `booking_id` BIGINT UNSIGNED NOT NULL COMMENT '预约ID，逻辑关联booking',
    `room_id` BIGINT UNSIGNED NOT NULL COMMENT '会议室ID，逻辑关联meeting_room',
    `slot_start` DATETIME(0) NOT NULL COMMENT '30分钟占用槽开始时间',
    `occupancy_state` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '运行时占用状态',
    `created_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '槽创建时间',
    `updated_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '槽状态更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_booking_slot_room_start` UNIQUE (`room_id`, `slot_start`),
    CONSTRAINT `uk_booking_slot_booking_start` UNIQUE (`booking_id`, `slot_start`),
    KEY `idx_booking_slot_start_room_booking` (`slot_start`, `room_id`, `booking_id`),
    CONSTRAINT `ck_booking_slot_occupancy_state` CHECK (`occupancy_state` IN ('ACTIVE', 'CANCELLED_CURRENT_SLOT_HOLD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会议室运行时30分钟占用槽';

CREATE TABLE `booking_audit_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `booking_id` BIGINT UNSIGNED NOT NULL COMMENT '预约ID，逻辑关联booking',
    `operation_type` VARCHAR(24) NOT NULL COMMENT '审计操作类型',
    `actor_user_id` BIGINT UNSIGNED NOT NULL COMMENT '真实操作人ID，逻辑关联sys_user',
    `actor_role_snapshot` VARCHAR(16) NOT NULL COMMENT '操作时角色快照',
    `target_owner_user_id` BIGINT UNSIGNED NOT NULL COMMENT '预约归属人ID，逻辑关联sys_user',
    `reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '管理员操作他人预约的原因',
    `version_before` INT UNSIGNED NULL DEFAULT NULL COMMENT '操作前版本，创建时为空',
    `version_after` INT UNSIGNED NOT NULL COMMENT '操作后版本',
    `before_json` JSON NULL COMMENT '操作前完整预约快照',
    `after_json` JSON NOT NULL COMMENT '操作后完整预约快照',
    `slot_change_json` JSON NULL COMMENT '时间槽变更快照',
    `occurred_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_booking_audit_booking_occurred` (`booking_id`, `occurred_at`, `id`),
    KEY `idx_booking_audit_actor_occurred` (`actor_user_id`, `occurred_at`, `id`),
    KEY `idx_booking_audit_operation_occurred` (`operation_type`, `occurred_at`, `id`),
    CONSTRAINT `ck_booking_audit_operation_type` CHECK (`operation_type` IN ('CREATE', 'UPDATE', 'CANCEL')),
    CONSTRAINT `ck_booking_audit_actor_role` CHECK (`actor_role_snapshot` IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预约创建修改取消审计日志';

CREATE TABLE `idempotency_record` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    `operation_type` VARCHAR(32) NOT NULL DEFAULT 'CREATE_BOOKING' COMMENT '幂等操作类型',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '请求用户ID，逻辑关联sys_user',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '客户端幂等键',
    `request_hash` BINARY(32) NOT NULL COMMENT '规范化请求SHA-256哈希',
    `processing_status` VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '幂等处理状态',
    `booking_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '成功创建的预约ID，逻辑关联booking',
    `response_http_status` SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT '首次完成响应HTTP状态码',
    `response_body` JSON NULL COMMENT '首次完成响应体',
    `failure_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '稳定业务失败码',
    `processing_started_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取处理权时间',
    `completed_at` DATETIME(0) NULL DEFAULT NULL COMMENT '终态完成时间',
    `created_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `expires_at` DATETIME(0) NOT NULL COMMENT '幂等有效期截止时间',
    `updated_at` DATETIME(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间',
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_idempotency_operation_user_key` UNIQUE (`operation_type`, `user_id`, `idempotency_key`),
    KEY `idx_idempotency_expires_at` (`expires_at`),
    CONSTRAINT `ck_idempotency_operation_type` CHECK (`operation_type` IN ('CREATE_BOOKING')),
    CONSTRAINT `ck_idempotency_processing_status` CHECK (`processing_status` IN ('PROCESSING', 'SUCCEEDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='创建预约严格幂等记录';
