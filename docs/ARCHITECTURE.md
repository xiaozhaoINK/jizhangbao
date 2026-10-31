# 架构与设计决策

本文记录「记账宝」的结构划分与关键取舍，便于二次开发和代码评审。

---

## 1. 分层与模块边界

```
controller  只做参数校验、鉴权上下文读取、响应包装，不写业务逻辑
service     业务编排、事务边界；跨模块只依赖对方 Service 接口
mapper      MyBatis-Plus BaseMapper + 少量聚合 SQL（XML）
entity      与表结构一一对应，仅承载持久化字段
dto         record 类型，出入参分离，entity 不出 service 层
```

模块：

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| `user` | 注册登录、资料、密码 | category（注册时初始化分类） |
| `category` | 分类树、内置分类模板、关键词匹配 | bill mapper（统计分类账单数） |
| `bill` | 账单 CRUD、多维筛选、区间合计 | category |
| `stats` | 概览/趋势/分类/月度对比/日历聚合 | bill mapper、budget |
| `budget` | 预算进度与提醒 | bill mapper、category |
| `report` | 月度报告生成与定时任务 | ai.analysis |
| `ai` | 解析/问答/洞察、提示词、调用日志 | category、bill、stats、budget |
| `admin` | 平台概览、用户管理 | 各 mapper |

> 依赖方向刻意保持单向：`stats → budget`、`budget → bill mapper`，避免 `stats ↔ budget` 循环依赖。
> `category` 与 `bill` 之间也只在 `CategoryServiceImpl` 里直接注入 `BillMapper`（而非 `BillService`），从而打断循环。

---

## 2. AI 记账链路

```
POST /api/ai/bills/parse
   │
   ├─ 限流（Redis 计数，20 次/分钟/用户）
   ├─ 缓存（同句 1 小时内直接返回，省 token）
   │
   ├─ AiEngine.parseBill
   │     ├─ SpringAiEngine：提示词里给出「用户真实分类清单」+ 今天日期，要求只输出 JSON
   │     └─ RuleBasedAiEngine：正则抽金额/日期/支付方式 + 关键词表映射分类
   │
   ├─ 分类解析与兜底（CategoryMatcher）
   │     ├─ 精确名命中 → 关键词命中 → 一级分类兜底 → 「其他」
   │     └─ 补全 parentCategoryId，返回 hints 让前端提示用户确认
   │
   └─ BillParseVO（含 confidence / engine / complete / hints）

POST /api/ai/bills/record  → parse + createFromAi，落库后返回账单
```

### 为什么要有规则引擎

1. **可演示性**：clone 下来不配 API Key 也能完整体验 AI 记账，评审无需任何准备。
2. **可用性**：大模型超时/限流/欠费时自动降级，记账这个核心动作不会因为第三方故障而不可用。
3. **可控性**：规则引擎的输出是确定性的，便于写回归测试。

### 提示词工程要点

