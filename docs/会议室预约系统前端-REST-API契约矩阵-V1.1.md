# 会议室预约系统前端 REST API 契约矩阵 V1.1

文档版本：V1.1  
状态：正式基线  
适用项目：中山大学校史馆会议室预约系统  
后端基线：`backend-mvp-v1`  
REST 前缀：`/api/v1`

---

# 1. 文档目的

本文用于明确前端页面、用户操作和已冻结 REST API 之间的对应关系。

目标：

1. 防止前端自行发明 API；
2. 防止为了 UI 需求修改已冻结后端契约；
3. 明确每个页面的数据来源；
4. 明确 Query Key；
5. 明确 Mutation 后需要刷新哪些 Server State；
6. 明确权限和错误处理边界。

---

# 2. 通用 REST 约定

统一前缀：

```text
/api/v1
```

JSON：

```text
camelCase
```

成功响应：

```text
直接返回资源 / 集合
```

不包裹：

```json
{
  "data": ...
}
```

业务日期：

```text
YYYY-MM-DD
```

业务日期时间：

```text
YYYY-MM-DDTHH:mm:ss
```

全部按照：

```text
Asia/Shanghai
```

解释。

客户端不得传：

```text
organizerUserId
actorUserId
roleCode
admin
```

身份来自后端 CurrentUser。

---

# 3. 后端统一错误模型

```json
{
  "errorCode": "...",
  "message": "...",
  "fieldErrors": [],
  "requestId": "req_..."
}
```

前端建议统一类型：

```text
ApiErrorResponse
ApiFieldError
```

前端不应只依赖 HTTP Status。

业务行为应结合：

```text
HTTP Status + errorCode
```

判断。

---

# 4. 17 个 API 总目录

| # | Method | Path | 主要用途 |
|---|---|---|---|
| 1 | GET | `/me` | 当前用户 |
| 2 | GET | `/rooms` | 全部会议室 |
| 3 | GET | `/schedules?date=` | 某日完整日程 |
| 4 | POST | `/bookings` | 创建预约 |
| 5 | GET | `/bookings/idempotency-result` | 创建幂等结果 |
| 6 | GET | `/bookings/{bookingId}` | 完整预约详情 |
| 7 | PATCH | `/bookings/{bookingId}` | 用户修改本人预约 |
| 8 | POST | `/bookings/{bookingId}/cancel` | 用户取消本人预约 |
| 9 | GET | `/me/bookings?page=&size=&status?=&date?` | 我的预约服务端筛选 |
| 10 | POST | `/admin/rooms` | 新增会议室 |
| 11 | PATCH | `/admin/rooms/{roomId}` | 修改会议室 |
| 12 | POST | `/admin/rooms/{roomId}/enable` | 启用会议室 |
| 13 | POST | `/admin/rooms/{roomId}/disable` | 停用会议室 |
| 14 | GET | `/admin/bookings?page=&size=&organizerKeyword?=&date?=&status?` | 管理员预约列表服务端筛选 |
| 15 | PATCH | `/admin/bookings/{bookingId}` | 管理员修改预约 |
| 16 | POST | `/admin/bookings/{bookingId}/cancel` | 管理员取消预约 |
| 17 | GET | `/admin/bookings/export` | CSV 导出 |

---

# 5. Query Key 基线

前端建议固定使用：

```text
['current-user']

['rooms']

['schedule', date]

['booking-detail', bookingId]

['my-bookings', page, size, status, date]

['admin-bookings', page, size, organizerKeyword, fromDate, toDate, status]
```

不要使用无业务含义的：

```text
query1
listData
tableData
bookingData
```

---

# 6. 当前用户

## 页面

全局 AppLayout、导航、管理员路由。

## 前端动作

应用启动时获取当前用户。

## API

```http
GET /api/v1/me
```

## Request

无。

## Response

```text
{
  id,
  displayName,
  departmentName,
  roleCode,
  status
}
```

## Query Key

```text
['current-user']
```

## 前端用途

- 顶部显示用户名；
- 显示部门；
- 判断是否展示管理员菜单；
- Router Guard；
- 判断管理员修改的预约是否属于自己。

