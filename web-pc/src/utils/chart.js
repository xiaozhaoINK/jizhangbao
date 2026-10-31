import { formatMoney, formatNumber, formatPercent, hexToRgba } from './format'

export const CHART_COLORS = {
  primary: '#4F6EF7',
  purple: '#7C5CFF',
  income: '#00B578',
  expense: '#FF5B5B',
  warning: '#FF9F0A',
  cyan: '#36CFC9',
  pink: '#F759AB',
  violet: '#9254DE'
}

export const PALETTE = [
  '#4F6EF7',
  '#7C5CFF',
  '#00B578',
  '#FF9F0A',
  '#FF5B5B',
  '#36CFC9',
  '#F759AB',
  '#9254DE',
  '#40A9FF',
  '#73D13D'
]

export const axisLineStyle = { lineStyle: { color: '#E7EBF5' } }
export const axisTickStyle = { show: false }
export const axisLabelStyle = { color: '#8A94AB', fontSize: 11 }
export const splitLineStyle = { lineStyle: { color: '#EDF0F7', type: 'dashed' } }
export const gridBase = { left: 4, right: 12, top: 28, bottom: 2, containLabel: true }
export const legendBase = {
  icon: 'circle',
  itemWidth: 8,
  itemHeight: 8,
  itemGap: 18,
  textStyle: { color: '#5A6480', fontSize: 12 }
}

export function tooltipConfig(options = {}) {
  return {
    trigger: 'axis',
    backgroundColor: '#FFFFFF',
    borderColor: '#EDF0F7',
    borderWidth: 1,
    padding: [10, 14],
    textStyle: { color: '#1B2440', fontSize: 12 },
    extraCssText: 'box-shadow: 0 8px 24px rgba(31,45,90,.12); border-radius: 10px;',
    axisPointer: { type: 'line', lineStyle: { color: '#D6DCEA', type: 'dashed' } },
    ...options
  }
}

export function areaGradient(color, from = 0.26) {
  return {
    type: 'linear',
    x: 0,
    y: 0,
    x2: 0,
    y2: 1,
    colorStops: [
      { offset: 0, color: hexToRgba(color, from) },
      { offset: 1, color: hexToRgba(color, 0.01) }
    ]
  }
}

export function seriesColor(color) {
  return color || CHART_COLORS.primary
}

export function tooltipRows(params, formatter = (value) => formatMoney(value)) {
  const list = Array.isArray(params) ? params : [params]
  const axisLabel = list[0]?.axisValueLabel ?? list[0]?.name ?? ''
  const rows = list
    .map(
      (item) => `<div style="display:flex;align-items:center;justify-content:space-between;gap:18px;margin-top:6px;">
        <span style="display:inline-flex;align-items:center;gap:6px;color:#5A6480;">
          <i style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${item.color};"></i>
          ${item.seriesName}
        </span>
        <strong style="font-variant-numeric:tabular-nums;">${formatter(item.value, item)}</strong>
      </div>`
    )
    .join('')
  return `<div style="font-size:12px;color:#8A94AB;">${axisLabel}</div>${rows}`
}

export function pieTooltip(params) {
  const item = Array.isArray(params) ? params[0] : params
  if (!item) return ''
  return `<div style="display:flex;align-items:center;gap:8px;font-size:12px;color:#1B2440;">
      <i style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${item.color};"></i>
      <span>${item.name}</span>
    </div>
    <div style="margin-top:6px;font-size:13px;font-variant-numeric:tabular-nums;">
      ${formatMoney(item.value)} · ${formatPercent(item.percent, 1)}
    </div>`
}

export function compactAxisLabel(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return ''
  if (Math.abs(num) >= 10000) return `${formatNumber(num / 10000, 1)}万`
  return formatNumber(num, 0)
}
