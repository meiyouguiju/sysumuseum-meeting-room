# 会议室预约系统 REST API 技术设计

文档版本：V0.2  
状态：设计基线（替代 V0.1）  
依据：MVP 业务规则 V1.0、数据库设计 V0.2、数据库物理设计 V0.1.1、Flyway V1。  
范围：接口契约设计；不包含 Controller、Service、Mapper、Entity、DTO 或前端实现。

## 1. 基线继承与本次修订范围

本文件替代《会议室预约系统 REST API 技术设计 V0.1》。除本文件明确修订、补充或重述的内容外，V0.1 的接口体系、字段含义、权限模型、错误码、HTTP 状态码、两段式幂等模型、数据库约束映射和设计检查结论均保持不变。本版本不引入新表、字段、迁移、接口或权限。

本次只确认以下边界：管理员修改的按状态请求体、创建命令规范化与哈希一致性、`Idempotency-Key` 与 `X-Request-Id` 格式、日程日期范围、容量 warning 的稳定重放、CSV 导出安全，以及稳定响应中的动态追踪信息处理。

## 2. 仍然有效的统一约定

- API 前缀为 `/api/v1`，JSON 使用 `camelCase`。
- 业务日期为 `YYYY-MM-DD`；业务日期时间为 `YYYY-MM-DDTHH:mm:ss`，不携带 `Z` 或偏移量。全部按 `Asia/Shanghai` 语义解释，映射 MySQL `DATETIME(0)` 和 Java `LocalDateTime`。
- 认证由后续不可伪造的 `CurrentUser(userId, displayName, roleCode, userStatus)` 提供。客户端不得传递或决定 `organizerUserId`、`actorUserId` 或角色。未认证为 `401 UNAUTHENTICATED`。
- 成功响应直接返回资源或集合；错误响应为 `errorCode`、`message`、可选 `fieldErrors` 与当前请求的 `requestId`。分页仍使用 `page`（从 1 开始，默认 1）和 `size`（默认 20，最大 100）；列表固定排序，不开放任意排序字段。
- 主要 HTTP 状态码仍为：`200`、`201`、`202`、`400`、`401`、`403`、`404`、`409`、`422`、`500`、`503`。错误码集合保持 V0.1 不变。

### 2.1 `X-Request-Id`

客户端可选传递 `X-Request-Id`，用于关联当前一次 HTTP 请求。服务端只接受符合 `[A-Za-z0-9._:-]{1,64}` 的安全 ASCII 值。

- 缺失时服务端生成 `requestId`。
- 超长、含控制字符、换行或其他不安全字符时，不拒绝业务请求；服务端忽略该值并生成新的 `requestId`。
- 不安全客户端值不得原样写入日志、错误消息或响应头。
- 所有响应的 `X-Request-Id`，以及错误体的 `requestId`，均为**本次** HTTP 请求的值。

## 3. 接口目录（路径与数量不变）

| 分类 | 接口 |
| --- | --- |
| 当前用户 | `GET /api/v1/me` |
| 会议室与日程 | `GET /api/v1/rooms`；`GET /api/v1/schedules` |
| 普通预约 | `POST /api/v1/bookings`；`GET /api/v1/bookings/idempotency-result`；`GET /api/v1/bookings/{bookingId}`；`PATCH /api/v1/bookings/{bookingId}`；`POST /api/v1/bookings/{bookingId}/cancel`；`GET /api/v1/me/bookings` |
| 管理员会议室 | `POST /api/v1/admin/rooms`；`PATCH /api/v1/admin/rooms/{roomId}`；`POST /api/v1/admin/rooms/{roomId}/enable`；`POST /api/v1/admin/rooms/{roomId}/disable` |
| 管理员预约 | `GET /api/v1/admin/bookings`；`PATCH /api/v1/admin/bookings/{bookingId}`；`POST /api/v1/admin/bookings/{bookingId}/cancel`；`GET /api/v1/admin/bookings/export` |

共 17 个接口。会议室公开表示、预约详情、公共日程摘要、取消槽释放摘要及管理员列表字段均沿用 V0.1。

## 4. 日程查询