## 注意

前端不得硬编码：

```text
isAdmin = true
```

身份以后端为准。

---

# 7. 会议室列表

## 页面

- 日程；
- 创建预约；
- 修改预约；
- 管理员会议室管理。

## API

```http
GET /api/v1/rooms
```

## Response

```text
Room[]:
{
  id,
  name,
  location,
  capacity,
  facilitiesText,
  usageNotice,
  status,
  sortOrder
}
```

status：

```text
ENABLED
DISABLED
```

排序：

```text
sortOrder ASC
id ASC
```

## Query Key

```text
['rooms']
```

## 注意

不得在 API Client 层过滤 DISABLED。

停用会议室需要在日程和管理页继续显示。

---

# 8. 日程查询

## 页面

```text
/schedule
```

桌面和手机共用。

## API

```http
GET /api/v1/schedules?date=YYYY-MM-DD
```

## Request

```text
date
```

必填。

## Response

```text
{
  date,
  timeZone: "Asia/Shanghai",
  slotMinutes: 30,
  focusWindow: {
    start: "08:30",
    end: "17:30"
  },
  rooms,
  bookings,
  unavailableSlots
}
```

## Query Key

```text
['schedule', date]
```

## 前端行为

一次请求整个日期。

不得按 30 分钟格逐个请求。

---

# 9. ScheduleBooking

公共预约摘要：

```text
{
  id,
  roomId,
  subject,
  organizerName,
  isMine,
  startTime,
  endTime,
  displayStatus
}
```

displayStatus：

```text
UPCOMING
IN_PROGRESS
ENDED
```

不返回：

```text
participantsText
description
cancelReason
联系方式
```

公共日程不显示已取消预约。

---

# 10. 日程中的本人判断限制

`ScheduleBooking` 不返回 `organizerUserId`，但必须返回 `isMine:boolean`。前端禁止通过 organizerName 判断本人，只能使用 isMine。

因此前端：

> 不得通过 organizerName 判断是不是自己的预约。

日程使用 ScheduleBooking.isMine 区分本人预约；isMine=true 才允许调用完整详情并复用 F3 修改/取消。

---

# 11. unavailableSlots

结构：

```text
{
  roomId,
  slotStart,
  reason
}
```

当前：

```text
reason = "CANCELLED_CURRENT_SLOT_HOLD"
```

用于表示进行中预约取消后仍保留的当前 30 分钟槽。

前端：

```text
显示“暂不可预约”
```

不得显示原预约主题。

---

# 12. 日程日期范围

日程：

- 历史日期允许查看；
- 今天允许查看；
- 未来最大到 today + 13。

超过未来范围：

```text
400 REQUEST_VALIDATION_ERROR
```

前端 DatePicker 可以提前禁用未来超限日期。

但查看历史日程不能一并禁用。

---

# 13. 创建预约

## 页面动作

用户点击空闲槽。

## API

```http
POST /api/v1/bookings
```

Header：

```text
Idempotency-Key: <key>
```

## Request

```text
{
  roomId,
  subject,
  startTime,
  endTime,
  attendeeCount?,
  participantsText?,
  description?
}
```

客户端不得传 organizer。

---

# 14. 创建预约 Response

稳定成功响应包含：

```text
id
bookingNo
room
organizer
subject
attendeeCount
participantsText
description
startTime
endTime
status
version
created/occurred time
warnings
```

可能 Warning：

```text
ROOM_CAPACITY_EXCEEDED
```

容量超限：

> 只警告，不禁止创建。

---

# 15. 创建预约 Query / Mutation 关系

Mutation：

```text
createBooking
```

成功后失效：

```text
['schedule', bookingDate]
['my-bookings', ...]
```

实际实现可通过 prefix invalidation 使所有 MyBookings 页失效。

---

# 16. 创建预约严格幂等

`Idempotency-Key`：

- 必填；
- 同一次创建流程必须复用；
- 网络超时不能换 Key；
- Key 不得用于不同 Request Body。

关键错误：

