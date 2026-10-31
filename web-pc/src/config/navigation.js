export const NAV_GROUPS = [
  {
    title: '概览',
    items: [{ name: 'dashboard', label: '数据概览', icon: 'Odometer' }]
  },
  {
    title: '账簿',
    items: [
      { name: 'bills', label: '账单管理', icon: 'Tickets' },
      { name: 'categories', label: '分类管理', icon: 'Collection' },
      { name: 'budgets', label: '预算管理', icon: 'Wallet' }
    ]
  },
  {
    title: '洞察',
    items: [
      { name: 'stats', label: '统计分析', icon: 'TrendCharts' },
      { name: 'ai', label: 'AI 助手', icon: 'MagicStick' },
      { name: 'reports', label: '消费报告', icon: 'Document' }
    ]
  },
  {
    title: '系统',
    items: [
      { name: 'admin-overview', label: '平台概览', icon: 'DataBoard', admin: true },
      { name: 'admin-users', label: '用户管理', icon: 'User', admin: true },
      { name: 'profile', label: '个人中心', icon: 'Setting' }
    ]
  }
]

export const PAY_METHODS = ['微信', '支付宝', '银行卡', '信用卡', '现金', '云闪付']

export const BILL_TYPE_OPTIONS = [
  { value: 'EXPENSE', label: '支出' },
  { value: 'INCOME', label: '收入' }
]

export const SOURCE_LABELS = {
  MANUAL: '手动',
  AI: 'AI'
}
