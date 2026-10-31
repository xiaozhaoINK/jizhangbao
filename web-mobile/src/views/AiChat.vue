<template>
  <div class="page ai">
    <van-nav-bar title="AI 助手" fixed placeholder>
      <template #right>
        <span v-if="mode === 'chat'" class="ai__nav-btn" @click="startNewChat">新对话</span>
      </template>
    </van-nav-bar>

    <div class="ai__header">
      <div class="ai__avatar">
        <van-icon name="chat-o" size="20" color="#fff" />
      </div>
      <div class="ai__intro">
        <p class="ai__name">小账 · 你的财务助理</p>
        <p class="ai__desc">{{ status.description }}</p>
      </div>
      <van-tag :type="status.llm ? 'primary' : 'warning'" plain round size="medium">
        {{ status.llm ? '大模型模式' : '规则模式' }}
      </van-tag>
    </div>

    <div class="ai__mode">
      <span :class="{ active: mode === 'chat' }" @click="mode = 'chat'">财务问答</span>
      <span :class="{ active: mode === 'bill' }" @click="mode = 'bill'">一句话记账</span>
    </div>

    <!-- 财务问答 -->
    <template v-if="mode === 'chat'">
      <div ref="scrollRef" class="ai__messages">
        <div v-if="!messages.length" class="ai__empty">
          <p class="ai__empty-title">你好，我是小账 👋</p>
          <p class="ai__empty-desc">
            我已经读过你本月的账本了，可以问我任何消费相关的问题
          </p>
        </div>
        <div
          v-for="(item, index) in messages"
          :key="index"
          class="ai__bubble"
          :class="item.role === 'user' ? 'is-user' : 'is-ai'"
        >
          <div class="ai__bubble-text">{{ item.content }}<span v-if="item.streaming" class="ai__cursor">▍</span></div>
        </div>
      </div>

      <div class="ai__quick">
        <span v-for="q in quickQuestions" :key="q" class="ai__chip" @click="ask(q)">{{ q }}</span>
      </div>

      <div class="ai__input">
        <van-field
          v-model="input"
          placeholder="问问你的钱都花哪儿了…"
          :disabled="sending"
          @keyup.enter="send"
        />
        <van-button
          type="primary"
          size="small"
          round
          :loading="sending"
          :disabled="!input.trim()"
          @click="send"
        >
          发送
        </van-button>
      </div>
    </template>

    <!-- 一句话记账 -->
    <template v-else>
      <div class="ai__bill-input">
        <p class="ai__bill-tip">用一句话记账，例如「昨天午饭花了 30」</p>
        <van-field
          v-model="billText"
          type="textarea"
          rows="2"
          autosize
          maxlength="200"
          show-word-limit
          placeholder="昨天打车 28，用微信付的"
        />
        <div class="ai__bill-actions">
          <span v-for="sample in samples" :key="sample" class="ai__chip" @click="billText = sample">
            {{ sample }}
          </span>
        </div>
        <van-button
          type="primary"
          block
          round
          :loading="parsing"
          :disabled="!billText.trim()"
          class="ai__bill-btn"
          @click="parse"
        >
          识别一下
        </van-button>
      </div>

      <div v-if="parsed" class="card ai__result">
        <div class="card__title">
          <span>识别结果</span>
          <van-tag :type="parsed.complete ? 'success' : 'warning'" plain size="medium">
            置信度 {{ Math.round((parsed.confidence || 0) * 100) }}%
          </van-tag>
        </div>

        <div class="ai__result-amount" :class="parsed.type === 'INCOME' ? 'text-income' : 'text-expense'">
          {{ parsed.type === 'INCOME' ? '+' : '-' }}¥{{ money(parsed.amount) }}
        </div>

        <van-cell-group inset>
          <van-cell title="类型" :value="parsed.typeLabel" />
          <van-cell title="分类" is-link :value="parsed.categoryName || '未识别'" @click="showCategoryPicker = true" />
          <van-cell title="日期" is-link :value="parsed.billDate" @click="editDate = true" />
          <van-cell title="备注">
            <template #value>
              <van-field v-model="parsed.remark" placeholder="补充备注" class="ai__result-field" />
            </template>
          </van-cell>
          <van-cell v-if="parsed.payMethod" title="支付方式" :value="parsed.payMethod" />
        </van-cell-group>

        <div v-if="parsed.hints?.length" class="ai__hints">
          <p v-for="hint in parsed.hints" :key="hint"><van-icon name="info-o" /> {{ hint }}</p>
        </div>

        <div class="ai__result-foot">
          <van-button plain round size="small" @click="parsed = null">重新输入</van-button>
          <van-button type="primary" round size="small" :loading="recording" @click="recordParsed">
            确认记账
          </van-button>
        </div>
      </div>

      <div v-else class="card ai__howto">
        <div class="card__title">它能听懂这些说法</div>
        <ul>
          <li>「昨天午饭花了 30」→ 午餐 · 30 元 · 昨天</li>
          <li>「用微信打车 28.5」→ 打车 · 28.5 元 · 微信</li>
          <li>「今天发工资 12000」→ 收入 · 基本工资</li>
          <li>「前天超市买菜 156」→ 日用百货</li>
        </ul>
      </div>
    </template>

    <van-popup v-model:show="showCategoryPicker" position="bottom" round>
      <div class="ai__picker">
        <div class="ai__picker-head">
          <span>选择分类</span>
          <van-icon name="cross" @click="showCategoryPicker = false" />
        </div>
        <div class="ai__picker-body">
          <div
            v-for="item in flatCategories"
            :key="item.id"
            class="ai__picker-item"
            :class="{ active: item.id === parsed?.categoryId }"
            @click="pickCategory(item)"
          >
            <van-icon :name="iconName(item.icon)" :color="item.color" size="16" />
            <span>{{ item.parentId > 0 ? `　${item.name}` : item.name }}</span>
          </div>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="editDate" position="bottom" round>
      <van-date-picker
        v-model="datePickerValue"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onDateConfirm"
        @cancel="editDate = false"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { showToast } from 'vant'