```text
IDEMPOTENCY_KEY_REQUIRED
IDEMPOTENCY_KEY_INVALID
IDEMPOTENCY_KEY_REUSED
IDEMPOTENCY_PROCESSING
```

---

# 17. 创建幂等结果查询

## API

```http
GET /api/v1/bookings/idempotency-result
Idempotency-Key: <原Key>
```

## 情况 A

```text
202
status = PROCESSING
```

前端：

继续保留创建流程状态。

不得重新创建。

## 情况 B

```text
200
status = SUCCEEDED
originalHttpStatus = 201
response = ...
```

前端：

视为创建成功。

## 情况 C

```text
200
status = FAILED
failureCode = ...
response = ...
```

前端：

按照原业务失败处理。

## 情况 D

```text
404 IDEMPOTENCY_RESULT_NOT_FOUND_OR_EXPIRED
```

前端提示无法确认原请求状态。

不得擅自假设成功或失败。

---

# 18. 创建预约主要业务错误

```text
404 MEETING_ROOM_NOT_FOUND

409 MEETING_ROOM_DISABLED
409 BOOKING_SLOT_CONFLICT
409 IDEMPOTENCY_KEY_REUSED

422 BOOKING_WINDOW_EXCEEDED
422 BOOKING_TIME_INVALID
422 BOOKING_CROSS_DAY_NOT_ALLOWED
422 BOOKING_DURATION_EXCEEDED
```

冲突发生后：

```text
invalidate ['schedule', date]
```

表单其它字段保留。

---

# 19. 完整预约详情

## API

```http
GET /api/v1/bookings/{bookingId}
```

## 权限

仅：

```text
预约本人
ADMIN
```

普通用户查看他人：

```text
403 BOOKING_ACCESS_DENIED
```

## Response

BookingDetail 包含：

```text
id / bookingNo
room
organizer
subject
attendeeCount
participantsText
description
startTime
endTime
status
displayStatus
version
cancelledAt
取消信息
审计时间字段
```

## Query Key

```text
['booking-detail', bookingId]
```

---

# 20. 公共预约详情策略

普通用户点击他人预约：

> 不调用 `/bookings/{id}`。

使用 ScheduleBooking 本地公共摘要展示。

管理员或本人查看完整信息时才调用 BookingDetail API。

---

# 21. 我的预约

## 页面

```text
/my-reservations
```

## API

```http
GET /api/v1/me/bookings?page=&size=&status?=&date?=
```

默认：

```text
page = 1
size = 20
```

最大：

```text
size = 100
```

固定排序：

```text
startTime DESC
id DESC
```

## Response

```text
{
  items,
  page,
  size,
  total,
  totalPages
}
```

## Query Key

```text
['my-bookings', page, size, status, date]
```

---

# 22. 我的预约 UI 限制

V1.1 支持：

```text
status
date
```

`GET /me/bookings` 的正式筛选参数只有 status/date。status 只能为 `UPCOMING|IN_PROGRESS|ENDED|CANCELLED`，date 为 `YYYY-MM-DD`。服务端必须先筛选后分页；条件变化时 page 必须重置为 1。

因此 V1.1：

> 使用单一分页列表。

不得用当前页数据实现：

```text
即将开始
进行中
历史
```

三个伪完整 Tab。

不允许对当前页 items 做伪完整筛选。

---

# 23. 普通用户修改预约

## API

```http
PATCH /api/v1/bookings/{bookingId}
```

## 权限

```text
本人
ACTIVE
未开始
```

## Request

必须提交完整字段：

```text
{
  version,
  roomId,
  subject,
  startTime,
  endTime,
  attendeeCount,
  participantsText,
  description
}
```

不是部分 PATCH。

## 成功

```text
200 BookingDetail
```

---

# 24. 普通用户修改主要错误

```text
403 BOOKING_ACCESS_DENIED
404 BOOKING_NOT_FOUND

409 BOOKING_VERSION_CONFLICT
409 BOOKING_ALREADY_STARTED
409 BOOKING_ALREADY_CANCELLED
409 BOOKING_ALREADY_ENDED
409 MEETING_ROOM_DISABLED
409 BOOKING_SLOT_CONFLICT

422 BOOKING_WINDOW_EXCEEDED
422 BOOKING_TIME_INVALID
422 BOOKING_CROSS_DAY_NOT_ALLOWED
422 BOOKING_DURATION_EXCEEDED
```