- 分类候选集从**用户真实分类**动态生成，避免模型产出不存在的分类；
- 提示词里显式给出「今天是 X 年 X 月 X 日 星期 X」与昨天日期，让相对时间可换算；
- 强制"只输出 JSON"，再用 `readJson` 做容错抽取（去掉 ```json 围栏、截取首末花括号）；
- 把提示词放 `resources/prompts/*.st`，占位符 `{{name}}` 由 `PromptTemplates` 渲染，便于迭代与评审。

---

## 3. 财务问答为什么"不瞎编"

`FinanceContextBuilder` 在每次提问前构造 `FinanceSnapshot`：

```java
record FinanceSnapshot(
    String yearMonth, BigDecimal monthIncome, BigDecimal monthExpense, BigDecimal balance,
    long billCount, BigDecimal lastMonthIncome, BigDecimal lastMonthExpense, BigDecimal expenseMoM,
    BigDecimal avgDailyExpense, int continuousDays,
    List<CategoryLine> topExpenseCategories, List<CategoryLine> topIncomeCategories,
    BigDecimal budgetAmount, BigDecimal budgetUsed, BigDecimal budgetUsageRate,
    String topSingleBill, String text)  // text = 渲染好的中文上下文
```

- **LLM 模式**：`text` 进提示词，模型只负责组织语言；
- **规则模式**：`LocalAnswerGenerator` 按意图（支出/收入/结余/分类/预算/对比/建议）选择模板，所有数字来自快照。

同一份快照，两种表达，数字永远一致 —— 这是"可信"的来源。

---

## 4. 统计性能

- **聚合下推**：`BillMapper.xml` 中的 `sumByDay / sumByMonth / sumByCategory / sumRange` 全部是
  `GROUP BY` + `SUM(CASE WHEN ...)`，一次查询拿到收支两条线。
- **连续坐标轴在内存补全**：数据库只返回有数据的日期，Java 侧用 `DateUtils.datesBetween` 补 0，
  避免为"看起来连续"去写日历表连接。
- **索引**：`t_bill` 上 `(user_id, bill_date)`、`(user_id, type, bill_date)`、`category_id`、`parent_category_id`，
  覆盖按用户 + 时间 + 类型的常规查询。
- **降采样**：趋势接口跨度 > 120 天时自动按月聚合，避免前端画 365 个点。
- **缓存**：首页概览 60s 缓存，写账单时按前缀主动失效（`jzb:stats:overview:{uid}:`）。

---

## 5. 安全

| 面 | 措施 |
| --- | --- |
| 密码 | BCrypt（`spring-security-crypto`），不引入完整 Spring Security 以免过度设计 |
| 会话 | JWT（HS384）+ Redis 黑名单，登出立即失效 |
| 越权 | 所有 `getOwned(userId, id)` 校验归属；后台接口 `AuthContext.requireAdmin()` |
| 参数 | `jakarta.validation` 注解 + 全局异常汇总提示；分页 size 上限 200 |
| 上传/注入 | 无文件上传；全部走 MyBatis 预编译参数，统计 SQL 用 `<if>` 而非字符串拼接 |
| CORS | 可配置白名单（`jizhangbao.cors.allowed-origins`），默认通配仅用于本地开发 |

---

## 6. 可靠性与降级

| 场景 | 行为 |
| --- | --- |
| Redis 不可用 | `CacheHelper` 捕获全部异常：缓存读不到就回源、写失败忽略、限流失效（放行） |
| 大模型失败 | 解析降级到规则引擎；问答返回友好提示；洞察降级到规则生成 |
| 并发生成报告 | Redis `setIfAbsent` 30s 锁 |
| 预算提醒刷屏 | 同分类同月提醒 24h 去重 |
| MySQL 唯一约束 | 注册用 `username` 唯一索引兜底并发；预算同月同分类在 service 层先查后插 |

---

## 7. 前端要点

- **统一请求层**：`api/index.js` 收敛 axios 实例与拦截器，`code !== 0` 统一 Toast，401 清理会话并跳登录。
- **移动端 SSE**：`streamChat()` 用 `fetch` + `ReadableStream` 手动解析 `event:` / `data:`，
  因为 `EventSource` 不支持自定义请求头（需要 `Authorization`）。
- **图表封装**：`EChart.vue` / `BaseChart.vue` 负责 init / setOption / ResizeObserver / dispose，
  页面只关心 option，避免图表容器高度为 0 时不渲染的问题。
- **金额格式**：`utils/format.js` 统一千分位、两位小数、日期友好化（今天/昨天/前天），两端一致。

---

## 8. 已知取舍

| 取舍 | 原因 |
| --- | --- |
| 不用 Spring Security 全家桶 | 只需 BCrypt + 一个拦截器，引入 FilterChain 反而增加理解成本 |
| 不做通用代码生成器 | 项目规模适中，手写 mapper/service 更直观，也便于展示设计能力 |
| 分类固定两级 | 记账场景两级足够，演示与校验逻辑都更简单；表结构已支持无限级 |
| 演示数据写入生产 profile 外的 `dev` | 生产环境需显式关闭 `jizhangbao.demo.enabled` |
| 未引入消息队列 | 报告生成用 `@Scheduled` + Redis 锁已足够，避免为演示引入额外中间件 |
