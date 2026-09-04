# 会议室预约系统 REST API 技术设计

文档版本：V1.1  
状态：正式基线  
依据：MVP 业务规则 V1.1、数据库设计 V0.2、数据库物理设计 V0.1.1、Flyway V1。  
范围：REST 契约；不包含具体 Controller、Service、Mapper、Entity、DTO 或前端实现。

## 1. 通用约定

- 前缀：`/api/v1`；JSON 为 `camelCase`；成功响应直接返回资源或集合，不包裹 `data`。
- 业务日期为 `YYYY-MM-DD`；日期时间为 `YYYY-MM-DDTHH:mm:ss`，不接受 `Z` 或偏移量。全部按 `Asia/Shanghai` 解释，对应 MySQL `DATETIME(0)` 与 Java `LocalDateTime`。
- 除 Actuator 外，接口均依赖不可伪造的 `CurrentUser(userId, displayName, roleCode(USER|ADMIN), userStatus)`。预约人、操作人和角色只能由服务端认证上下文确定；不得接受 `organizerUserId`、`actorUserId` 或角色参数。未认证为 `401 UNAUTHENTICATED`。
- `X-Request-Id` 可选。仅接收 `[A-Za-z0-9._:-]{1,64}`；合法值沿用，缺失或非法值由服务端生成。非法值不得写入日志；每个响应返回当前请求的 `X-Request-Id`，错误体也使用当前请求的 `requestId`。
- 错误体：`{"errorCode":"...","message":"...","fieldErrors":[],"requestId":"req_..."}`。参数字段错误在 `fieldErrors` 返回。不得暴露异常类名或堆栈。
- 分页：`page` 从 1 起、默认 1；`size` 默认 20、最大 100。列表不提供任意字段排序，采用各接口规定的固定排序。
- 主要状态码：`200` 成功，`201` 创建成功，`202` 幂等处理中，`400` 协议/参数错误，`401` 未认证，`403` 无权限，`404` 不存在，`409` 冲突，`422` 已形成合法命令后的业务规则失败，`500` 未预期错误，`503` 基础设施不可用。

## 2. 资源表示与可见性

`Room`：`id,name,location,capacity,facilitiesText,usageNotice,status,sortOrder`。

公共 `ScheduleBooking`：`id,roomId,subject,organizerName,isMine,startTime,endTime,displayStatus`；`isMine` 必须由服务端以 CurrentUser.userId 判断。`displayStatus` 按当前北京时间派生为 `UPCOMING`、`IN_PROGRESS`、`ENDED`，不持久化。公共日程不返回参会人员、说明、取消原因、部门或联系方式，也不显示已取消预约。

完整 `BookingDetail` 仅预约人或 ADMIN 可读，包含预约号、房间、预约人、主题、预计人数、参会人员、说明、起止时间、持久化 `status`、派生 `displayStatus`、`version`、取消和审计时间字段。`booking.status` 只为 `ACTIVE` 或 `CANCELLED`；结束状态由 `endTime` 派生。

## 3. 接口目录与契约

### 3.1 当前用户、会议室和日程

| Method / Path | 权限 | 请求 | 成功响应 | 错误 |
| --- | --- | --- | --- | --- |
| `GET /me` | 已认证 | 无 | `200 {id,displayName,departmentName,roleCode,status}` | `401 UNAUTHENTICATED` |
| `GET /rooms` | 已认证 | 无 | `200 Room[]`，含 ENABLED、DISABLED，按 `sortOrder ASC,id ASC` | `401` |
| `GET /schedules?date=` | 已认证 | 必填 `date` | `200 {date,timeZone:"Asia/Shanghai",slotMinutes:30,focusWindow:{start:"08:30",end:"17:30"},rooms,bookings,unavailableSlots}` | `400 REQUEST_VALIDATION_ERROR`、`401` |

表中的路径均省略统一前缀 `/api/v1`。日程一次返回全部会议室、当天有效预约块和保留槽，禁止前端按格子请求。历史和今天可查；未来仅到服务端北京时间今天 `+13` 天，超限 `400 REQUEST_VALIDATION_ERROR`。`unavailableSlots` 仅为 `{roomId,slotStart,reason:"CANCELLED_CURRENT_SLOT_HOLD"}`，不泄露原预约信息。

### 3.2 创建预约与结果查询

