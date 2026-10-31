# 接口文档

- Base URL：`http://localhost:8080/api`
- 在线调试：启动后访问 `http://localhost:8080/doc.html`
- 认证：除 `/api/auth/login`、`/api/auth/register`、`/api/meta/**` 外的接口都需要请求头
  `Authorization: Bearer <token>`

## 统一响应

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1761900000000
}
```

| 字段 | 说明 |
| --- | --- |
| `code` | `0` 成功；其它为业务错误码（见下表） |
| `message` | 提示文案，前端可直接展示 |
| `data` | 业务数据，失败时为 `null` |

HTTP 状态码：`200` 业务处理完成（含业务失败）、`401` 未登录/Token 失效、`404` 路径不存在、`405` 方法不支持。

### 错误码

| code | 含义 |
| --- | --- |
| 1001 | 请求参数不合法 |
| 1002 | 请先登录 |
| 1003 | 没有权限 |
| 1004 | 资源不存在 |
| 1429 | 操作过于频繁 |
| 1500 | 服务器内部错误 |
| 2001 | 账号已被注册 |
| 2003 | 账号或密码错误 |
| 2004 | 账号已被禁用 |
| 2005 | 原密码不正确 |
| 3001 | 账单不存在 |
| 3002 | 无权操作他人账单 |
| 3003 | 分类不存在 |
| 3004 | 存在下级分类 |
| 3005 | 分类下还有账单 |
| 3006 | 系统内置分类不可删除 |
| 3007 | 分类类型与账单类型不一致 |
| 4001 | 预算不存在 |
| 4002 | 该月已存在同类预算 |
| 4004 | 当月无数据，无法生成报告 |
| 5001 | 没能理解这句话 |
| 5004 | 没有识别到金额 |

分页结构：

```json
{ "records": [], "total": 625, "page": 1, "size": 10, "pages": 63 }
```

---

## 1. 认证

### POST /auth/register

```json
{ "username": "alice", "password": "123456", "nickname": "爱丽丝" }
```

`username` 需以字母开头、3-20 位字母数字下划线；`password` 6-32 位。注册成功后自动初始化内置分类。

响应 `data`：`{ token, tokenType, expiresIn, user }`

### POST /auth/login

```json
{ "username": "demo", "password": "123456" }
```

### POST /auth/logout

当前 Token 进入 Redis 黑名单直到自然过期。

### GET /auth/me

返回当前用户 `{ id, username, nickname, avatar, email, phone, role, status, lastLogin, createdAt }`。

### PUT /users/profile

`{ nickname, email, phone, avatar }`

### PUT /users/password

`{ oldPassword, newPassword }`

---

## 2. 分类

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/categories?type=EXPENSE\|INCOME` | 分类树（一级带 `children`），`type` 不传返回全部 |
| GET | `/categories/flat?type=` | 平铺列表 |
| POST | `/categories` | 新增，body `{ name, type, parentId, icon, color, sort }` |
| PUT | `/categories/{id}` | 编辑 |
| DELETE | `/categories/{id}` | 删除（系统分类 / 有子分类 / 有账单 时拒绝） |

分类对象：

```json
{
  "id": 1, "parentId": 0, "name": "餐饮", "type": "EXPENSE",
  "icon": "food", "color": "#FF7A45", "sort": 10, "system": true,
  "billCount": 128, "children": []
}
```

`icon` 为语义化图标名，前端映射到具体图标组件：`food / transport / shopping / home / entertainment /
medical / education / gift / salary / parttime / invest / redpacket / refund / other`。

---

## 3. 账单

### GET /bills

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| page / size | int | 分页，size 上限 200 |
| startDate / endDate | yyyy-MM-dd | 日期区间 |
| type | EXPENSE / INCOME | 类型 |
| categoryId | long | 分类（匹配二级分类或该一级分类下的全部账单） |
| keyword | string | 匹配备注 / 标签 / AI 原始输入 |
| minAmount / maxAmount | decimal | 金额区间 |
| payMethod | string | 支付方式 |
| sortBy / sortOrder | string | `billDate`(默认) / `amount` / `createdAt`；`asc` / `desc` |

账单对象：

