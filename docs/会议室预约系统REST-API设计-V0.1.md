# 会议室预约系统 REST API 技术设计

文档版本：V0.1  
状态：设计基线  
依据：MVP 业务规则 V1.0、数据库设计 V0.2、数据库物理设计 V0.1.1、Flyway V1。  
范围：接口契约设计；不包含 Controller、Service、Mapper、Entity、DTO 或前端实现。

## 1. 认证上下文与统一约定

### 1.1 认证上下文抽象

除 Actuator 外，本文全部 API 均假定已由后续认证层提供不可伪造的 `CurrentUser`：

```text
userId, displayName, roleCode(USER|ADMIN), userStatus
```

- 预约人、修改人、取消人和审计操作人只能从该上下文取得。
- 客户端不得提交或指定 `organizerUserId`、`actorUserId`、角色等身份字段。
- 未认证为 `401 UNAUTHENTICATED`；停用用户不能创建、修改、取消预约。
- 当前阶段不实现认证；本约定仅定义后续接口边界。

### 1.2 URL、命名与时间

- 统一前缀：`/api/v1`。
- JSON 使用 `camelCase`。
- 日期：`YYYY-MM-DD`，例如 `2026-08-22`。
- 业务日期时间：`YYYY-MM-DDTHH:mm:ss`，例如 `2026-08-22T09:30:00`；不携带 `Z` 或 `+08:00` 偏移。
- 所有业务时间语义固定为 `Asia/Shanghai`，与 MySQL `DATETIME(0)`、Java `LocalDateTime` 一致；服务端以北京时间判定“当前时间”、14 天窗口和进行中状态。
- API 不接收 `TIMESTAMP`、UTC 时间或跨时区转换后的预约时间。

### 1.3 成功、错误、分页与追踪

成功响应直接返回资源或集合，不再额外包裹通用 `data` 对象。服务端在响应头返回 `X-Request-Id`；客户端可选发送该 Header 作为关联 ID，服务端缺失时生成。错误响应同时包含 `requestId`，便于排查。

```json
{
  "errorCode": "BOOKING_SLOT_CONFLICT",
  "message": "所选会议室的部分时间段已被预约。",
  "fieldErrors": [],
  "requestId": "req_01J..."
}
```

参数校验错误使用 `400`，并以 `fieldErrors` 返回字段级信息：

```json
{
  "errorCode": "REQUEST_VALIDATION_ERROR",
  "message": "请求参数不合法。",
  "fieldErrors": [
    {"field": "subject", "message": "会议主题不能为空"}
  ],
  "requestId": "req_01J..."
}
```

分页仅用于列表接口：

- `page`：从 `1` 开始，默认 `1`。
- `size`：默认 `20`，最大 `100`。
- 不开放任意字段排序；每个接口固定排序，避免排序字段注入和前后端语义不一致。

