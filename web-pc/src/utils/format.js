const pad = (value) => String(value).padStart(2, '0')

export function toNumber(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

export function formatNumber(value, digits = 2) {
  return toNumber(value).toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}

export function formatMoney(value, digits = 2) {
  const num = toNumber(value)
  const sign = num < 0 ? '-' : ''
  return `${sign}¥${formatNumber(Math.abs(num), digits)}`
}

export function formatSignedMoney(value, type, digits = 2) {
  const prefix = type === 'INCOME' ? '+' : '-'
  return `${prefix}¥${formatNumber(Math.abs(toNumber(value)), digits)}`
}

export function formatPercent(value, digits = 1) {
  return `${toNumber(value).toFixed(digits)}%`
}

function parseDateValue(value) {
  if (!value) return null
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value
  }
  if (Array.isArray(value)) {
    const [year, month = 1, day = 1, hour = 0, minute = 0, second = 0] = value
    return new Date(year, month - 1, day, hour, minute, second)
  }
  if (typeof value === 'number') {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? null : date
  }
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return null
    const dateOnly = text.match(/^(\d{4})-(\d{2})-(\d{2})$/)
    if (dateOnly) {
      return new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]))
    }
    const date = new Date(text.replace(' ', 'T'))
    return Number.isNaN(date.getTime()) ? null : date
  }
  return null
}

export function formatDate(value, fallback = '—') {
  const date = parseDateValue(value)
  if (!date) return fallback
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function formatDateTime(value, fallback = '—') {
  const date = parseDateValue(value)
  if (!date) return fallback
  return `${formatDate(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatMonthLabel(yearMonth, fallback = '—') {
  if (!yearMonth) return fallback
  const [year, month] = String(yearMonth).split('-')
  if (!year || !month) return String(yearMonth)
  return `${year}年${Number(month)}月`
}

export function currentYearMonth(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}`
}

export function todayString() {
  return formatDate(new Date())
}

export function monthStart(yearMonth) {
  return `${yearMonth}-01`
}

export function daysInMonth(yearMonth) {
  const [year, month] = String(yearMonth).split('-').map(Number)
  return new Date(year, month, 0).getDate()
}

export function monthEnd(yearMonth) {
  return `${yearMonth}-${pad(daysInMonth(yearMonth))}`
}

export function shiftMonth(yearMonth, offset) {
  const [year, month] = String(yearMonth).split('-').map(Number)
  const date = new Date(year, month - 1 + offset, 1)
  return currentYearMonth(date)
}

export function buildMonthRange(startMonth, endMonth) {
  return {
    startDate: monthStart(startMonth),
    endDate: monthEnd(endMonth)
  }
}

export function recentDaysRange(days = 30, endDate = new Date()) {
  const end = new Date(endDate.getFullYear(), endDate.getMonth(), endDate.getDate())
  const start = new Date(end)
  start.setDate(start.getDate() - Math.max(1, days) + 1)
  return { startDate: formatDate(start), endDate: formatDate(end) }
}

export function hexToRgba(color, alpha = 1) {
  if (!color) return `rgba(79, 110, 247, ${alpha})`
  const value = String(color).trim()
  if (value.startsWith('rgb')) return value
  let hex = value.replace('#', '')
  if (hex.length === 3) {
    hex = hex
      .split('')
      .map((char) => char + char)
      .join('')
  }
  const int = Number.parseInt(hex, 16)
  if (Number.isNaN(int) || hex.length !== 6) return `rgba(79, 110, 247, ${alpha})`
  const r = (int >> 16) & 255
  const g = (int >> 8) & 255
  const b = int & 255
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