## 补充信息

`PATCH /api/v1/bookings/{bookingId}/supplemental-info` 只发送：

```text
version
attendeeCount
participantsText
description
```

禁止发送或修改 `roomId`、`subject`、`startTime`、`endTime`。预约创建者本人和 ACTIVE ADMIN 可在 `UPCOMING`、`IN_PROGRESS`、`ENDED`、`CANCELLED` 任意状态调用；ADMIN 修改他人不要求 reason。成功后必须失效当前详情、所有 `my-bookings` 和 `admin-bookings` 查询；不必失效公共 Schedule。该操作使用 `BOOKING_VERSION_CONFLICT`，并写 reason/slot_change_json 均为 NULL 的 UPDATE 审计。

---

# 25. 普通用户修改刷新范围

修改前保存：

```text
oldDate
```

修改成功获得：

```text
newDate
```

失效：

```text
['booking-detail', bookingId]

所有 ['my-bookings', ...]

['schedule', oldDate]

如果 newDate != oldDate：
['schedule', newDate]
```

---

# 26. 普通用户取消预约

## API

```http
POST /api/v1/bookings/{bookingId}/cancel
```

## Request

```text
{
  version,
  reason?
}
```

## 权限

```text
本人
ACTIVE
尚未结束
```

## Response

```text
{
  id,
  status,
  version,
  cancelledAt,
  slotRelease
}
```

---

# 27. 普通用户取消刷新

失效：

```text
['booking-detail', bookingId]
所有 ['my-bookings', ...]
['schedule', bookingDate]
```

进行中预约取消后：

Schedule 会通过 unavailableSlots 正确返回当前保留槽。

前端无需手工计算 Slot。

---

# 28. 管理员预约列表

## 页面

```text
/admin/reservations
```

## API

```http
GET /api/v1/admin/bookings?page=&size=&organizerKeyword?=&fromDate?=&toDate?=&status?=
```

## 权限

ADMIN。

## Response

管理员预约摘要分页：

```text
{
  items,
  page,
  size,
  total,
  totalPages
}
```

固定排序：

```text
startTime DESC
id DESC
```

## Query Key

```text
['admin-bookings', page, size, organizerKeyword, fromDate, toDate, status]
```

---

# 29. 管理员预约列表 UI 限制

V1.1 管理员预约正式支持：

```text
organizerKeyword
date
status
```

organizerKeyword 为预约人 displayName 关键词，date 为预约 startTime 所属自然日，status 只能为 `UPCOMING|IN_PROGRESS|ENDED|CANCELLED`。服务端必须先筛选后分页；不提供 roomId、主题、department、任意排序等未定义筛选。

因此管理员预约列表 V1.1：

> 必须提供 organizerKeyword、date、status 服务端筛选栏。

只做：

```text
分页
查看
修改
取消
CSV
```

---

# 30. 管理员预约详情

管理员不使用独立详情 API。

直接：

```http
GET /api/v1/bookings/{bookingId}
```

管理员有读取任意 BookingDetail 权限。

---

# 31. 管理员修改预约

## API

```http
PATCH /api/v1/admin/bookings/{bookingId}
```

## 权限

ADMIN。

---

# 32. 管理员修改未开始预约

Request 必须完整：

```text
{
  version,
  roomId,
  subject,
  startTime,
  endTime,
  attendeeCount,
  participantsText,
  description,
  reason
}
```

如果预约属于其他用户：

```text
reason 必填
```

如果预约属于管理员本人：

```text
reason 可选
```

管理员不能修改 organizer。

管理员不能代订。

---

# 33. 管理员修改进行中预约

Request 只允许：

```text
{
  version,
  subject,
  attendeeCount,
  participantsText,
  description,
  reason
}
```

不得提交：

```text
roomId
startTime
endTime
```

即使值与数据库相同也不能提交。

否则：