### 4.1 `GET /api/v1/schedules?date=YYYY-MM-DD`

接口仍一次返回指定自然日的全部会议室、公开预约摘要与 `unavailableSlots`，供桌面二维时间表和手机单会议室时间轴复用；不得按 30 分钟格子逐个请求。公共摘要仅包括必要的会议主题、预约人姓名、起止时间和派生展示状态，不返回参会人员、说明、取消原因、联系方式等敏感数据。

`date` 的服务端校验范围为：

- 任意历史自然日允许查询；
- 今天允许查询；
- 未来最多到服务端北京时间“今天 + 13 天”（含今天共 14 个自然日）；
- 超过该上限返回 `400 REQUEST_VALIDATION_ERROR`。

这是 MVP 日程页面的查询范围，不改变创建或修改预约所执行的独立“未来 14 天”业务校验。

## 5. 创建预约与严格幂等

### 5.1 `POST /api/v1/bookings`

| 项目 | 规则 |
| --- | --- |
| 权限 | 已认证且状态为 `ACTIVE` 的当前用户 |
| 必需 Header | `Idempotency-Key` |
| 可选 Header | `X-Request-Id` |
| 成功 | `201 Created`，返回稳定首次创建结果 |

请求体字段保持不变：`roomId`、`subject`、`startTime`、`endTime`、可选 `attendeeCount`、`participantsText`、`description`。不接受 `organizerUserId`。

`Idempotency-Key` 必须匹配 `[A-Za-z0-9._:-]{1,128}`；UUID 与 ULID 均符合。缺失返回 `400 IDEMPOTENCY_KEY_REQUIRED`，格式不合法在领取幂等记录之前返回 `400 IDEMPOTENCY_KEY_INVALID`，不持久化 `FAILED`。

### 5.2 规范化命令与 `requestHash`

`requestHash` 不是原始 HTTP JSON 的散列。处理顺序固定为：

```text
HTTP 请求 → 解析 → 格式层校验 → 构造规范化 CreateBookingCommand
→ 计算 requestHash → 幂等领取与后续业务校验、持久化
```

格式层错误（JSON 无法解析、Key 缺失/非法、未知字段、必填字段缺失、字段类型或日期时间无法转换、无法形成创建命令）直接返回 `400`，不领取记录。

规范化命令固定包含七个字段：`roomId`、`subject`、`startTime`、`endTime`、`attendeeCount`、`participantsText`、`description`。可选字段缺失统一为 `null`；日期时间解析后统一格式化为 `yyyy-MM-dd'T'HH:mm:ss`；`subject` 去除首尾空白；其他文本字段保留其提交值。随后以键名字典序、UTF-8、无额外空白的规范 JSON 计算 SHA-256。

同一个规范化 `CreateBookingCommand` 必须同时用于后续业务校验和数据库持久化。例如 `subject` 的哈希值使用去首尾空格后的文本，则 `booking.subject` 也必须写入这一去空格后的文本；不得让哈希和实际持久化使用不同的值。

哈希字段范围不包含 Header、`requestId`、认证身份、服务端派生字段或动态展示字段。

### 5.3 两段式幂等及稳定响应

两段式模型保持不变：先以独立短事务提交 `PROCESSING` 的 `(CREATE_BOOKING, CurrentUser.id, Idempotency-Key)` 记录；业务事务通过 `SELECT ... FOR UPDATE` 锁定该记录，写入预约、全部 `booking_slot`、审计和 `SUCCEEDED` 稳定结果；确定性业务失败在回滚业务事务后，由独立短事务写入 `FAILED` 稳定结果；基础设施异常保持 `PROCESSING`，由恢复机制在取得行锁后终结。

稳定的首次响应保存 HTTP 状态码、稳定业务响应体或稳定错误体、`failureCode`（失败时）和 `bookingId`（成功时）。`response_body` 不保存 `requestId`、当前时间线、`displayStatus`、追踪字段等动态内容。

容量超出不阻止创建。首次 `201` 的稳定成功响应增加 `warnings`：