分页响应：

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 0,
  "totalPages": 0
}
```

主要 HTTP 状态：`200` 查询/更新成功、`201` 创建成功、`202` 幂等仍在处理、`400` 协议或参数错误、`401` 未认证、`403` 无权限、`404` 不存在、`409` 资源/版本/幂等冲突、`422` 已形成合法命令后的业务规则失败、`500` 未预期错误、`503` 基础设施暂不可用。

## 2. 公共资源表示

### 2.1 会议室表示 `Room`

```json
{
  "id": 1,
  "name": "博物馆2A会议室",
  "location": "543栋2A层",
  "capacity": 100,
  "facilitiesText": "电脑、智慧屏、麦克风",
  "usageNotice": "请在会议结束后关闭设备。",
  "status": "ENABLED",
  "sortOrder": 10
}
```

### 2.2 日程公开预约摘要 `ScheduleBooking`

普通用户可见完整主题和预约人姓名，但不能得到参会人员、会议说明、取消原因、部门或联系方式。

```json
{
  "id": 101,
  "roomId": 1,
  "subject": "校史馆展陈讨论会",
  "organizerName": "张三",
  "startTime": "2026-08-22T09:00:00",
  "endTime": "2026-08-22T10:30:00",
  "displayStatus": "UPCOMING"
}
```

`displayStatus` 为读取时派生字段：`UPCOMING`、`IN_PROGRESS`、`ENDED`，不对应 `booking.status` 的持久化值。公共日程不返回已取消预约。

### 2.3 完整预约 `BookingDetail`

仅预约人或管理员获取。管理员可获取所有预约；普通用户不可通过详情接口读取他人参会信息。

```json
{
  "id": 101,
  "bookingNo": "01J...",
  "room": {"id": 1, "name": "博物馆2A会议室"},
  "organizer": {"id": 9, "displayName": "张三"},
  "subject": "校史馆展陈讨论会",
  "attendeeCount": 12,
  "participantsText": "张三、李四",
  "description": "讨论下阶段展陈方案",
  "startTime": "2026-08-22T09:00:00",
  "endTime": "2026-08-22T10:30:00",
  "status": "ACTIVE",
  "displayStatus": "UPCOMING",
  "version": 1,
  "cancelledAt": null,
  "cancelReason": null,
  "createdAt": "2026-08-20T14:00:00",
  "updatedAt": "2026-08-20T14:00:00"
}
```

`displayStatus` 不会写入幂等首次响应；幂等首次成功响应仅保存稳定的持久化预约字段及创建时固定的资源名称快照。

## 3. 当前用户、会议室与日程

### 3.1 当前用户

| 项目 | 设计 |
| --- | --- |
| Method / Path | `GET /api/v1/me` |
| 权限 | 已认证用户 |
| 成功 | `200` |
| 错误 | `401 UNAUTHENTICATED` |

响应：

```json
{"id":9,"displayName":"张三","departmentName":"校史馆","roleCode":"USER","status":"ACTIVE"}
```

### 3.2 会议室列表

| 项目 | 设计 |
| --- | --- |
| Method / Path | `GET /api/v1/rooms` |
| 权限 | 已认证用户 |
| 成功 | `200`，按 `sortOrder ASC, id ASC` |
| 错误 | `401` |

返回全部会议室，包含 `DISABLED`，符合“所有用户可查看停用会议室及历史预约”的基线。创建/修改预约仍仅允许目标为 `ENABLED`。

### 3.3 指定日期日程总览

| 项目 | 设计 |
| --- | --- |
| Method / Path | `GET /api/v1/schedules` |
| 权限 | 已认证用户 |
| Query | `date`，必填，`YYYY-MM-DD` |
| 成功 | `200` |
| 错误 | `400 REQUEST_VALIDATION_ERROR`、`401` |

响应一次返回日期对应的全部会议室、有效预约摘要和取消后的当前槽保留信息，禁止按 30 分钟格子发起 N+1 请求：

```json
{
  "date": "2026-08-22",
  "timeZone": "Asia/Shanghai",
  "slotMinutes": 30,
  "focusWindow": {"start": "08:30", "end": "17:30"},
  "rooms": [
    {"id": 1, "name": "博物馆2A会议室", "status": "ENABLED", "capacity": 100}
  ],
  "bookings": [
    {
      "id": 101,
      "roomId": 1,
      "subject": "校史馆展陈讨论会",
      "organizerName": "张三",
      "startTime": "2026-08-22T09:00:00",
      "endTime": "2026-08-22T10:30:00",
      "displayStatus": "UPCOMING"
    }
  ],
  "unavailableSlots": [
    {
      "roomId": 1,
      "slotStart": "2026-08-22T10:00:00",
      "reason": "CANCELLED_CURRENT_SLOT_HOLD"
    }
  ]
}
```

`unavailableSlots` 仅表示取消进行中会议后必须保留的当前槽，不返回原预约主题、预约人或 `bookingId`。桌面二维表和手机单会议室时间轴均可基于同一响应渲染。

## 4. 创建预约与严格幂等

### 4.1 创建接口

| 项目 | 设计 |
| --- | --- |
| Method / Path | `POST /api/v1/bookings` |
| 权限 | 已认证且状态为 `ACTIVE` 的用户 |
| 必需 Header | `Idempotency-Key`，1～128 个非空可打印字符；建议 UUID/ULID |
| 可选 Header | `X-Request-Id` |
| 成功 | `201 Created`，返回稳定首次创建结果 |

请求体：

```json
{
  "roomId": 1,
  "subject": "校史馆展陈讨论会",
  "startTime": "2026-08-22T09:00:00",
  "endTime": "2026-08-22T10:30:00",
  "attendeeCount": 12,
  "participantsText": "张三、李四",
  "description": "讨论下阶段展陈方案"
}
```

没有 `organizerUserId`。预约人和姓名快照由认证上下文确定。

### 4.2 请求哈希与领取边界

先完成 HTTP/格式层校验，再计算 `requestHash` 并领取 `idempotency_record`。

**领取前直接返回 4xx，不保存 FAILED：** JSON 无法解析、`Idempotency-Key` 缺失或非法、未知字段、必填字段缺失、字段类型/日期时间格式无法转换，及其他无法形成创建预约命令的错误。

**哈希字段范围：** `roomId`、`subject`、`startTime`、`endTime`、`attendeeCount`、`participantsText`、`description`。不包含 Header、`requestId`、认证身份和服务端派生字段。

**规范化规则：**

1. 仅允许上述字段；未知字段在领取前拒绝。
2. 固定生成上述七个键，缺失的可选字段规范化为 JSON `null`。
3. `subject` 去首尾空白后参与哈希；其余文本按原值参与哈希。
4. 日期时间先解析为 `LocalDateTime`，再以固定 `yyyy-MM-dd'T'HH:mm:ss` 重新序列化。
5. 键按字典序排序、UTF-8 编码、无额外空白的 JSON 计算 SHA-256，写入 `request_hash`。

这确保“可选字段缺失”和“显式 null”语义一致，也确保同一命令的格式差异不会意外产生不同哈希。

### 4.3 两段式处理与响应

1. 独立短事务插入 `operationType=CREATE_BOOKING`、当前 `userId`、Key、Hash、`PROCESSING` 和 `expiresAt=createdAt+24小时`。
2. 业务事务使用 `SELECT ... FOR UPDATE` 锁定该记录，校验会议室、时间规则和权限，创建 `booking`、插入全部 `booking_slot`、写审计，并在同一事务更新为 `SUCCEEDED`，保存首次 `201` 状态码及响应体。
3. 已形成合法命令后的确定性业务失败使业务事务回滚；随后独立短事务写 `FAILED`、首次 HTTP 状态码、`failureCode` 和稳定错误响应体。
4. 基础设施异常不伪造为 `FAILED`，记录保持 `PROCESSING` 并由既定恢复机制处理。

首次成功响应示例：

```json
{
  "id": 101,
  "bookingNo": "01J...",
  "room": {"id": 1, "name": "博物馆2A会议室"},
  "organizer": {"id": 9, "displayName": "张三"},
  "subject": "校史馆展陈讨论会",
  "attendeeCount": 12,
  "participantsText": "张三、李四",
  "description": "讨论下阶段展陈方案",
  "startTime": "2026-08-22T09:00:00",
  "endTime": "2026-08-22T10:30:00",
  "status": "ACTIVE",
  "version": 1,
  "createdAt": "2026-08-20T14:00:00"
}
```

首次终态响应保存范围：HTTP 状态码、以上稳定响应体或稳定错误体、`failureCode`、`bookingId`。不得保存或重放动态 `displayStatus`、当前时间线、`requestId`、追踪信息。

### 4.4 创建结果矩阵

| 情况 | HTTP | errorCode / 响应 | 幂等记录 |
| --- | --- | --- | --- |
| 首次成功 | `201` | 创建响应 | `SUCCEEDED` |
| 同 Key、同 Hash、已成功 | 首次 `201` | 原首次响应 | 不新建预约 |
| 同 Key、同 Hash、已失败 | 首次失败状态 | 原首次错误响应 | 不重新执行业务 |
| 同 Key、不同 Hash | `409` | `IDEMPOTENCY_KEY_REUSED` | 不处理新请求 |
| 同 Key、处理中 | `202` | `IDEMPOTENCY_PROCESSING` | 不重新执行 |
| 会议室不存在 | `404` | `MEETING_ROOM_NOT_FOUND` | `FAILED` |
| 会议室停用 | `409` | `MEETING_ROOM_DISABLED` | `FAILED` |
| 超过未来 14 天 | `422` | `BOOKING_WINDOW_EXCEEDED` | `FAILED` |
| 非半小时/结束不晚于开始/过去时间 | `422` | `BOOKING_TIME_INVALID` | `FAILED` |
| 跨日 | `422` | `BOOKING_CROSS_DAY_NOT_ALLOWED` | `FAILED` |
| 时长超过 5 小时 | `422` | `BOOKING_DURATION_EXCEEDED` | `FAILED` |
| 槽唯一约束冲突 | `409` | `BOOKING_SLOT_CONFLICT` | `FAILED` |

`attendeeCount` 超过容量只在成功响应中附带 `warnings`，例如 `[{"code":"ROOM_CAPACITY_EXCEEDED","message":"预计人数超过会议室容量"}]`；不得拒绝创建。

网络超时后，客户端必须使用原 Header 的 `Idempotency-Key` 调用 4.5 的查询接口；在结果未知时不得换 Key 再次提交。

### 4.5 幂等处理结果查询

| 项目 | 设计 |
| --- | --- |
| Method / Path | `GET /api/v1/bookings/idempotency-result` |
| 权限 | 当前已认证用户 |
| 必需 Header | `Idempotency-Key` |
| operationType | 服务端固定为 `CREATE_BOOKING`，客户端不传，直接使用联合唯一索引 |

查询条件始终为 `(operation_type='CREATE_BOOKING', user_id=CurrentUser.id, idempotency_key=Header)`，因此不能读取其他用户记录。

| 记录状态 | HTTP | 响应 |
| --- | --- | --- |
| `PROCESSING` | `202` | `{"status":"PROCESSING","message":"预约结果暂时无法确认，请稍后重新查询。"}` |
| `SUCCEEDED` | `200` | `{"status":"SUCCEEDED","originalHttpStatus":201,"response":{...首次创建响应...}}` |
| `FAILED` | `200` | `{"status":"FAILED","originalHttpStatus":409,"failureCode":"BOOKING_SLOT_CONFLICT","response":{...首次错误响应...}}` |
| 不存在、已过期或已清理 | `404` | `IDEMPOTENCY_RESULT_NOT_FOUND_OR_EXPIRED` |

即使第二个请求恰好位于首业务事务回滚和 `FAILED` 短事务提交之间，也只能等待后再次查询，或收到 `202`；不得由查询或重复 POST 自行再创建预约。

## 5. 普通用户预约接口

### 5.1 查询单个预约详情

| 项目 | 设计 |
| --- | --- |
| Method / Path | `GET /api/v1/bookings/{bookingId}` |
| 权限 | 预约人本人或 `ADMIN` |
| 成功 | `200 BookingDetail` |
| 错误 | `401`、`403 BOOKING_ACCESS_DENIED`、`404 BOOKING_NOT_FOUND` |

普通用户查看他人预约时使用日程公开摘要，不使用此接口，避免泄露参会人员及说明。

### 5.2 修改本人预约

| 项目 | 设计 |
| --- | --- |
| Method / Path | `PATCH /api/v1/bookings/{bookingId}` |
| 权限 | 当前预约人；预约必须未开始且 `ACTIVE` |
| 成功 | `200 BookingDetail` |

请求体：

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

本接口采用完整可编辑字段提交，`version` 必填。服务端更新条件必须包含 `id + version`；受影响行数为零时返回 `409 BOOKING_VERSION_CONFLICT`，前端刷新详情与日程后重试。

时间或会议室变化时重新校验创建规则和时间槽唯一约束；冲突返回 `409 BOOKING_SLOT_CONFLICT`，原预约与原槽保持不变。已开始返回 `409 BOOKING_ALREADY_STARTED`；已结束返回 `409 BOOKING_ALREADY_ENDED`；已取消返回 `409 BOOKING_ALREADY_CANCELLED`。成功后前端重新获取当前日期日程。

### 5.3 取消本人预约

| 项目 | 设计 |
| --- | --- |
| Method / Path | `POST /api/v1/bookings/{bookingId}/cancel` |
| 权限 | 当前预约人；未结束、`ACTIVE` |
| 成功 | `200` |

请求体：

```json
{"version": 1, "reason": "临时取消"}
```

`reason` 对本人可省略。响应包含稳定取消事实及槽释放摘要：

```json
{
  "id": 101,
  "status": "CANCELLED",
  "version": 2,
  "cancelledAt": "2026-08-22T10:12:00",
  "slotRelease": {
    "mode": "AFTER_CURRENT_SLOT",
    "heldSlotStart": "2026-08-22T10:00:00",
    "releasedFrom": "2026-08-22T10:30:00"
  }
}
```

未开始预约的 `slotRelease.mode` 为 `IMMEDIATE`，全部槽释放。进行中取消保留当前槽为 `CANCELLED_CURRENT_SLOT_HOLD`、删除后续槽；已结束或已取消均返回 `409` 对应错误码。成功后前端重新获取日程。

### 5.4 我的预约

| 项目 | 设计 |
| --- | --- |
| Method / Path | `GET /api/v1/me/bookings` |
| 权限 | 已认证用户 |
| Query | `page`、`size` |
| 排序 | `startTime DESC, id DESC` |
| 成功 | `200` 分页 `BookingDetail` 列表 |

列表包含未来、已结束和已取消预约，不提供会议室、主题、日期或预约人搜索，符合 MVP 规则。

## 6. 管理员接口

所有本节接口均要求 `CurrentUser.roleCode=ADMIN`；否则 `403 FORBIDDEN`。管理员同样受未来 14 天限制。

### 6.1 会议室管理

| 操作 | Method / Path | Request 要点 | 成功 |
| --- | --- | --- | --- |
| 新增 | `POST /api/v1/admin/rooms` | `name`、`location`、`capacity` 必填；设备、须知、排序可选 | `201 Room` |
| 修改 | `PATCH /api/v1/admin/rooms/{roomId}` | 可改名称、位置、容量、设备、须知、排序 | `200 Room` |
| 启用 | `POST /api/v1/admin/rooms/{roomId}/enable` | 无 Body | `200 Room` |
| 停用 | `POST /api/v1/admin/rooms/{roomId}/disable` | 无 Body | `200 Room` |

错误：`404 MEETING_ROOM_NOT_FOUND`、`409 MEETING_ROOM_NAME_CONFLICT`。不提供删除接口。停用不取消已有未来预约，且创建/时间变更接口不得选用停用会议室。

### 6.2 管理员预约查询与详情

| 项目 | 设计 |
| --- | --- |
| 列表 | `GET /api/v1/admin/bookings?page=1&size=20` |
| 排序 | `startTime DESC, id DESC` |
| 成功 | `200`，分页管理员预约摘要 |

MVP 不增加任意搜索或排序字段。管理员若需完整单条内容，可复用 `GET /api/v1/bookings/{bookingId}`，该接口已允许管理员读取完整详情。

管理员列表摘要可返回预约号、会议室、预约人、主题、起止时间、持久化状态、派生展示状态和版本；不在列表重复返回大文本参会人员与说明，详情和导出才包含。

### 6.3 管理员修改预约

| 项目 | 设计 |
| --- | --- |
| Method / Path | `PATCH /api/v1/admin/bookings/{bookingId}` |
| 权限 | `ADMIN`，预约为 `ACTIVE` 且未结束 |
| 成功 | `200 BookingDetail` |

请求体基于普通修改体，新增 `reason`。当管理员修改他人预约时 `reason` 必填；修改自己的预约可为空。

- 未开始：可修改全部允许字段，时间/会议室修改重新校验 14 天与槽冲突。
- 已开始未结束：仅允许 `subject`、`attendeeCount`、`participantsText`、`description`；请求中出现 `roomId`、`startTime`、`endTime` 一律返回 `409 BOOKING_STARTED_TIME_FIELDS_IMMUTABLE`。
- 已结束：返回 `409 BOOKING_ALREADY_ENDED`。
- 每次成功修改写完整 before/after 审计、操作人角色与必要原因，并递增 `version`。

### 6.4 管理员取消预约

| 项目 | 设计 |
| --- | --- |
| Method / Path | `POST /api/v1/admin/bookings/{bookingId}/cancel` |
| 权限 | `ADMIN`，预约为 `ACTIVE` 且未结束 |
| 成功 | `200`，与本人取消相同的槽释放摘要 |

请求体：`{"version":1,"reason":"场地临时调整"}`。

取消他人预约时 `reason` 必填；进行中取消遵循“保留当前 30 分钟槽，释放后续槽”规则，并记录完整审计。

### 6.5 管理员预约导出

| 项目 | 设计 |
| --- | --- |
| Method / Path | `GET /api/v1/admin/bookings/export` |
| 权限 | `ADMIN` |
| Query | 可选 `fromDate`、`toDate`；省略时导出当前北京时间所在自然日 |
| 成功 | `200 text/csv; charset=utf-8`，`Content-Disposition: attachment` |

该接口不是通用自定义报表：字段固定为预约号、会议室、预约人、主题、预计人数、参会人员、说明、起止时间、状态、取消时间、取消原因、创建时间、最后修改时间。日期范围必须满足 `fromDate <= toDate`；范围含义固定为预约 `startTime` 落入闭区间。管理员有权限导出参会人员信息。

## 7. 错误码

| errorCode | HTTP | 含义 |
| --- | --- | --- |
| `REQUEST_BODY_INVALID` | 400 | JSON 或字段类型无法解析 |
| `REQUEST_VALIDATION_ERROR` | 400 | 命令无法形成，如缺必填字段、未知字段 |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | 创建或结果查询缺少 Key |
| `IDEMPOTENCY_KEY_INVALID` | 400 | Key 格式或长度非法 |
| `UNAUTHENTICATED` | 401 | 无认证上下文 |
| `FORBIDDEN` | 403 | 当前角色无接口权限 |
| `BOOKING_ACCESS_DENIED` | 403 | 非本人试图读取/操作私有预约 |
| `MEETING_ROOM_NOT_FOUND` | 404 | 会议室不存在 |
| `BOOKING_NOT_FOUND` | 404 | 预约不存在 |
| `IDEMPOTENCY_RESULT_NOT_FOUND_OR_EXPIRED` | 404 | 当前用户的 Key 不存在、已过期或已清理 |
| `MEETING_ROOM_NAME_CONFLICT` | 409 | 会议室名称已存在 |
| `MEETING_ROOM_DISABLED` | 409 | 停用会议室不能用于新增/时间变更 |
| `BOOKING_SLOT_CONFLICT` | 409 | 数据库时间槽唯一约束冲突 |
| `BOOKING_VERSION_CONFLICT` | 409 | 乐观锁版本已过期 |
| `BOOKING_ALREADY_CANCELLED` | 409 | 已取消预约不可再修改/取消 |
| `BOOKING_ALREADY_STARTED` | 409 | 普通用户修改已开始预约 |
| `BOOKING_STARTED_TIME_FIELDS_IMMUTABLE` | 409 | 管理员不能修改进行中预约的时间字段/会议室 |
| `BOOKING_ALREADY_ENDED` | 409 | 已结束预约不可修改或取消 |
| `IDEMPOTENCY_KEY_REUSED` | 409 | 同 Key 对应不同请求 Hash |
| `IDEMPOTENCY_PROCESSING` | 202 | 同 Key 首次请求仍在处理 |
| `BOOKING_WINDOW_EXCEEDED` | 422 | 目标时间不在未来 14 个自然日内 |
| `BOOKING_TIME_INVALID` | 422 | 过去时间、非半小时边界或结束时间无效 |
| `BOOKING_CROSS_DAY_NOT_ALLOWED` | 422 | 预约跨自然日 |
| `BOOKING_DURATION_EXCEEDED` | 422 | 单次预约超过 5 小时 |
| `INTERNAL_ERROR` | 500 | 未预期服务端错误，不暴露异常细节 |
| `SERVICE_UNAVAILABLE` | 503 | 数据库等基础设施暂不可用 |

## 8. 设计检查结果

1. **需求 V1.0：** 已落实 14 天、30 分钟、5 小时、非跨日、进行中取消、管理员字段限制、公开摘要、无代订和无 WebSocket 规则。
2. **数据库设计 V0.2 / 物理设计 V0.1：** 创建接口与 `idempotency_record` 两段式状态、`booking.version`、审计快照和 `booking_slot` 两个唯一约束一致；不引入新表、字段或迁移。
3. **前端实现：** 日程接口按日期批量返回全部房间、预约块和保留槽，桌面及手机可复用，不存在按格子请求的 N+1 设计。
4. **幂等语义：** 格式层错误不领取；确定性业务失败稳定持久化；处理中不会再次创建；查询绑定当前用户，且固定服务端 operation type。
5. **越权与隐私：** 预约归属完全来自认证上下文；普通用户详情不可读取他人参会人员；公共日程不返回敏感内容；幂等查询按当前用户隔离。
6. **产品负责人确认项：** 无。管理员导出采用固定字段、可选简单日期范围，属于 V1.0 所述的固定导出能力，不构成复杂自定义报表。