| Method / Path | 权限 | Header / 请求 | 成功响应 | 错误 |
| --- | --- | --- | --- | --- |
| `POST /bookings` | 已认证且 ACTIVE | 必需 `Idempotency-Key`；Body 为 `roomId,subject,startTime,endTime,attendeeCount?,participantsText?,description?`；`participantsText` 最多 2000 字符、`description` 最多 4000 字符 | `201` 稳定创建响应 | 见第 4 节 |
| `GET /bookings/idempotency-result` | 当前已认证用户 | 必需 `Idempotency-Key` | `200` 终态结果或 `202 PROCESSING` | `400`、`401`、`404` |
| `GET /bookings/{bookingId}` | 预约人或 ADMIN | Path id | `200 BookingDetail` | `401`、`403 BOOKING_ACCESS_DENIED`、`404 BOOKING_NOT_FOUND` |

创建的预约人来自 CurrentUser；客户端不传预约人。容量超出只警告不拒绝，成功响应可含 `warnings:[{code:"ROOM_CAPACITY_EXCEEDED",message:"预计人数超过会议室容量"}]`。

### 3.3 普通用户修改、取消和我的预约

| Method / Path | 权限 | 请求 | 成功响应 | 错误 |
| --- | --- | --- | --- | --- |
| `PATCH /bookings/{bookingId}` | 本人、ACTIVE、未开始 | 完整字段：`version,roomId,subject,startTime,endTime,attendeeCount,participantsText,description` | `200 BookingDetail` | `401`、`403`、`404`、`409`、`422` |
| `PATCH /bookings/{bookingId}/supplemental-info` | 预约创建者本人或 ACTIVE ADMIN；任意预约状态 | 仅 `version,attendeeCount,participantsText,description` | `200 BookingDetail` | `401`、`403`、`404`、`409` |
| `POST /bookings/{bookingId}/cancel` | 本人、ACTIVE、未结束 | `{version,reason?}` | `200 {id,status,version,cancelledAt,slotRelease}` | `401`、`403`、`404`、`409` |
| `GET /me/bookings?page=&size=&status?=&date?` | 已认证 | 分页；可选 status/date，先筛选后分页 | `200 {items,page,size,total,totalPages}`，按 `startTime DESC,id DESC` | `401` |

修改用 `id + version` 乐观锁；受影响行数为 0 时 `409 BOOKING_VERSION_CONFLICT`。时间/房间变更重校验全部创建规则和 `booking_slot` 唯一约束；冲突不改变原预约。普通用户不能通过完整修改接口修改已开始、已结束或已取消预约。补充信息接口不修改核心预约事实、不变更 `booking_slot`，允许预约创建者本人和 ACTIVE ADMIN 在 UPCOMING、IN_PROGRESS、ENDED、CANCELLED 任意状态修改预计人数、参会人员和说明；空白文本规范化为 NULL，并写 reason 为 NULL 的 UPDATE 审计。取消未开始预约立即释放所有槽；进行中取消保留当前 30 分钟槽、释放后续槽；已结束不可取消。成功或冲突后前端刷新日程。

### 3.4 管理员会议室

| Method / Path | 权限 | 请求 | 成功响应 | 错误 |
| --- | --- | --- | --- | --- |
| `POST /admin/rooms` | ADMIN | `name,location,capacity` 必填；`facilitiesText,usageNotice,sortOrder` 可选 | `201 Room` | `400`、`401`、`403`、`409 MEETING_ROOM_NAME_CONFLICT` |
| `PATCH /admin/rooms/{roomId}` | ADMIN | 可改名称、位置、容量、设备、须知、排序 | `200 Room` | `400`、`401`、`403`、`404`、`409` |
| `POST /admin/rooms/{roomId}/enable` | ADMIN | 无 body | `200 Room` | `401`、`403`、`404` |
| `POST /admin/rooms/{roomId}/disable` | ADMIN | 无 body | `200 Room` | `401`、`403`、`404` |

无删除接口。停用不取消或破坏历史预约，但不能作为新增/改期目标。

### 3.5 管理员预约

| Method / Path | 权限 | 请求 | 成功响应 | 错误 |
| --- | --- | --- | --- | --- |
| `GET /admin/bookings?page=&size=&organizerKeyword?=&fromDate?=&toDate?=&status?=&date?` | ADMIN | 分页；可选筛选，先筛选后分页；date 为兼容参数 | `200` 管理员摘要分页，按 `startTime DESC,id DESC` | `400`、`401`、`403` |
| `PATCH /admin/bookings/{bookingId}` | ADMIN、ACTIVE、未结束 | 按状态见下文 | `200 BookingDetail` | `401`、`403`、`404`、`409`、`422` |
| `POST /admin/bookings/{bookingId}/cancel` | ADMIN、ACTIVE、未结束 | `{version,reason}` | `200` 槽释放摘要 | `401`、`403`、`404`、`409` |
| `GET /admin/bookings/export?organizerKeyword?=&fromDate?=&toDate?=&status?=&date?` | ADMIN | 与管理员列表使用同一筛选；date 仅兼容且不得与 fromDate/toDate 并用 | `200 text/csv; charset=utf-8` | `400`、`401`、`403` |