```json
{
  "id": 101,
  "bookingNo": "01J...",
  "subject": "校史馆展陈讨论会",
  "status": "ACTIVE",
  "version": 1,
  "warnings": [
    {"code": "ROOM_CAPACITY_EXCEEDED", "message": "预计人数超过会议室容量"}
  ]
}
```

`warnings` 是稳定首次 `response_body` 的组成部分：同 Key、同 Hash 的 POST 重放，及幂等查询中 `SUCCEEDED` 的结果，必须原样返回首次 `warnings`，即使此后会议室容量发生变化。

| 情况 | HTTP | 行为 |
| --- | --- | --- |
| 首次成功 | `201` | 创建预约并保存 `SUCCEEDED` 稳定响应（含 warnings） |
| 同 Key、同 Hash、`SUCCEEDED` | 首次 `201` | 原样重放稳定成功内容；不新建预约 |
| 同 Key、同 Hash、`FAILED` | 首次失败状态 | 原样重放稳定失败内容；不重新执行业务 |
| 同 Key、不同 Hash | `409 IDEMPOTENCY_KEY_REUSED` | 不处理新命令 |
| 同 Key、`PROCESSING` | `202 IDEMPOTENCY_PROCESSING` | 不重新执行 |

已形成合法命令后的会议室不存在/停用、14 天范围、半小时边界、跨日、时长、槽冲突等确定性规则失败仍进入幂等流程并持久化 `FAILED`。网络超时不是创建失败结论；客户端必须用原 Key 查询结果，结果未知时不得改用新 Key 再次创建。

### 5.4 `GET /api/v1/bookings/idempotency-result`

Header 必需为合法 `Idempotency-Key`；服务端固定查询：

```text
operation_type = CREATE_BOOKING
AND user_id = CurrentUser.id
AND idempotency_key = Header 值
```

客户端不传 `operationType`。因此既复用联合唯一索引，也不会泄露其他用户记录。

| 状态 | HTTP | 响应 |
| --- | --- | --- |
| `PROCESSING` | `202` | `{"status":"PROCESSING","message":"预约结果暂时无法确认，请稍后重新查询。"}` |
| `SUCCEEDED` | `200` | `{"status":"SUCCEEDED","originalHttpStatus":201,"response":{...稳定首次成功响应，含 warnings...}}` |
| `FAILED` | `200` | `{"status":"FAILED","originalHttpStatus":409,"failureCode":"BOOKING_SLOT_CONFLICT","response":{...稳定首次错误响应...}}` |
| 不存在、过期或已清理 | `404 IDEMPOTENCY_RESULT_NOT_FOUND_OR_EXPIRED` | 不暴露其他用户信息 |

每次调用均生成当前请求自己的 `X-Request-Id`。若重放的是错误，错误结构中的 `requestId` 也必须由当前请求重新注入，不得使用首次处理时的 ID。首业务事务回滚与 `FAILED` 短事务提交之间的极短窗口，只能等待重查或得到 `202`，绝不可由重复 POST 或查询重新创建。

## 6. 修改与取消

### 6.1 `PATCH /api/v1/bookings/{bookingId}`

普通用户仅能修改本人尚未开始且 `ACTIVE` 的预约，继续使用完整可编辑字段请求体，且 `version` 必填：

```json
{
  "version": 1,
  "roomId": 1,
  "subject": "更新后的主题",
  "startTime": "2026-08-22T09:30:00",
  "endTime": "2026-08-22T11:00:00",
  "attendeeCount": 12,
  "participantsText": "张三、李四",
  "description": "更新说明"
}
```

仍以 `id + version` 乐观锁更新；版本冲突为 `409 BOOKING_VERSION_CONFLICT`。时间或会议室变更重新校验时间规则和 `booking_slot` 唯一约束；冲突返回 `409 BOOKING_SLOT_CONFLICT` 且原预约不变。已开始、已结束、已取消分别使用既定 `409` 错误码。

### 6.2 `PATCH /api/v1/admin/bookings/{bookingId}`

管理员只能操作 `ACTIVE`、未结束预约；修改他人时 `reason` 必填，成功时写完整 before/after 审计并递增 `version`。

**预约未开始**时，请求体必须提交完整允许字段：

