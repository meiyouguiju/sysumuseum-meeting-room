# 会议室预约系统前端 REST API 契约矩阵 V1.0

文档版本：V1.0  
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
| 9 | GET | `/me/bookings?page=&size=` | 我的预约 |
| 10 | POST | `/admin/rooms` | 新增会议室 |
| 11 | PATCH | `/admin/rooms/{roomId}` | 修改会议室 |
| 12 | POST | `/admin/rooms/{roomId}/enable` | 启用会议室 |
| 13 | POST | `/admin/rooms/{roomId}/disable` | 停用会议室 |
| 14 | GET | `/admin/bookings?page=&size=` | 管理员预约列表 |
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

['my-bookings', page, size]

['admin-bookings', page, size]
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

`ScheduleBooking` 当前没有：

```text
organizerUserId
isMine
```

因此前端：

> 不得通过 organizerName 判断是不是自己的预约。

V1 日程不显示“我的预约”标签。

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
GET /api/v1/me/bookings?page=&size=
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
['my-bookings', page, size]
```

---

# 22. 我的预约 UI 限制

API 不支持：

```text
status
fromDate
toDate
```

等筛选参数。

因此 V1：

> 使用单一分页列表。

不得用当前页数据实现：

```text
即将开始
进行中
历史
```

三个伪完整 Tab。

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
GET /api/v1/admin/bookings?page=&size=
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
['admin-bookings', page, size]
```

---

# 29. 管理员预约列表 UI 限制

API 当前不支持：

```text
keyword
roomId
status
fromDate
toDate
```

因此管理员预约列表 V1：

> 不做筛选栏。

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
GET /api/v1/admin/bookings/export
```

可选：

```text
fromDate
toDate
```

例如：

```http
GET /api/v1/admin/bookings/export?fromDate=2026-08-01&toDate=2026-08-31
```

如果省略：

```text
默认为当天
```

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

getMyBookings(page, size)

updateMyBooking(bookingId, request)

cancelMyBooking(bookingId, request)

getAdminBookings(page, size)

updateAdminBooking(bookingId, request)

cancelAdminBooking(bookingId, request)

exportAdminBookings(fromDate?, toDate?)

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

# 54. 前后端映射发现的三个正式产品修正

## 修正 1：日程不显示“我的预约”标签

原因：

ScheduleBooking 当前没有：

```text
organizerUserId
isMine
```

不能用 organizerName 判断本人。

结论：

> V1 不修改后端，调整前端。

---

## 修正 2：我的预约不做三个筛选 Tab

原因：

```text
GET /me/bookings
```

只支持：

```text
page
size
```

前端无法对全部服务器数据正确分组。

结论：

> V1 使用单一分页列表 + 每条状态。

---

## 修正 3：管理员预约列表不做筛选栏

原因：

```text
GET /admin/bookings
```

仅支持：

```text
page
size
```

结论：

> V1 不做日期、会议室、状态、关键字筛选。

CSV 日期范围是独立接口能力，可以保留。

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

不得自行添加请求参数：

```text
status
keyword
roomId filter
organizerId
department
sort
```

除非后端正式基线已经定义。

不得向现有 Request 添加：

```text
actorUserId
organizerUserId
role
isAdmin
```

---

# 64. V1 不新增 API

除非经过重新设计评审，Frontend F0～F5 应优先在现有 17 个 API 范围内完成。

如果发现某个交互无法实现：

1. 先检查是否是前端设计问题；
2. 优先调整前端；
3. 确认确实属于必要业务能力缺口后，再讨论是否修改后端基线；
4. Codex 不得自行新增后端 Endpoint。

---

# 65. 最终基线原则

本文和：

```text
会议室预约系统前端产品与交互设计-V1.0.md
```

共同组成 Frontend V1.0 基线。

后续 Codex 每个阶段都应先阅读：

```text
AGENTS.md
MVP业务规则
REST API V1.0
前端产品与交互 V1.0
前端 REST API 契约矩阵 V1.0
coding-standards
```

再开始实现。

若代码行为与本文冲突，应优先修正代码，而不是默默修改本文。