管理员未开始预约的核心修改 body 必须完整：`version,roomId,subject,startTime,endTime,attendeeCount,participantsText,description,reason`。已开始未结束预约的核心修改 body 只允许：`version,subject,attendeeCount,participantsText,description,reason`；不得提交 `roomId,startTime,endTime`，否则 `409 BOOKING_STARTED_TIME_FIELDS_IMMUTABLE`。已结束不可作核心修改。管理员可使用普通 booking 的 supplemental-info 接口在任意状态修改三个补充字段，且不要求 reason；该操作仍写完整 UPDATE 审计。核心修改/取消他人必须填写原因；管理员也不能代订，且同样受未来 14 天约束。进行中取消规则同普通用户。

导出字段固定为预约号、会议室、预约人、主题、预计人数、参会人员、说明、起止时间、状态、取消时间、取消原因、创建时间、最后修改时间；以预约 `startTime` 落入闭区间筛选。使用 UTF-8，建议 UTF-8 BOM；文本单元格首个有效字符为 `=,+,-,@` 时加 `'` 防公式注入，再按 RFC 4180 正确转义逗号、引号和 CR/LF。

## 4. 创建预约严格幂等

`Idempotency-Key` 必须匹配 `[A-Za-z0-9._:-]{1,128}`（UUID/ULID 可用）。缺失为 `400 IDEMPOTENCY_KEY_REQUIRED`，非法为 `400 IDEMPOTENCY_KEY_INVALID`；均在领取幂等记录前返回。

处理顺序固定：HTTP 请求 → 解析 → 格式校验 → 构造规范化 `CreateBookingCommand` → 计算 SHA-256 `requestHash` → 幂等领取、业务校验和持久化。格式层失败（JSON、未知/缺失字段、类型或日期格式、Key）直接 4xx，不持久化 FAILED。

规范化命令只含 `roomId,subject,startTime,endTime,attendeeCount,participantsText,description`：可选缺失为 null；`subject` 非 null 时以 Java `String.strip()` 去首尾 Unicode 空白，`participantsText` 与 `description` 同样 `strip()` 且结果为空时规范化为 null，三者内部空格和换行保持不变；时间重格式化为固定 LocalDateTime 文本。键名字典序、UTF-8、无额外空白 JSON 计算 Hash。该同一命令必须用于哈希、业务校验和入库，不得重新读取原始 DTO。

`participantsText` 的业务输入上限为 2000 个 Java 字符，`description` 为 4000 个 Java 字符。它们是 HTTP/API 业务边界，不等同于 MySQL `TEXT` 在 `utf8mb4` 下的理论字节容量；超限属于格式层校验错误，必须在领取幂等记录前以 `400 REQUEST_VALIDATION_ERROR` 返回。

短事务先提交 `(CREATE_BOOKING,CurrentUser.id,key)` 的 PROCESSING；业务事务 `SELECT ... FOR UPDATE` 锁定它，写 booking、全部 booking_slot、审计并写 SUCCEEDED 及稳定首次响应；确定性业务失败回滚后以独立短事务写 FAILED；基础设施异常保留 PROCESSING，由恢复任务在取得行锁后终结。`response_body` 只保存稳定业务内容、状态码、失败码/bookingId，不保存 requestId、displayStatus 或当前时间。

| 情况 | HTTP / 结果 |
| --- | --- |
| 首次成功 | `201`，SUCCEEDED，稳定响应（含容量 warning） |
| 同 Key 同 Hash 成功 | 原首次 `201` 和原稳定内容，不新建 |
| 同 Key 同 Hash 失败 | 原首次失败状态和稳定错误，不重执行业务 |
| 同 Key 不同 Hash | `409 IDEMPOTENCY_KEY_REUSED` |
| 同 Key PROCESSING | `202 IDEMPOTENCY_PROCESSING`，不得自行再次创建 |