```json
{
  "version": 1,
  "roomId": 1,
  "subject": "更新后的主题",
  "startTime": "2026-08-22T09:30:00",
  "endTime": "2026-08-22T11:00:00",
  "attendeeCount": 12,
  "participantsText": "张三、李四",
  "description": "更新说明",
  "reason": "场地安排调整"
}
```

此状态下可修改会议室、日期、起止时间和非时间字段，服务端重新校验 14 天、会议室启用状态及槽冲突。

**预约已开始但尚未结束**时，请求体只允许以下字段：

```json
{
  "version": 1,
  "subject": "现场调整后的主题",
  "attendeeCount": 12,
  "participantsText": "张三、李四",
  "description": "更新说明",
  "reason": "补充会议记录"
}
```

此时不得提交 `roomId`、`startTime`、`endTime`；请求中出现任一字段返回 `409 BOOKING_STARTED_TIME_FIELDS_IMMUTABLE`。这些字段既不要求、也不允许由客户端补齐。后续 Java 实现应采用两个独立请求 DTO/校验模型，按服务端读取到的预约时间状态选择校验模型，避免沿用“完整修改体”而导致所有进行中管理员修改失败。

预约已结束返回 `409 BOOKING_ALREADY_ENDED`；已取消返回 `409 BOOKING_ALREADY_CANCELLED`。管理员仍可按既定规则取消进行中预约：当前 30 分钟槽保留，后续槽释放。

### 6.3 取消、列表和详情

`POST /api/v1/bookings/{bookingId}/cancel`、`POST /api/v1/admin/bookings/{bookingId}/cancel`、`GET /api/v1/me/bookings`、`GET /api/v1/bookings/{bookingId}` 的路径、字段和既定规则不变。普通用户仅操作本人；管理员取消他人必须提供原因；已开始未结束取消保留当前槽并释放后续槽；已结束不能取消；前端在成功或冲突后重新获取当前日程。

## 7. 管理员会议室、预约查询与导出

管理员会议室新增、修改、启用、停用，预约列表及详情复用规则均不变；无物理删除、停用不破坏历史预约、管理员不提供代订。

### 7.1 `GET /api/v1/admin/bookings/export`

查询参数、固定导出字段和按 `startTime` 日期闭区间筛选语义保持不变。响应仍为：

```text
Content-Type: text/csv; charset=utf-8
Content-Disposition: attachment
```

输出使用 UTF-8，建议在文件开头写入 UTF-8 BOM，以保证 Windows Excel 的中文显示。CSV 生成必须同时满足：

1. 对预约主题、参会人员、说明、取消原因、预约人姓名、会议室名称及任何其他文本列，先按统一 CSV 公式注入策略处理；单元格首个有效字符为 `=`、`+`、`-`、`@` 时，在内容前增加单引号 `'`。
2. 统一正确转义逗号、双引号、CR、LF：字段使用 RFC 4180 风格双引号包裹，字段内双引号替换为两个双引号。
3. 公式防护和 CSV 转义的执行顺序必须固定，并覆盖空白前缀后的首个有效字符，避免通过前置空格绕过防护。

这只改变导出编码与安全实现要求，不增加导出字段或自定义报表能力。

## 8. 设计一致性检查

1. **需求与数据库：** 本版本继续落实 USER/ADMIN、无代订、未来 14 天、30 分钟槽、`booking.version` 乐观锁、进行中取消与 `booking_slot` 最终冲突裁决；未改变 V0.2 数据库逻辑或 Flyway V1。
2. **幂等：** 规范化命令消除了哈希值和持久化值不一致的风险；稳定响应不再污染动态 requestId；warning 可被可靠重放；协议层错误与确定性业务失败边界保持清晰。
3. **越权与隐私：** 预约人和审计操作人仍只来自认证上下文；结果查询按当前用户和固定 `CREATE_BOOKING` 操作隔离；公共日程不暴露参会信息。
4. **前端可实现性：** 管理员修改有两种明确请求形态，前端可根据详情 `displayStatus` 决定表单；日程一次请求支持桌面与手机，不存在按时间格 N+1 请求。
5. **产品待确认项：** 无。V0.2 不新增业务选择，仅将已确认技术决策固化为接口契约。
