import {
  Brush,
  Cellphone,
  Coffee,
  Coin,
  CreditCard,
  Film,
  FirstAidKit,
  Food,
  House,
  Money,
  More,
  Present,
  Reading,
  RefreshLeft,
  ShoppingBag,
  ShoppingCart,
  Suitcase,
  Tickets,
  TrendCharts,
  Trophy,
  Van,
  Wallet
} from '@element-plus/icons-vue'

/**
 * 后端分类的 icon 字段是语义化标识（food / transport ...），这里映射到图标组件。
 */
export const CATEGORY_ICON_MAP = {
  food: Food,
  transport: Van,
  shopping: ShoppingBag,
  home: House,
  entertainment: Film,
  medical: FirstAidKit,
  education: Reading,
  gift: Present,
  other: More,
  salary: Money,
  parttime: Suitcase,
  invest: TrendCharts,
  redpacket: Trophy,
  refund: RefreshLeft,
  'other-income': Coin,
  wallet: Wallet,
  'credit-card': CreditCard,
  coffee: Coffee,
  'shopping-cart': ShoppingCart,
  ticket: Tickets,
  phone: Cellphone,
  beauty: Brush
}

export const CATEGORY_ICON_OPTIONS = [
  { value: 'food', label: '餐饮' },
  { value: 'transport', label: '交通' },
  { value: 'shopping', label: '购物' },
  { value: 'home', label: '居住' },
  { value: 'entertainment', label: '娱乐' },
  { value: 'medical', label: '医疗' },
  { value: 'education', label: '教育' },
  { value: 'gift', label: '礼物' },
  { value: 'salary', label: '工资' },
  { value: 'parttime', label: '兼职' },
  { value: 'invest', label: '理财' },
  { value: 'refund', label: '退款' },
  { value: 'wallet', label: '钱包' },
  { value: 'credit-card', label: '信用卡' },
  { value: 'coffee', label: '咖啡' },
  { value: 'shopping-cart', label: '购物车' },
  { value: 'ticket', label: '票券' },
  { value: 'phone', label: '通讯' },
  { value: 'beauty', label: '美妆' },
  { value: 'other', label: '其他' }
]

export const CATEGORY_COLORS = [
  '#FF7A45',
  '#40A9FF',
  '#9254DE',
  '#36CFC9',
  '#F759AB',
  '#73D13D',
  '#597EF7',
  '#FFC53D',
  '#8C8C8C',
  '#52C41A',
  '#13C2C2',
  '#FAAD14'
]

export function resolveCategoryIcon(icon) {
  return CATEGORY_ICON_MAP[icon] || null
}