```text
409 BOOKING_STARTED_TIME_FIELDS_IMMUTABLE
```

前端应定义独立 Request Type。

例如：

```text
AdminUpcomingBookingUpdateRequest
AdminInProgressBookingUpdateRequest
```

---

# 34. 管理员修改不可用状态

已结束：

```text
409 BOOKING_ALREADY_ENDED
```

已取消：

```text
409 BOOKING_ALREADY_CANCELLED
```

Version 不匹配：

```text
409 BOOKING_VERSION_CONFLICT
```

---

# 35. 管理员修改刷新

成功后：

```text
invalidate:
所有 ['admin-bookings', ...]
['booking-detail', bookingId]
所有 ['my-bookings', ...]
['schedule', oldDate]

如果日期变化：
['schedule', newDate]
```

---

# 36. 管理员取消预约

## API

```http
POST /api/v1/admin/bookings/{bookingId}/cancel
```

## Request

```text
{
  version,
  reason
}
```

如果取消他人预约：

```text
reason 必填
```

管理员自己的预约可按后端当前规则处理。

## 状态

进行中：

允许取消。

已结束：

不允许。

---

# 37. 管理员取消刷新

```text
invalidate:
所有 ['admin-bookings', ...]
['booking-detail', bookingId]
所有 ['my-bookings', ...]
['schedule', bookingDate]
```

---

# 38. CSV 导出

## 页面

管理员预约管理。

## API

```http
GET /api/v1/admin/bookings/export?organizerKeyword?=&date?=&status?=
```

V1.1 前端只发送：

```text
organizerKeyword
date
status
```

CSV 导出全部命中数据，不受 page/size 影响。

### 后端兼容

`fromDate/toDate` 仅为后端兼容参数，V1.1 前端不得发送；date 与任一 fromDate/toDate 同时出现必须返回 400。

## Response

```text
text/csv; charset=utf-8
```

前端使用 Blob 下载。

---

# 39. CSV 固定字段

CSV 后端字段固定：

```text
预约号
会议室
预约人
主题
预计人数
参会人员
说明
起止时间
状态
取消时间
取消原因
创建时间
最后修改时间
```

前端不得自行把当前表格分页转成 CSV。

---

# 40. 管理员会议室列表

## 页面

```text
/admin/rooms
```

## 查询 API

复用：

```http
GET /api/v1/rooms
```

不需要管理员专用会议室查询。

---

# 41. 新增会议室

## API

```http
POST /api/v1/admin/rooms
```

## Request

必填：

```text
name
location
capacity
```

可选：

```text
facilitiesText
usageNotice
sortOrder
```

## Response

```text
201 Room
```

## 主要错误

```text
400
401
403
409 MEETING_ROOM_NAME_CONFLICT
```

---

# 42. 新增会议室刷新

成功：

```text
invalidate ['rooms']
invalidate 当前已缓存 schedule
```

如果实现方便，可以使所有：

```text
['schedule', ...]
```

失效。

---

# 43. 修改会议室

## API

```http
PATCH /api/v1/admin/rooms/{roomId}
```

可修改：

```text
name
location
capacity
facilitiesText
usageNotice
sortOrder
```

不能通过此 API 修改状态。

---

# 44. 修改会议室刷新

```text
invalidate ['rooms']
invalidate 所有 ['schedule', ...]
```

---

# 45. 启用会议室

## API

```http
POST /api/v1/admin/rooms/{roomId}/enable
```

无 Body。

Response：

```text
200 Room
```

成功：

```text
invalidate ['rooms']
invalidate 所有 ['schedule', ...]
```

---

# 46. 停用会议室

## API

```http
POST /api/v1/admin/rooms/{roomId}/disable
```

无 Body。

Response：

```text
200 Room
```

成功：

```text
invalidate ['rooms']
invalidate 所有 ['schedule', ...]
```

---

# 47. 停用后的行为

前端必须接受：

```text
Room.status = DISABLED
```

并继续显示该会议室。

后端不会：

- 删除历史预约；
- 自动取消未来预约。

前端不得因为 DISABLED 隐藏整行。

---

# 48. Mutation 刷新矩阵

