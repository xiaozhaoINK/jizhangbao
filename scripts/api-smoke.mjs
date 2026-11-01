#!/usr/bin/env node
/**
 * 记账宝 · 接口冒烟验收脚本
 *
 * 覆盖 9 组共 56 条断言：公共元数据 / 认证与鉴权 / 分类 / 账单 / 统计 / 预算 /
 * AI 能力（含 SSE 流式与语义解析）/ 月度报告 / 后台管理。
 *
 * 用法：
 *   1. 先启动后端（默认 dev profile，会自动写入演示数据）
 *        cd server && mvn spring-boot:run
 *   2. 再执行
 *        node scripts/api-smoke.mjs
 *        node scripts/api-smoke.mjs http://127.0.0.1:8080/api   # 指定地址
 *
 * 说明：依赖演示数据（demo/123456、admin/123456）；聚合类断言使用「上个月」区间，
 *      因此任何月份执行都能稳定通过。退出码 0 表示全部通过。
 */
const BASE = process.argv[2] || 'http://127.0.0.1:8080/api'

let pass = 0
let fail = 0
const failures = []

async function api(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined || body === null ? undefined : JSON.stringify(body)
  })
  let json = null
  const text = await res.text()
  try {
    json = text ? JSON.parse(text) : null
  } catch (e) {
    json = null
  }
  if (res.status === 401) {
    const err = new Error(`401 ${path}`)
    err.status = 401
    throw err
  }
  if (!json || json.code !== 0) {
    throw new Error(`${path} -> code=${json && json.code} msg=${json && json.message}`)
  }
  return json.data
}

async function check(name, fn) {
  try {
    const ok = await fn()
    if (ok === false) throw new Error('断言失败')
    pass++
    console.log(`  PASS  ${name}`)
  } catch (e) {
    fail++
    failures.push(`${name}: ${e.message}`)
    console.log(`  FAIL  ${name}  --> ${e.message}`)
  }
}

async function expectFailure(name, fn) {
  try {
    await fn()
    fail++
    failures.push(`${name}: 期望失败却成功了`)
    console.log(`  FAIL  ${name}  --> 期望被拒绝但请求成功`)
  } catch (e) {
    pass++
    console.log(`  PASS  ${name}`)
  }
}