import { aiApi, categoryApi, streamChat } from '@/api'
import { money, today } from '@/utils/format'

const ICON_MAP = {
  food: 'shop-o',
  transport: 'logistics',
  shopping: 'gift-o',
  home: 'wap-home-o',
  entertainment: 'music-o',
  medical: 'plus',
  education: 'records',
  gift: 'gift-card-o',
  salary: 'gold-coin-o',
  other: 'ellipsis',
  'other-income': 'ellipsis'
}
const iconName = (icon) => ICON_MAP[icon] || 'label-o'

const mode = ref('chat')
const status = ref({ llm: false, description: '正在连接 AI…' })
const messages = ref([])
const input = ref('')
const sending = ref(false)
const scrollRef = ref(null)
const conversationId = ref(null)

const billText = ref('')
const parsed = ref(null)
const parsing = ref(false)
const recording = ref(false)
const flatCategories = ref([])
const showCategoryPicker = ref(false)
const editDate = ref(false)
const maxDate = new Date()
const minDate = new Date(new Date().getFullYear() - 3, 0, 1)
const datePickerValue = ref(today().split('-'))

const quickQuestions = ['本月花了多少', '哪类花得最多', '预算还剩多少', '怎么才能省点钱', '上月对比如何']

const samples = ['昨天午饭花了 30', '打车 28.5 微信付', '今天发工资 12000', '前天超市买菜 156']

const scrollToBottom = async () => {
  await nextTick()
  if (scrollRef.value) {
    scrollRef.value.scrollTop = scrollRef.value.scrollHeight
  }
}

const startNewChat = () => {
  conversationId.value = null
  messages.value = []
  showToast('已开启新对话')
}