| Mutation | BookingDetail | MyBookings | AdminBookings | Rooms | Schedule |
|---|---:|---:|---:|---:|---:|
| 创建预约 | 否 | 是 | 可选/管理员页面存在时可失效 | 否 | 是 |
| 用户修改 | 是 | 是 | 可选 | 否 | 旧/新日期 |
| 用户取消 | 是 | 是 | 可选 | 否 | 是 |
| 管理员修改 | 是 | 是 | 是 | 否 | 旧/新日期 |
| 管理员取消 | 是 | 是 | 是 | 否 | 是 |
| 新增会议室 | 否 | 否 | 否 | 是 | 是 |
| 修改会议室 | 否 | 否 | 否 | 是 | 是 |
| 启用会议室 | 否 | 否 | 否 | 是 | 是 |
| 停用会议室 | 否 | 否 | 否 | 是 | 是 |

原则：

> 优先正确刷新，不为了减少一个 GET 引入复杂前端状态同步。

---

# 49. 推荐 API Client 函数

```text
getCurrentUser()

getRooms()

getSchedule(date)

createBooking(request, idempotencyKey)

getBookingCreationResult(idempotencyKey)

getBookingDetail(bookingId)

getMyBookings(page, size, status?, date?)

updateMyBooking(bookingId, request)

cancelMyBooking(bookingId, request)

getAdminBookings(
page,
size,
organizerKeyword?,
date?,
status?,
)

updateAdminBooking(bookingId, request)

cancelAdminBooking(bookingId, request)

exportAdminBookings(
organizerKeyword?,
date?,
status?,
)

createRoom(request)

updateRoom(roomId, request)

enableRoom(roomId)

disableRoom(roomId)
```

---

# 50. 推荐文件结构

```text
src/api/

http.ts
currentUser.ts
schedule.ts
bookings.ts
rooms.ts

admin/
  bookings.ts
  rooms.ts
```

建议：

```text
src/types/
```

维护明确的 Request / Response Type。

---

# 51. HTTP Client 原则

统一使用 Axios instance。

开发环境调用相对地址：

```text
/api/v1/...
```

由 Vite proxy：

```text
/api → http://localhost:8080
```

禁止在业务文件硬编码：

```text
http://localhost:8080
```

---

# 52. API Error 类型

推荐：

```text
ApiErrorResponse
{
  errorCode: string
  message: string
  fieldErrors: ApiFieldError[]
  requestId: string
}
```

错误处理分层：

## 通用层

处理：

```text
网络错误
500
503
401
```

## 页面业务层

识别：

```text
BOOKING_SLOT_CONFLICT
BOOKING_VERSION_CONFLICT
MEETING_ROOM_DISABLED
BOOKING_ALREADY_STARTED
BOOKING_ALREADY_ENDED
BOOKING_ALREADY_CANCELLED
BOOKING_STARTED_TIME_FIELDS_IMMUTABLE
BOOKING_WINDOW_EXCEEDED
BOOKING_TIME_INVALID
BOOKING_CROSS_DAY_NOT_ALLOWED
BOOKING_DURATION_EXCEEDED
```

---

# 53. 重点错误处理建议

## BOOKING_SLOT_CONFLICT

```text
刷新 Schedule
保留表单
提示重新选择
```

## BOOKING_VERSION_CONFLICT

```text
提示数据已变化
重新获取 BookingDetail
重新获取列表/日程
```

## MEETING_ROOM_DISABLED

```text
刷新 Rooms
刷新 Schedule
提示会议室已停用
```

## BOOKING_ALREADY_STARTED

普通用户：

```text
不再允许修改
刷新详情
```

## BOOKING_STARTED_TIME_FIELDS_IMMUTABLE

管理员进行中修改：

```text
说明当前预约已开始，不能修改会议室或时间
```

## 401 UNAUTHENTICATED

F0～F5：

```text
显示认证不可用 / 会话不可用
```

F6 SSO 接入后再正式做登录重定向。

---

# 54. V1.1 前后端映射的正式产品结论

## 54.1 日程中的本人预约

`ScheduleBooking` 必须返回：

