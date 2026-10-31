const ENGINE_LABELS = {
  LLM: '大模型',
  RULE: '规则引擎'
}

const SCENE_LABELS = {
  PARSE: '智能解析',
  CHAT: '智能问答',
  ANALYSIS: '消费洞察',
  REPORT: '报告生成'
}

export function aiEngineLabel(value, fallback = '智能引擎') {
  if (!value) return fallback
  return ENGINE_LABELS[value] || value
}

export function aiSceneLabel(value, fallback = '未分类') {
  if (!value) return fallback
  return SCENE_LABELS[value] || value
}
