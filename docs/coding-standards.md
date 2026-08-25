# 编码规范

本规范以阿里巴巴 Java 开发手册的可读性、边界清晰和可维护性原则为基础，适用于本项目的 Java 21 / Spring Boot 3 后端。

## 1. 命名与格式

- 类、接口、枚举使用 PascalCase；方法、变量、包使用 camelCase；常量使用 `UPPER_SNAKE_CASE`。
- 名称应表达业务含义；布尔值优先使用 `is`、`has`、`can` 等可读前缀，避免 `data`、`info`、`tmp` 等泛化名称。
- 使用 4 空格缩进，禁止 Tab。一个语句一行；左大括号同行、右大括号独立一行；成员、方法和逻辑段之间保留恰当空行。
- import 按 Java、第三方、项目内分组；不使用通配符或未使用 import。

## 2. 类、方法与分层

- 类和方法以单一职责为目标；复杂流程提取具名私有方法，不用过度拆分制造跳转成本。
- Controller 只解析 HTTP、调用 Service 和返回 DTO；不得直接使用 Mapper。
- Service 承担业务规则、权限检查和事务边界；Mapper 只表达数据库访问，不承载业务判断。
- DTO 是接口契约，不直接作为数据库持久化对象；Entity 对应表映射；需要复杂业务语义时单独定义 Domain 对象。

## 3. 注入、Lombok 与数据处理

- 禁止字段注入；依赖保持 `private final`，使用构造器注入。纯依赖构造器可使用 `@RequiredArgsConstructor`。
- Lombok 只用于消除样板：可用 `@RequiredArgsConstructor`、`@Getter`；`@Builder` 仅在确有可选组合构造需求时使用。复杂 Entity/Domain 不使用 `@Data`。
- `Optional` 主要用于返回值表达“可能不存在”，不用于字段、参数、集合或 JPA/MyBatis Entity 属性。
- 明确 null 语义。外部输入先校验；集合优先返回空集合，不返回 null；对可空数据库字段使用显式分支或清晰映射。

## 4. 异常、日志与时间

- 用稳定的业务错误码和统一异常处理返回客户端错误；不得暴露 Java 类型、SQL、堆栈或敏感信息。
- 日志包含排查所需上下文和 requestId，不记录密码、令牌、原始不安全 Header、参会人员等不必要敏感信息。
- 预约业务时区固定 `Asia/Shanghai`。API 和持久化预约时间使用 `LocalDateTime` / `DATETIME(0)`；只有跨系统瞬时事件才讨论 `Instant`。

## 5. MyBatis 与事务

- 简单单表 CRUD 可用 MyBatis-Plus；多表或日程类查询使用清晰的专用 Mapper SQL。
- 禁止在循环中逐房间、逐时间槽查询；先使用批量查询再聚合。
- 事务声明在 Service 层。写预约时须遵守既定的 booking、booking_slot、审计和幂等事务边界；不要在 rollback-only 事务中继续处理重复键异常。

## 6. 测试与注释

- 新业务规则必须有自动测试。时间相关测试使用可注入 `Clock`；集成测试 fixture 必须可重复执行且不保留正式业务数据。
- 完整集成测试以 Maven `test` 为准；Surefire 会在测试 JVM 中关闭 maintenance scheduling。IDE 直接运行单个测试不保证继承该 Surefire 系统属性。
- 测试名称描述行为与预期；断言包含权限、错误码、隐私字段和边界值。
- 注释解释“为什么”，不复述显而易见的代码。仅对公共 API、复杂约束或非直观决定写简短 JavaDoc。

## 7. 自动检查

- Maven 使用 Checkstyle 进行自动规范检查，并在 `validate` 阶段执行。

- 当前规则检查 Tab、通配符 import、未使用 import、`NeedBraces`、`OneStatementPerLine` 等机械性和基础规范问题。

- Checkstyle 不承担完整自动代码格式化；4 空格缩进、合理空行、方法可读性和不压缩代码等要求仍由本规范、`AGENTS.md` 与代码审查共同约束。

- 当前项目不使用 Spotless 或 Google Java Format。

## 8.Frontend formatting:
- Use Prettier as the canonical formatter.
- Run `npm run format` before commit when formatting changes are needed.
- Run `npm run format:check` in verification.
- printWidth: 100
- 2-space indentation
- single quotes
- no semicolons