const send = async () => {
  const question = input.value.trim()
  if (!question || sending.value) return
  input.value = ''
  messages.value.push({ role: 'user', content: question })
  const aiMessage = { role: 'assistant', content: '', streaming: true }
  messages.value.push(aiMessage)
  sending.value = true
  await scrollToBottom()

  try {
    await streamChat(
      { conversationId: conversationId.value, message: question },
      {
        onMeta: (data) => {
          conversationId.value = data.conversationId
        },
        onDelta: (chunk) => {
          aiMessage.content += chunk
          scrollToBottom()
        },
        onDone: () => {
          aiMessage.streaming = false
        },
        onError: (data) => {
          aiMessage.content = data.message || 'AI 服务暂时不可用'
          aiMessage.streaming = false
        }
      }
    )
  } catch (e) {
    aiMessage.content = '连接 AI 服务失败，请检查后端是否已启动'
    aiMessage.streaming = false
  } finally {
    sending.value = false
    aiMessage.streaming = false
    await scrollToBottom()
  }
}

const ask = (question) => {
  input.value = question
  send()
}

const loadStatus = async () => {
  try {
    status.value = await aiApi.status()
  } catch (e) {
    status.value = { llm: false, description: 'AI 状态获取失败' }
  }
}

const loadCategories = async () => {
  try {
    flatCategories.value = (await categoryApi.flat()) || []
  } catch (e) {
    flatCategories.value = []
  }
}

const parse = async () => {
  if (!billText.value.trim()) return
  parsing.value = true
  parsed.value = null
  try {
    parsed.value = await aiApi.parse(billText.value.trim())
    if (!parsed.value.amount) {
      showToast({ message: '没识别到金额，请说明具体花了多少', position: 'top' })
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    parsing.value = false
  }
}

const pickCategory = (item) => {
  if (!parsed.value) return
  parsed.value.categoryId = item.id
  parsed.value.categoryName = item.name
  parsed.value.type = item.type
  parsed.value.typeLabel = item.type === 'INCOME' ? '收入' : '支出'
  showCategoryPicker.value = false
}

const onDateConfirm = ({ selectedValues }) => {
  parsed.value.billDate = selectedValues.join('-')
  editDate.value = false
}

const recordParsed = async () => {
  if (!parsed.value) return
  if (!parsed.value.amount) {
    showToast('请补充金额')
    return
  }
  if (!parsed.value.categoryId) {
    showToast('请选择分类')
    return
  }
  recording.value = true
  try {
    const bill = await aiApi.record(billText.value.trim())
    // 若用户在结果卡片上调整过分类/日期，这里以卡片为准再更新一次
    if (
      bill &&
      (bill.categoryId !== parsed.value.categoryId || bill.billDate !== parsed.value.billDate)
    ) {
      const { billApi } = await import('@/api')
      await billApi.update(bill.id, {
        type: parsed.value.type,
        categoryId: parsed.value.categoryId,
        amount: Number(parsed.value.amount),
        billDate: parsed.value.billDate,
        remark: parsed.value.remark || undefined,
        payMethod: parsed.value.payMethod || undefined
      })
    }
    showToast({ message: '记账成功', position: 'top' })
    parsed.value = null
    billText.value = ''
  } catch (e) {
    // 拦截器已提示
  } finally {
    recording.value = false
  }
}

watch(mode, () => {
  if (mode.value === 'bill' && !flatCategories.value.length) {
    loadCategories()
  }
})

onMounted(() => {
  loadStatus()
  loadCategories()
})
</script>

<style scoped>
.ai {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding-bottom: 70px;
}

.ai__nav-btn {
  font-size: 13px;
  color: #4f6ef7;
}

.ai__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #4f6ef7, #7c5cff);
  color: #fff;
  margin: 0 14px;
  border-radius: 16px;
  margin-top: 8px;
}