```json
{
  "id": 626, "type": "EXPENSE",
  "categoryId": 21, "categoryName": "饮品零食", "categoryIcon": "food", "categoryColor": "#FF7A45",
  "parentCategoryId": 1, "parentCategoryName": "餐饮",
  "amount": 32.00, "remark": "奶茶", "billDate": "2026-10-30", "billTime": "2026-10-30 20:15:00",
  "payMethod": "微信", "tags": null, "source": "AI", "aiInput": "昨天买了两杯奶茶 32 用微信",
  "createdAt": "2026-10-31 20:15:03"
}
```

### 其它

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/bills/{id}` | 详情 |
| POST | `/bills` | 新增，body `{ type, categoryId, amount, remark, billDate, payMethod, tags }` |
| PUT | `/bills/{id}` | 编辑 |
| DELETE | `/bills/{id}` | 逻辑删除 |
| GET | `/bills/recent?limit=5` | 最近账单 |
| GET | `/bills/summary?startDate&endDate` | 区间合计 `{ income, expense, balance, count }` |

---

## 4. 统计

### GET /stats/overview?yearMonth=2026-10

```json
{
  "yearMonth": "2026-10",
  "monthIncome": 20098.37, "monthExpense": 12240.33, "monthBalance": 7858.04, "monthBillCount": 121,
  "todayIncome": 0, "todayExpense": 457.56, "todayBillCount": 4,
  "lastMonthIncome": 22205.04, "lastMonthExpense": 10113.39, "expenseMoM": 21.03, "incomeMoM": -9.49,
  "avgDailyExpense": 394.85, "topCategoryName": "居住", "topCategoryAmount": 3744.46,
  "budgetAmount": 7000.00, "budgetUsed": 12240.33, "budgetUsageRate": 174.86,
  "continuousDays": 120
}
```

### GET /stats/trend?startDate&endDate&granularity=day|month

返回连续坐标轴（缺失日期补 0）：

```json
[{ "date": "2026-10-01", "label": "10-01", "income": 0.00, "expense": 128.50 }]
```

跨度超过 120 天时自动按月聚合。

### GET /stats/category?startDate&endDate&type=EXPENSE&level=parent|child

```json
[{ "categoryId": 4, "name": "居住", "icon": "home", "color": "#36CFC9",
   "amount": 3744.46, "count": 3, "percent": 30.59, "avgAmount": 1248.15 }]
```

### GET /stats/monthly?year=2026

12 个月固定返回：`[{ month, label, income, expense, balance }]`

### GET /stats/recent-months?months=6

近 N 个月对比，同上结构。

### GET /stats/calendar?yearMonth=2026-10

当月每天的收支与热力等级（0-4）：`[{ date, day, income, expense, count, level }]`

---

## 5. 预算

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/budgets?yearMonth=` | 该月预算列表（含执行情况） |
| GET | `/budgets/status?yearMonth=` | 预算总览 + 提醒 |
| POST | `/budgets` | body `{ yearMonth, categoryId(0=总预算), amount, alertThreshold }` |
| PUT | `/budgets/{id}` | 修改金额 / 阈值 |
| DELETE | `/budgets/{id}` | 删除 |
| POST | `/budgets/copy-last-month?yearMonth=` | 复用上月预算 |

`/budgets/status` 响应：

```json
{
  "yearMonth": "2026-10",
  "totalBudget": 7000.00, "totalUsed": 12240.33, "totalRemaining": -5240.33,
  "usageRate": 174.86, "dailyAllowance": 0.00, "daysLeft": 0,
  "items": [{ "categoryId": 1, "categoryName": "餐饮", "amount": 2600, "used": 3120.5,
              "usageRate": 120.02, "status": "EXCEEDED", "dailyAllowance": 0, "daysLeft": 0 }],
  "alerts": [{ "level": "EXCEEDED", "title": "本月预算已超支",
               "message": "本月支出 12240.33 元，已超出预算 5240.33 元…",
               "categoryId": 0, "amount": 12240.33, "usageRate": 174.86 }]
}
```

`status`：`SAFE` / `WARNING`（≥ 阈值，默认 80%）/ `EXCEEDED`（≥ 100%）。

---

## 6. AI 能力

### GET /ai/status

```json
{ "mode": "rule", "engine": "RULE", "model": "local-rule-engine",
  "provider": "deepseek", "llm": false,
  "description": "当前为本地规则引擎，离线也能记账与问答", "dailyLimit": 20 }
```

### POST /ai/bills/parse

```json
{ "text": "昨天午饭花了 30，用微信付的" }
```

