-- 仅限本地开发环境使用。
-- 禁止用于生产环境，也不由 Flyway 自动执行。
-- 可重复执行：仅写入或更新本脚本定义的本地开发用户和会议室。

INSERT INTO sys_user (
    id,
    auth_provider,
    external_subject,
    login_name,
    display_name,
    department_name,
    role_code,
    status,
    last_synced_at
)
VALUES
    (1, 'LOCAL_DEV', 'local-dev-user-a', 'frontend-user-a', '前端测试用户A', '本地开发测试部门', 'USER', 'ACTIVE', NOW()),
    (2, 'LOCAL_DEV', 'local-dev-user-b', 'frontend-user-b', '前端测试用户B', '本地开发测试部门', 'USER', 'ACTIVE', NOW()),
    (3, 'LOCAL_DEV', 'local-dev-admin', 'frontend-admin', '前端测试管理员', '本地开发测试部门', 'ADMIN', 'ACTIVE', NOW()),
    (4, 'LOCAL_DEV', 'local-dev-disabled-user', 'frontend-disabled-user', '前端测试停用用户', '本地开发测试部门', 'USER', 'DISABLED', NOW())
ON DUPLICATE KEY UPDATE
    auth_provider = VALUES(auth_provider),
    external_subject = VALUES(external_subject),
    login_name = VALUES(login_name),
    display_name = VALUES(display_name),
    department_name = VALUES(department_name),
    role_code = VALUES(role_code),
    status = VALUES(status),
    last_synced_at = VALUES(last_synced_at);

INSERT INTO meeting_room (
    name,
    location,
    capacity,
    facilities_text,
    usage_notice,
    status,
    sort_order,
    created_by_user_id,
    updated_by_user_id
)
VALUES
    ('博物馆2A会议室', '543栋2A层', 100, '电脑、智慧屏、麦克风', NULL, 'ENABLED', 10, 3, 3),
    ('515栋会议室', '515栋2层', 100, '电脑、麦克风', NULL, 'ENABLED', 20, 3, 3),
    ('博物馆学术交流空间（里间）', '543栋学术交流空间', 100, '电脑、智慧屏', NULL, 'ENABLED', 30, 3, 3)
ON DUPLICATE KEY UPDATE
    location = VALUES(location),
    capacity = VALUES(capacity),
    facilities_text = VALUES(facilities_text),
    usage_notice = VALUES(usage_notice),
    status = VALUES(status),
    sort_order = VALUES(sort_order),
    updated_by_user_id = VALUES(updated_by_user_id);