.ai__avatar {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ai__intro {
  flex: 1;
  min-width: 0;
}

.ai__name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
}

.ai__desc {
  margin: 2px 0 0;
  font-size: 11px;
  opacity: 0.85;
}

.ai__mode {
  display: flex;
  gap: 8px;
  margin: 12px 14px 0;
  background: #fff;
  padding: 4px;
  border-radius: 12px;
  box-shadow: var(--jzb-shadow);
}

.ai__mode span {
  flex: 1;
  text-align: center;
  font-size: 13px;
  padding: 8px 0;
  border-radius: 9px;
  color: #6b7699;
}

.ai__mode span.active {
  background: #eef1ff;
  color: #4f6ef7;
  font-weight: 600;
}

.ai__messages {
  flex: 1;
  overflow-y: auto;
  padding: 14px 14px 4px;
  max-height: calc(100vh - 320px);
  min-height: 240px;
}

.ai__empty {
  text-align: center;
  padding: 30px 20px;
}

.ai__empty-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 8px;
}

.ai__empty-desc {
  font-size: 12px;
  color: #8b95b3;
  margin: 0;
  line-height: 1.7;
}

.ai__bubble {
  display: flex;
  margin-bottom: 12px;
}

.ai__bubble.is-user {
  justify-content: flex-end;
}

.ai__bubble-text {
  max-width: 82%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai__bubble.is-user .ai__bubble-text {
  background: linear-gradient(135deg, #4f6ef7, #6f5cf6);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai__bubble.is-ai .ai__bubble-text {
  background: #fff;
  color: #1f2d5a;
  box-shadow: var(--jzb-shadow);
  border-bottom-left-radius: 4px;
}

.ai__cursor {
  animation: blink 1s infinite;
  color: #4f6ef7;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

.ai__quick {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 8px 14px;
  scrollbar-width: none;
}

.ai__quick::-webkit-scrollbar {
  display: none;
}

.ai__chip {
  flex-shrink: 0;
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #fff;
  color: #4f6ef7;
  border: 1px solid #e3e8ff;
}

.ai__input {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(56px + env(safe-area-inset-bottom));
  width: 100%;
  max-width: 560px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: #fff;
  border-top: 1px solid #f2f4fa;
}

.ai__input :deep(.van-field) {
  flex: 1;
  background: #f5f7fb;
  border-radius: 999px;
  padding: 8px 14px;
}

.ai__bill-input {
  background: #fff;
  margin: 12px 14px;
  border-radius: 14px;
  padding: 14px;
  box-shadow: var(--jzb-shadow);
}

.ai__bill-tip {
  margin: 0 0 10px;
  font-size: 12px;
  color: #8b95b3;
}

.ai__bill-input :deep(.van-field) {
  background: #f5f7fb;
  border-radius: 12px;
  padding: 10px 12px;
}

.ai__bill-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 10px 0 12px;
}

.ai__bill-btn {
  height: 42px;
  background: linear-gradient(135deg, #4f6ef7, #7c5cff);
  border: none;
}

.ai__result-amount {
  text-align: center;
  font-size: 30px;
  font-weight: 700;
  padding: 6px 0 12px;
}

.ai__result-field :deep(.van-field__control) {
  text-align: right;
}

.ai__hints {
  margin: 10px 16px 0;
  font-size: 12px;
  color: #ff9f0a;
}

.ai__hints p {
  margin: 4px 0;
}

.ai__result-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}

.ai__howto ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: #4a5578;
  line-height: 2;
}

.ai__picker {
  max-height: 60vh;
  display: flex;
  flex-direction: column;
}

.ai__picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid #f2f4fa;
}

.ai__picker-body {
  overflow-y: auto;
  padding: 6px 0 16px;
}

.ai__picker-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 18px;
  font-size: 14px;
}

.ai__picker-item.active {
  background: #f5f7ff;
  color: #4f6ef7;
  font-weight: 600;
}
</style>