```text
isMine:boolean
```

后端必须依据 `CurrentUser.userId` 与预约所属用户的真实 ID 判断 `isMine`。

`ScheduleBooking` 不返回：

```text
organizerUserId
```

前端不得通过：

```text
organizerName == currentUser.displayName
```

判断本人预约。

当：

```text
isMine = true
```

前端必须：

- 使用专属样式和明确的“我的预约”文字/标识；
- 允许进入本人完整 `BookingDetail`；
- 复用既有 F3 修改、取消、`version` snapshot、版本冲突提示和 Query invalidation 逻辑。

当：

```text
isMine = false
```

前端只能展示公共摘要，不得请求或展示参会人员、说明等私有信息，也不得提供本人修改/取消操作。

管理员在公共日程中同样只将其本人预约视为 `isMine=true`；管理其他用户预约继续进入管理员预约管理页面。

---

## 54.2 我的预约

正式 API：

```http
GET /api/v1/me/bookings?page=&size=&status?=&date?=
```

支持：

```text
page
size
status?
date?
```

其中：

```text
status = UPCOMING | IN_PROGRESS | ENDED | CANCELLED
date = YYYY-MM-DD
```

`date` 按 `Asia/Shanghai` 表示预约 `startTime` 所属自然日。

筛选必须由服务端在分页之前执行。筛选条件变化时，前端必须：

```text
page = 1
```

页面继续采用单一分页列表/卡片结构，可以提供状态和日期筛选，但不得仅对当前页 `items` 做伪完整筛选，也不得用当前页数据模拟“即将开始 / 进行中 / 历史”等完整数据 Tab。

---

## 54.3 管理员预约与 CSV

正式管理员列表 API：

```http
GET /api/v1/admin/bookings?page=&size=&organizerKeyword?=&date?=&status?=
```

支持：

```text
page
size
organizerKeyword?
fromDate?
toDate?
status?
```

其中：

- `organizerKeyword`：预约人 `displayName` 关键词；
- `fromDate` / `toDate`：预约 `startTime` 的 Asia/Shanghai 自然日范围；单独提供任一边界时分别表示无上界或无下界。
- `status`：`UPCOMING | IN_PROGRESS | ENDED | CANCELLED`。

筛选必须由服务端在分页之前执行。

正式 CSV API：

```http
GET /api/v1/admin/bookings/export?organizerKeyword?=&fromDate?=&toDate?=&status?=
```

V1.1 前端直接使用当前管理员列表的：

```text
organizerKeyword
fromDate
toDate
status
```

作为导出条件。

CSV 必须导出所有符合当前筛选条件的记录，不受：

```text
page
size
```

影响。

`date` 仅作为后端兼容参数保留；V1.1.1 前端不得发送。`date` 与任一 `fromDate/toDate` 同时提供时，服务端必须返回参数校验错误；fromDate 晚于 toDate 同样必须返回参数校验错误。

---

# 55. F0 接口范围

Frontend F0 只允许真正对接：

```text
GET /api/v1/me
GET /api/v1/rooms
```

其它 API 暂不实现。

---

# 56. F1 接口范围

Frontend F1：

```text
GET /api/v1/schedules?date=
```

以及根据 UI 权限需求使用：

```text
GET /api/v1/bookings/{id}
```

但普通用户点击他人预约不得调用 BookingDetail。

---

# 57. F2 接口范围

Frontend F2：

```text
POST /api/v1/bookings

GET /api/v1/bookings/idempotency-result
```

---

# 58. F3 接口范围

Frontend F3：

```text
GET /api/v1/me/bookings

GET /api/v1/bookings/{id}

PATCH /api/v1/bookings/{id}

POST /api/v1/bookings/{id}/cancel
```

---

# 59. F4 接口范围

Frontend F4：

```text
GET /api/v1/admin/bookings

PATCH /api/v1/admin/bookings/{id}

POST /api/v1/admin/bookings/{id}/cancel

GET /api/v1/admin/bookings/export

POST /api/v1/admin/rooms

PATCH /api/v1/admin/rooms/{id}

POST /api/v1/admin/rooms/{id}/enable

POST /api/v1/admin/rooms/{id}/disable
```