;(async () => {
  console.log('===== 1. 公共元数据 =====')
  await check('健康检查', async () => (await api('GET', '/meta/health')).status === 'UP')
  await check('字典接口', async () => (await api('GET', '/meta/dicts')).payMethods.length > 0)

  console.log('===== 2. 认证 =====')
  const suffix = Math.random().toString(36).slice(2, 8)
  const newUser = `t${suffix}`
  let reg = null
  await check('注册新用户', async () => {
    reg = await api('POST', '/auth/register', { username: newUser, password: '123456', nickname: '测试用户' })
    return reg.token.length > 20
  })
  await check('新用户自动初始化 60+ 分类', async () => {
    const flat = await api('GET', '/categories/flat', null, reg.token)
    return flat.length >= 60
  })
  const login = await api('POST', '/auth/login', { username: 'demo', password: '123456' })
  const T = login.token
  await check('演示账号登录', async () => login.user.username === 'demo')
  await check('当前用户信息', async () => (await api('GET', '/auth/me', null, T)).role === 'USER')
  await expectFailure('错误密码被拒绝', () => api('POST', '/auth/login', { username: 'demo', password: 'wrong' }))
  await expectFailure('未带 Token 访问被拦截', () => api('GET', '/bills'))

  console.log('===== 3. 分类 =====')
  await check('支出分类树 9 个一级分类', async () => (await api('GET', '/categories?type=EXPENSE', null, T)).length === 9)
  const cat = await api('POST', '/categories', { name: '验收分类', type: 'EXPENSE', color: '#123456', icon: 'other' }, T)
  await check('新增自定义分类', async () => cat.id > 0)
  await check('编辑分类', async () => (await api('PUT', `/categories/${cat.id}`, { name: '验收分类2', type: 'EXPENSE', color: '#654321' }, T)).name === '验收分类2')
  await check('删除自定义分类', async () => {
    await api('DELETE', `/categories/${cat.id}`, null, T)
    return true
  })
  await expectFailure('系统内置分类不可删除', async () => {
    const flat = await api('GET', '/categories/flat?type=EXPENSE', null, T)
    const sys = flat.find((c) => c.system === true)
    await api('DELETE', `/categories/${sys.id}`, null, T)
  })

  console.log('===== 4. 账单 =====')
  const page = await api('GET', '/bills?page=1&size=5', null, T)
  await check('账单分页', async () => page.total > 700 && page.records.length === 5)
  await check('关键字筛选', async () => (await api('GET', '/bills?keyword=' + encodeURIComponent('外卖'), null, T)).total >= 0)
  await check('金额区间筛选', async () => (await api('GET', '/bills?minAmount=100&maxAmount=200', null, T)).total >= 0)
  await check('类型筛选', async () => (await api('GET', '/bills?type=INCOME', null, T)).records.length > 0)
  // 系统时间已跨月，聚合断言统一用「上个月」这个一定有数据的区间
  const prev = new Date()
  prev.setMonth(prev.getMonth() - 1)
  const prevMonth = `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}`
  const span = `startDate=${prevMonth}-01&endDate=${prevMonth}-28`
  const sum = await api('GET', '/bills/summary?' + span, null, T)
  await check('区间合计', async () => Number(sum.income) > 0 && Number(sum.expense) > 0 && Number(sum.count) > 0)

  const flatExpense = await api('GET', '/categories/flat?type=EXPENSE', null, T)
  const child = flatExpense.find((c) => c.parentId > 0)
  const today = new Date().toISOString().slice(0, 10)
  const created = await api('POST', '/bills', { type: 'EXPENSE', categoryId: child.id, amount: 66.66, remark: '接口验收', billDate: today, payMethod: '微信' }, T)
  await check('新增账单', async () => created.id > 0 && created.categoryName === child.name)
  await check('编辑账单', async () => Number((await api('PUT', `/bills/${created.id}`, { type: 'EXPENSE', categoryId: child.id, amount: 88.88, billDate: today }, T)).amount) === 88.88)
  await check('账单详情', async () => (await api('GET', `/bills/${created.id}`, null, T)).id === created.id)
  await check('删除账单（逻辑删除）', async () => {
    await api('DELETE', `/bills/${created.id}`, null, T)
    return (await api('GET', `/bills/${created.id}`, null, T).then(() => false).catch(() => true))
  })
  await expectFailure('分类与账单类型不匹配被拒绝', async () => {
    const incomeChild = (await api('GET', '/categories/flat?type=INCOME', null, T)).find((c) => c.parentId > 0)
    await api('POST', '/bills', { type: 'EXPENSE', categoryId: incomeChild.id, amount: 10 }, T)
  })

  console.log('===== 5. 统计 =====')
  const ov = await api('GET', '/stats/overview', null, T)
  await check('概览指标完整', async () => Boolean(ov.yearMonth) && ov.monthExpense !== undefined && ov.continuousDays !== undefined)
  const trend = await api('GET', '/stats/trend?granularity=day&' + span, null, T)
  await check('收支趋势返回连续坐标轴', async () => trend.length >= 28 && trend.every((p) => p.date && p.label))
  const cats = await api('GET', '/stats/category?type=EXPENSE&level=parent&' + span, null, T)
  await check('分类占比（含百分比）', async () => cats.length > 0 && Number(cats[0].percent) > 0)
  await check('年度 12 个月对比', async () => (await api('GET', '/stats/monthly', null, T)).length === 12)
  await check('近 6 个月对比', async () => (await api('GET', '/stats/recent-months?months=6', null, T)).length === 6)
  await check('日历热力等级 0-4', async () => {
    const cal = await api('GET', '/stats/calendar', null, T)
    return cal.length >= 28 && cal.every((d) => d.level >= 0 && d.level <= 4)
  })

  console.log('===== 6. 预算 =====')
  const bs = await api('GET', '/budgets/status', null, T)
  await check('预算状态与超支提醒', async () => Number(bs.totalBudget) > 0 && bs.items.length > 0)
  await expectFailure('同月重复设置总预算被拒绝', () => api('POST', '/budgets', { yearMonth: bs.yearMonth, categoryId: 0, amount: 1000 }, T))
  const budgetCat = flatExpense.find((c) => c.parentId === 0 && c.name === '娱乐')
  // 清理历史用例可能残留的同月同分类预算
  const existing = await api('GET', `/budgets?yearMonth=${bs.yearMonth}`, null, T)
  for (const item of existing.filter((i) => i.categoryId === budgetCat.id)) {
    await api('DELETE', `/budgets/${item.id}`, null, T)
  }
  const b = await api('POST', '/budgets', { yearMonth: bs.yearMonth, categoryId: budgetCat.id, amount: 888, alertThreshold: 80 }, T)
  await check('新增分类预算', async () => b.id > 0)
  await check('修改预算金额', async () => Number((await api('PUT', `/budgets/${b.id}`, { yearMonth: bs.yearMonth, categoryId: budgetCat.id, amount: 999 }, T)).amount) === 999)
  await check('删除预算', async () => {
    await api('DELETE', `/budgets/${b.id}`, null, T)
    return true
  })
  const copied = await api('POST', `/budgets/copy-last-month?yearMonth=${bs.yearMonth}`, null, T)
  await check('复用上月预算', async () => typeof copied.copied === 'number')

  console.log('===== 7. AI 能力 =====')
  const status = await api('GET', '/ai/status', null, T)
  await check('AI 状态', async () => Boolean(status.engine) && Boolean(status.description))
  const p1 = await api('POST', '/ai/bills/parse', { text: '昨天午饭花了 30，用微信付的' }, T)
  await check('语义解析：类型/金额/分类/支付方式/日期', async () => p1.type === 'EXPENSE' && Number(p1.amount) === 30 && p1.categoryName === '午餐' && p1.payMethod === '微信' && p1.billDate !== today)
  const p2 = await api('POST', '/ai/bills/parse', { text: '今天发工资 12000' }, T)
  await check('收入意图识别', async () => p2.type === 'INCOME' && p2.categoryName === '基本工资')
  const p3 = await api('POST', '/ai/bills/parse', { text: '午饭三十块' }, T)
  await check('中文数字识别', async () => Number(p3.amount) === 30)
  const p4 = await api('POST', '/ai/bills/parse', { text: '上周五看电影花了 45' }, T)
  await check('相对日期 + 分类识别', async () => p4.categoryName === '电影演出' && p4.billDate !== today)
  const rec = await api('POST', '/ai/bills/record', { text: '验收测试买奶茶 18.5 支付宝' }, T)
  await check('AI 一句话落账（source=AI）', async () => rec.source === 'AI' && Number(rec.amount) === 18.5)
  await api('DELETE', `/bills/${rec.id}`, null, T)

  const chatRes = await fetch(`${BASE}/ai/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json; charset=utf-8', Authorization: `Bearer ${T}` },
    body: JSON.stringify({ message: '本月花了多少？' })
  })
  const chatText = await chatRes.text()
  await check('AI 问答 SSE（meta/delta/done）', async () => chatText.includes('event:meta') && chatText.includes('event:delta') && chatText.includes('event:done'))
  await check('AI 回答包含真实金额', async () => chatText.includes('元') && /\d/.test(chatText))

  const an = await api('GET', `/ai/analysis?yearMonth=${prevMonth}`, null, T)
  await check('消费洞察（洞察 + 建议）', async () => an.insights.length >= 3 && an.suggestions.length >= 2)
  await check('省钱建议避开刚性支出', async () => !/居住|房租/.test(an.suggestions[0].title))

  console.log('===== 8. 月度报告 =====')
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
  const lastMonth = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  const rp = await api('GET', `/reports/monthly/${lastMonth}`, null, T)
  await check('历史报告读取', async () => rp.yearMonth === lastMonth && rp.content.length > 50)
  await check('报告含 Markdown 正文与洞察', async () => rp.content.includes('关键发现') && rp.insights.length > 0)
  await check('报告重新生成', async () => Boolean((await api('POST', `/reports/monthly/${lastMonth}/generate`, null, T)).generator))
  await check('历史报告列表', async () => (await api('GET', '/reports/monthly?limit=5', null, T)).length >= 1)
  await check('当月报告（无数据时明确报 4004，不返回脏数据）', async () => {
    try {
      const cur = await api('GET', '/reports/current', null, T)
      return Boolean(cur.yearMonth)
    } catch (e) {
      return /4004/.test(e.message) || e.message.includes('4004')
    }
  })

  console.log('===== 9. 后台管理 =====')
  await expectFailure('普通用户访问后台被拒绝', () => api('GET', '/admin/overview', null, T))
  const admin = await api('POST', '/auth/login', { username: 'admin', password: '123456' })
  const A = admin.token
  const ao = await api('GET', '/admin/overview', null, A)
  await check('平台概览', async () => ao.userCount >= 2 && ao.billCount > 700)
  await check('用户列表', async () => (await api('GET', '/admin/users?page=1&size=10', null, A)).total >= 2)
  await check('禁用 / 启用用户', async () => {
    const u = (await api('GET', `/admin/users?keyword=${newUser}`, null, A)).records[0]
    await api('PUT', `/admin/users/${u.id}/status?status=0`, null, A)
    const disabled = (await api('GET', `/admin/users?keyword=${newUser}`, null, A)).records[0].status
    await api('PUT', `/admin/users/${u.id}/status?status=1`, null, A)
    return disabled === 0
  })
  await check('AI 调用分布', async () => (await api('GET', '/admin/ai-usage', null, A)).length > 0)
  await check('退出登录后 Token 进入黑名单', async () => {
    const extra = await api('POST', '/auth/login', { username: 'demo', password: '123456' })
    await api('POST', '/auth/logout', null, extra.token)
    try {
      await api('GET', '/auth/me', null, extra.token)
      return false
    } catch (e) {
      return e.status === 401
    }
  })

  console.log('')
  console.log('================ 验收结果 ================')
  console.log(`  PASS: ${pass}    FAIL: ${fail}`)
  if (failures.length) failures.forEach((f) => console.log('  - ' + f))
  console.log('==========================================')
  process.exit(fail > 0 ? 1 : 0)
})()