已形成合法命令后的会议室不存在 `404 MEETING_ROOM_NOT_FOUND`、停用 `409 MEETING_ROOM_DISABLED`、窗口 `422 BOOKING_WINDOW_EXCEEDED`、时间 `422 BOOKING_TIME_INVALID`、跨日 `422 BOOKING_CROSS_DAY_NOT_ALLOWED`、超 5 小时 `422 BOOKING_DURATION_EXCEEDED`、槽冲突 `409 BOOKING_SLOT_CONFLICT` 均须持久化 FAILED。网络超时不能视为失败，客户端必须用原 Key 查询，结果未知时不得更换 Key 重提。

结果查询固定以 `(operation_type=CREATE_BOOKING,user_id=CurrentUser.id,idempotency_key=Header)` 查询，客户端不传 operationType：PROCESSING 返回 `202 {status:"PROCESSING",...}`；SUCCEEDED 返回 `200 {status:"SUCCEEDED",originalHttpStatus:201,response:{...}}`；FAILED 返回 `200 {status:"FAILED",originalHttpStatus,failureCode,response:{...}}`；不存在、过期或清理返回 `404 IDEMPOTENCY_RESULT_NOT_FOUND_OR_EXPIRED`。重放时重新生成当前 HTTP requestId；若错误体有 requestId，也用当前值。回滚与 FAILED 写入间只可重查或 202，不能重执行业务。

## 5. 错误码

`REQUEST_BODY_INVALID`、`REQUEST_VALIDATION_ERROR`（400）；`IDEMPOTENCY_KEY_REQUIRED`、`IDEMPOTENCY_KEY_INVALID`（400）；`UNAUTHENTICATED`（401）；`FORBIDDEN`、`BOOKING_ACCESS_DENIED`（403）；`MEETING_ROOM_NOT_FOUND`、`BOOKING_NOT_FOUND`、`IDEMPOTENCY_RESULT_NOT_FOUND_OR_EXPIRED`（404）；`MEETING_ROOM_NAME_CONFLICT`、`MEETING_ROOM_DISABLED`、`BOOKING_SLOT_CONFLICT`、`BOOKING_VERSION_CONFLICT`、`BOOKING_ALREADY_CANCELLED`、`BOOKING_ALREADY_STARTED`、`BOOKING_STARTED_TIME_FIELDS_IMMUTABLE`、`BOOKING_ALREADY_ENDED`、`IDEMPOTENCY_KEY_REUSED`（409）；`IDEMPOTENCY_PROCESSING`（202）；`BOOKING_WINDOW_EXCEEDED`、`BOOKING_TIME_INVALID`、`BOOKING_CROSS_DAY_NOT_ALLOWED`、`BOOKING_DURATION_EXCEEDED`（422）；`INTERNAL_ERROR`（500）；`SERVICE_UNAVAILABLE`（503）。

## 6. 不在本基线实现的能力

本 API 基线不代表已实现：正式中大 SSO、Spring Security、WebSocket 实时刷新、代订、复杂报表/自定义导出、预约写入的具体 Java 代码均按后续阶段实施。日程在日期切换、创建/修改/取消成功和冲突后重新获取。

## 7. V1.1 契约变更

本文完整保留 V1.0 契约；以下为最终覆盖规则。`GET /schedules?date=` 的 bookings[] 增加必返 `isMine:boolean`，服务端按 CurrentUser.userId 判断。

`GET /me/bookings?page=&size=&status?=&date?` 新增 status/date；status 仅 `UPCOMING|IN_PROGRESS|ENDED|CANCELLED`，date 为 YYYY-MM-DD。管理员 V1.1.1 使用 `GET /admin/bookings?page=&size=&organizerKeyword?=&fromDate?=&toDate?=&status?`；organizerKeyword 对 displayName 模糊匹配且 trim 后空值视为未传。fromDate/toDate 分别为开始日下界和结束日上界，均按 Asia/Shanghai 自然日解释；两者同时提供时 fromDate 不得晚于 toDate。两接口全部先筛选后分页。

`GET /admin/bookings/export?organizerKeyword?=&fromDate?=&toDate?=&status?=&date?` 导出全部命中筛选数据，不接受 page/size，并与管理员列表使用同一筛选。V1.1.1 新客户端必须使用 organizerKeyword/fromDate/toDate/status；date 保留兼容。date 与任一 fromDate/toDate 同时出现必须 400 REQUEST_VALIDATION_ERROR。

`POST /bookings` Request DTO 不增加字段。startTime 最早允许服务端处理时刻向下取整到 30 分钟边界，处理时重新校验；早于该边界返回 BOOKING_TIME_INVALID。
