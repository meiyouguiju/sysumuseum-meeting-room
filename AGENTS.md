# Project

本项目为中山大学校史馆会议室预约系统。技术栈：Java 21、Spring Boot 3、Maven Wrapper、MyBatis-Plus、MySQL 8、Flyway；后续前端采用 Vue 3 与 TypeScript。

# Sources of truth

开发前优先阅读并遵守：

- `docs/会议室预约系统MVP业务规则确认文档-V1.0.md`
- `docs/会议室预约系统数据库设计-V0.2.md`
- `docs/会议室预约系统数据库物理设计-V0.1.md`（当前内容版本为 V0.1.1）
- `docs/会议室预约系统REST-API设计-V1.0.md`
- `docs/coding-standards.md`

发生冲突时，以已冻结的更新版本为准。不得自行改变已冻结业务规则。

# Java style and architecture

- 使用 4 空格缩进、一个语句一行、清晰空行和非通配符 import；禁止为缩短代码压缩构造器、方法体或控制语句。
- 遵循可读性优先的阿里巴巴 Java 开发手册思想；命名清晰，避免无意义缩写。
- Controller 只做 HTTP 适配并调用 Service；Service 承载业务规则和事务；Mapper 只负责访问数据库；DTO、Entity、Domain 对象不得随意混用。
- 保持模块化单体：`common`、`auth`、`user`、`room`、`schedule`、`booking`、`admin`；不得建立全局巨型 controller/service/mapper 目录。
- 业务代码只能通过 `CurrentUserProvider` 获取当前用户身份；Controller 不得直接访问 Mapper。

# Dependency injection and Lombok

- 禁止 `@Autowired` 字段注入，统一使用构造器注入和 `private final` 依赖。
- 仅含 final 依赖的 Spring 组件优先用 Lombok `@RequiredArgsConstructor`；有特殊构造逻辑时显式构造器。
- 可使用 `@Getter` 和真正需要时的 `@Builder`；禁止为求短而滥用 `@Data` 或隐藏重要业务行为。

# Database and security

- 已执行的 `backend/src/main/resources/db/migration/V1__init_schema.sql` 禁止修改；结构变化仅通过新的 V2、V3 等 Flyway 迁移。
- `booking_slot` 的 `UNIQUE(room_id, slot_start)` 是预约冲突的最终数据库保障。
- 禁止信任客户端提交的用户 ID、角色；禁止提交密码、Secret 或数据库凭据；禁止返回异常类、堆栈、内部数据库信息；日志不得记录不安全 Header 原文。

# Testing and handoff

- 正常开发和开发者本机验证必须优先运行 `.\mvnw.cmd test`；Codex 也应优先尝试 Maven Wrapper。失败必须先修复，不得作为完成状态。
- 完整集成测试以 Maven `test` 为准；Maven Surefire 仅在测试 JVM 中设置 `maintenance.scheduling.enabled=false`，避免真实 cron 任务执行。
- 若 Codex 自身执行环境无法启动 `mvnw.cmd`，不得擅自修改或重写 Wrapper；先记录并报告其 stdout、stderr 和 exit code。确需继续验证时，可临时调用该 Wrapper 已下载的同版本 Maven。此 fallback 仅处理 Codex 环境兼容性，不改变项目标准命令；开发者本机能正常执行 Wrapper 时，不得误判 Wrapper 损坏。
- 完成汇报须说明修改文件、测试结果、是否修改数据库、是否改变既有 REST 契约。