---

# 60. 安全边界

前端可以：

```text
隐藏按钮
禁用按钮
Router Guard
提前校验时间
```

但是最终必须依赖后端。

前端不能认为以下逻辑是安全控制：

```text
if currentUser.roleCode === 'ADMIN'
```

后端仍必须校验管理员权限。

同样：

```text
canEdit
canCancel
```

前端仅用于 UI。

---

# 61. Server State 原则

以下数据属于 Server State：

```text
CurrentUser
Rooms
Schedule
BookingDetail
MyBookings
AdminBookings
```

使用 TanStack Vue Query。

不得复制全部数据到 Pinia 后再人工同步。

---

# 62. Mutation 一致性原则

V1 默认不做复杂乐观更新。

统一：

```text
调用后端 Mutation
↓
成功
↓
invalidate
↓
重新 GET
```

后端数据库状态为最终事实来源。

---

# 63. 不允许前端自行扩展 REST 契约

前端只能使用正式 V1.1 REST 基线已经定义的 Query Parameter、Path Parameter、Header 和 Request Body 字段。

不得为了实现 UI 方便，自行增加未进入 V1.1 契约的参数，例如：

```text
roomId filter
organizerId
department
sort
任意未定义 keyword
其它未定义过滤字段
```

以下 V1.1 已正式定义的参数可以按对应接口使用：

```text
status
date
organizerKeyword
```

但不得把它们发送到未定义这些参数的接口。

不得向现有 Request Body 自行添加身份或权限字段：

```text
actorUserId
organizerUserId
role
isAdmin
```

身份、角色和预约归属始终以后端 `CurrentUser` 与服务端对象级权限判断为准。

---

# 64. V1.1 不新增额外 API

除非经过重新设计评审，V1.1 应优先在现有 17 个 API 及本版本已冻结的参数扩展范围内完成。

如果发现某个交互无法实现：

1. 先检查是否是前端设计或已有 API 使用方式的问题；
2. 优先复用现有正式契约；
3. 确认确实属于必要业务能力缺口后，再重新评审 REST 基线；
4. Codex 或开发人员不得自行新增 Endpoint 或未定义参数。

---

# 65. 最终基线原则

Frontend V1.1 的当前事实来源为：

```text
AGENTS.md
docs/coding-standards.md
docs/会议室预约系统MVP业务规则确认文档-V1.1.md
docs/会议室预约系统REST-API设计-V1.1.md
docs/会议室预约系统前端产品与交互设计-V1.1.md
docs/会议室预约系统前端-REST-API契约矩阵-V1.1.md
```

V1.0 文档继续保留作为历史基线和版本演进记录，但不是 V1.1 开发的直接事实来源。

后续 Codex 或开发人员在实现 V1.1 前，应优先阅读上述 V1.1 基线。

若代码行为与正式 V1.1 基线冲突，应优先修正代码；不得为了迎合现有实现而默默修改已冻结文档。

---

# 66. V1.1 契约矩阵最终规则

| 页面/功能 | API | V1.1 变化 |
| --- | --- | --- |
| 日程 | GET `/schedules` | bookings[].isMine 由服务端 user id 判断 |
| 日程本人详情/修改/取消 | GET/PATCH/POST `/bookings/{id}` | 仅 isMine=true；复用 F3 |
| 我的预约 | GET `/me/bookings` | status/date，先筛选后分页 |
| 创建预约 | POST `/bookings` | 当前 30 分钟槽允许，处理时重新校验 |
| 管理员列表 | GET `/admin/bookings` | organizerKeyword/fromDate/toDate/status |
| CSV | GET `/admin/bookings/export` | 同管理员筛选，导出全部命中数据 |
| CSV 日期 Dialog | 无 | V1.1 废弃 |

My Bookings Key 必须为 `['my-bookings', page, size, status, date]`；Admin Key 必须为 `['admin-bookings', page, size, organizerKeyword, fromDate, toDate, status]`。CSV 禁止 page/size，date 与 fromDate/toDate 不得同时发送。