```json
{
  "type": "EXPENSE", "typeLabel": "支出", "amount": 30.00,
  "categoryId": 3, "categoryName": "午餐", "categoryIcon": "food", "categoryColor": "#FF7A45",
  "parentCategoryId": 1, "parentCategoryName": "餐饮",
  "billDate": "2026-10-30", "dateLabel": "昨天",
  "remark": "午饭", "payMethod": "微信",
  "confidence": 0.95, "engine": "RULE", "complete": true,
  "rawText": "昨天午饭花了 30，用微信付的", "hints": []
}
```

`hints` 会给出「没有识别到金额」「自动归入了其他」等需要用户确认的提示。

### POST /ai/bills/record

同一 body，解析成功后直接落账，返回账单对象（`source = AI`）。

### POST /ai/chat（SSE）

请求：`{ "conversationId": null, "message": "本月花了多少？" }`
响应：`text/event-stream`

```
event:meta
data:{"conversationId":1,"engine":"RULE","model":"local-rule-engine"}

event:delta
data:{"content":"你 2026-10 一共"}

event:done
data:{"conversationId":1,"messageId":42,"engine":"RULE"}
```

失败时发送 `event:error`，`data:{"message":"..."}`。

> 因为需要 `Authorization` 头，前端不能用 `EventSource`，改为 `fetch` + `ReadableStream` 手动解析
> SSE（见 `web-mobile/src/api/index.js` 的 `streamChat`）。

### 会话管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/ai/conversations` | 最近 30 个会话（含消息数） |
| GET | `/ai/conversations/{id}/messages` | 会话消息明细 |
| DELETE | `/ai/conversations/{id}` | 删除会话 |

### GET /ai/analysis?yearMonth=

```json
{
  "yearMonth": "2026-10", "title": "2026-10 消费分析",
  "totalIncome": 20098.37, "totalExpense": 12240.33, "balance": 7858.04, "billCount": 121,
  "summary": "本月支出 12240.33 元，收入 20098.37 元，结余 7858.04 元，共 121 笔，支出大头是「居住」。",
  "insights": [{ "type": "WARNING", "title": "「居住」是最大支出项",
                 "detail": "占比超过四成，消费结构偏集中，是省钱的第一突破口。",
                 "evidence": "居住 3744.46 元，占比 30.59%，共 3 笔" }],
  "suggestions": [{ "type": "TIP", "title": "压缩「居住」两成",
                    "detail": "把 居住 的支出降低 20%，下个月就能多存一笔。",
                    "evidence": "本月 3744.46 元 → 预计可省 748.89 元/月" }],
  "generator": "RULE"
}
```

`type` 取值：`INFO` / `WARNING` / `GOOD` / `TIP`；`generator` 取值：`RULE` / `LLM`。

---

## 7. 消费报告

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/reports/monthly/{yearMonth}` | 指定月份报告（不存在则自动生成） |
| GET | `/reports/current?yearMonth=` | 当月报告（不传月份即当月） |
| POST | `/reports/monthly/{yearMonth}/generate` | 重新生成指定月份 |
| POST | `/reports/generate?yearMonth=` | 重新生成当月 |
| GET | `/reports/monthly?limit=12` | 历史报告列表 |

报告对象包含 `content`（Markdown 正文）、`insights`、`suggestions`、`generator` 等字段。

---

## 8. 后台管理（需 ADMIN）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/overview` | 用户数、账单数、累计收支、AI 调用量、今日新增 |
| GET | `/admin/users?page&size&keyword` | 用户列表（含账单数） |
| PUT | `/admin/users/{id}/status?status=1\|0` | 启用 / 禁用（不能禁用自己） |
| GET | `/admin/ai-usage` | AI 调用分布 `[{ scene, engine, cnt }]` |

---

## 9. 公共元数据

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/meta/health` | 健康检查（无需登录） |
| GET | `/meta/dicts` | 账单类型、支付方式、来源字典（无需登录） |

---

## 10. cURL 速查

```bash
# 登录并取出 token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"123456"}' | jq -r '.data.token')

# 本月概览
curl -s http://localhost:8080/api/stats/overview -H "Authorization: Bearer $TOKEN"

# 一句话记账
curl -s -X POST http://localhost:8080/api/ai/bills/record \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"text":"昨天午饭花了 30"}'

# AI 问答（流式）
curl -N -X POST http://localhost:8080/api/ai/chat \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"哪类花得最多"}'
```
