/** 金额：保留两位小数并加千分位 */
export function money(value, withSign = false) {
  const num = Number(value || 0)
  const text = num
    .toFixed(2)
    .replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  if (!withSign) return text
  return num > 0 ? `+${text}` : text
}

/** 金额（带 ¥ 符号） */
export function yuan(value) {
  return `¥${money(value)}`
}

/** 紧凑金额：超过 1 万显示「1.2万」 */
export function compactMoney(value) {
  const num = Number(value || 0)
  if (Math.abs(num) >= 10000) {
    return `${(num / 10000).toFixed(1)}万`
  }
  return money(num)
}

/** yyyy-MM-dd -> M月d日 */
export function shortDate(date) {
  if (!date) return ''
  const [y, m, d] = String(date).split('-')
  return `${Number(m)}月${Number(d)}日`
}

/** yyyy-MM-dd -> 周几 */
export function weekdayOf(date) {
  if (!date) return ''
  const names = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  const parsed = new Date(`${date}T00:00:00`)
  return names[parsed.getDay()] || ''
}

/** 今天 / 昨天 / M月d日 */
export function friendlyDate(date) {
  if (!date) return ''
  const today = new Date()
  const target = new Date(`${date}T00:00:00`)
  const diff = Math.round((today.setHours(0, 0, 0, 0) - target.getTime()) / 86400000)
  if (diff === 0) return '今天'
  if (diff === 1) return '昨天'
  if (diff === 2) return '前天'
  return shortDate(date)
}

/** 当前月份 yyyy-MM */
export function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

/** yyyy-MM 月份的首末日期 */
export function monthRange(yearMonth) {
  const [y, m] = yearMonth.split('-').map(Number)
  const lastDay = new Date(y, m, 0).getDate()
  return {
    startDate: `${yearMonth}-01`,
    endDate: `${yearMonth}-${String(lastDay).padStart(2, '0')}`
  }
}

/** 今天 yyyy-MM-dd */
export function today() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(
    now.getDate()
  ).padStart(2, '0')}`
}

/** 百分比展示，去掉多余小数 */
export function percent(value) {
  const num = Number(value || 0)
  return `${Number(num.toFixed(1))}%`
}

/** 环比描述 */
export function momText(value) {
  const num = Number(value || 0)
  if (num === 0) return { text: '持平', type: 'flat' }
  return num > 0
    ? { text: `较上月 ↑${percent(Math.abs(num))}`, type: 'up' }
    : { text: `较上月 ↓${percent(Math.abs(num))}`, type: 'down' }
}

/** 收支颜色 */
export function amountClass(type) {
  return type === 'INCOME' ? 'text-income' : 'text-expense'
}

/** 收支符号 */
export function amountSign(type) {
  return type === 'INCOME' ? '+' : '-'
}
