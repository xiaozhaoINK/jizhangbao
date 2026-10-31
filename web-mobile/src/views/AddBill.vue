<template>
  <div class="page page--plain add-bill">
    <van-nav-bar
      :title="isEdit ? '编辑账单' : '记一笔'"
      left-arrow
      fixed
      placeholder
      @click-left="router.back()"
    >
      <template #right>
        <span class="add-bill__save" :class="{ disabled: !canSave }" @click="save">保存</span>
      </template>
    </van-nav-bar>

    <div class="add-bill__type">
      <div
        class="add-bill__type-item"
        :class="{ active: form.type === 'EXPENSE' }"
        @click="switchType('EXPENSE')"
      >
        支出
      </div>
      <div
        class="add-bill__type-item"
        :class="{ active: form.type === 'INCOME' }"
        @click="switchType('INCOME')"
      >
        收入
      </div>
    </div>

    <div class="add-bill__amount">
      <span class="add-bill__currency">¥</span>
      <span class="add-bill__value num" :class="{ placeholder: !amountText }">
        {{ amountText || '0.00' }}
      </span>
      <span v-if="activeCategoryName" class="add-bill__cat">{{ activeCategoryName }}</span>
    </div>

    <div class="card add-bill__cats">
      <div class="add-bill__parents">
        <div
          v-for="parent in categories"
          :key="parent.id"
          class="add-bill__parent"
          :class="{ active: activeParentId === parent.id }"
          @click="selectParent(parent.id)"
        >
          {{ parent.name }}
        </div>
      </div>
      <div class="add-bill__children">
        <div
          v-for="child in children"
          :key="child.id"
          class="add-bill__child"
          :class="{ active: form.categoryId === child.id }"
          @click="form.categoryId = child.id"
        >
          <div class="add-bill__child-icon" :style="{ background: `${child.color}1a` }">
            <van-icon :name="iconName(child.icon)" :color="child.color" size="18" />
          </div>
          <span>{{ child.name }}</span>
        </div>
        <van-empty v-if="!children.length" description="该分类下暂无子分类" image-size="60" />
      </div>
    </div>

    <van-cell-group inset class="add-bill__form">
      <van-cell title="日期" is-link :value="form.billDate" @click="showDatePicker = true" />
      <van-cell title="支付方式" is-link :value="form.payMethod || '请选择'" @click="showPaySheet = true" />
      <van-field
        v-model="form.remark"
        label="备注"
        placeholder="花在哪儿了？（选填）"
        maxlength="50"
        show-word-limit
      />
    </van-cell-group>

    <div v-if="voiceHint" class="add-bill__hint">
      <van-icon name="info-o" /> {{ voiceHint }}
    </div>

    <van-number-keyboard
      v-model="amountText"
      :show="keyboardVisible"
      theme="custom"
      extra-key="."
      close-button-text="完成"
      :maxlength="9"
      @blur="keyboardVisible = false"
    />

    <van-popup v-model:show="showDatePicker" position="bottom" round>
      <van-date-picker
        v-model="datePickerValue"
        title="选择日期"
        :min-date="minDate"
        :max-date="maxDate"
        @confirm="onDateConfirm"
        @cancel="showDatePicker = false"
      />
    </van-popup>

    <van-action-sheet
      v-model:show="showPaySheet"
      :actions="payActions"
      cancel-text="取消"
      title="选择支付方式"
      close-on-click-action
      @select="onPaySelect"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { billApi, categoryApi, metaApi } from '@/api'
import { today } from '@/utils/format'

const router = useRouter()
const route = useRoute()

const isEdit = computed(() => Boolean(route.params.id))
const categories = ref([])
const activeParentId = ref(null)
const amountText = ref('')
const keyboardVisible = ref(true)
const showDatePicker = ref(false)
const showPaySheet = ref(false)
const payActions = ref([])
const voiceHint = ref('')
const maxDate = new Date()
const minDate = new Date(new Date().getFullYear() - 3, 0, 1)
const datePickerValue = ref([])

const form = ref({
  type: 'EXPENSE',
  categoryId: null,
  billDate: today(),
  remark: '',
  payMethod: ''
})

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

const children = computed(() => {
  const parent = categories.value.find((item) => item.id === activeParentId.value)
  return parent?.children?.length ? parent.children : parent ? [parent] : []
})

const activeCategoryName = computed(() => {
  for (const parent of categories.value) {
    if (parent.id === form.value.categoryId) return parent.name
    const child = parent.children?.find((item) => item.id === form.value.categoryId)
    if (child) return `${parent.name} · ${child.name}`
  }
  return ''
})

const canSave = computed(() => Number(amountText.value) > 0 && form.value.categoryId)

const loadCategories = async (keepCategory = false) => {
  const data = await categoryApi.tree(form.value.type)
  categories.value = data || []
  const hasCategory = categories.value.some(
    (parent) =>
      parent.id === form.value.categoryId ||
      parent.children?.some((child) => child.id === form.value.categoryId)
  )
  if (!keepCategory || !hasCategory) {
    const first = categories.value[0]
    activeParentId.value = first?.id || null
    form.value.categoryId = first?.children?.[0]?.id || first?.id || null
  } else {
    const parent = categories.value.find(
      (item) =>
        item.id === form.value.categoryId ||
        item.children?.some((child) => child.id === form.value.categoryId)
    )
    activeParentId.value = parent?.id || categories.value[0]?.id || null
  }
}

const switchType = async (type) => {
  if (form.value.type === type) return
  form.value.type = type
  form.value.categoryId = null
  await loadCategories()
}

const selectParent = (id) => {
  activeParentId.value = id
  const parent = categories.value.find((item) => item.id === id)
  form.value.categoryId = parent?.children?.[0]?.id || parent?.id || null
}

const onDateConfirm = ({ selectedValues }) => {
  form.value.billDate = selectedValues.join('-')
  showDatePicker.value = false
}

const onPaySelect = (action) => {
  form.value.payMethod = action.name
  showPaySheet.value = false
}

watch(showDatePicker, (visible) => {
  if (visible) {
    datePickerValue.value = form.value.billDate.split('-')
  }
})

watch(keyboardVisible, (visible) => {
  if (!visible) voiceHint.value = ''
})

const save = async () => {
  if (!canSave.value) {
    showToast('请填写金额并选择分类')
    keyboardVisible.value = true
    return
  }
  const payload = {
    type: form.value.type,
    categoryId: form.value.categoryId,
    amount: Number(amountText.value),
    billDate: form.value.billDate,
    remark: form.value.remark || undefined,
    payMethod: form.value.payMethod || undefined
  }
  try {
    if (isEdit.value) {
      await billApi.update(route.params.id, payload)
      showToast({ message: '修改成功', position: 'top' })
    } else {
      await billApi.create(payload)
      showToast({ message: '记账成功', position: 'top' })
    }
    router.back()
  } catch (e) {
    // 拦截器已提示
  }
}

const loadDicts = async () => {
  try {
    const dicts = await metaApi.dicts()
    payActions.value = (dicts.payMethods || []).map((name) => ({ name }))
  } catch (e) {
    payActions.value = ['微信', '支付宝', '银行卡', '现金'].map((name) => ({ name }))
  }
}

const loadBill = async () => {
  const bill = await billApi.detail(route.params.id)
  form.value.type = bill.type
  form.value.billDate = bill.billDate
  form.value.remark = bill.remark || ''
  form.value.payMethod = bill.payMethod || ''
  amountText.value = String(bill.amount)
  await loadCategories(true)
  form.value.categoryId = bill.categoryId
  const parent = categories.value.find(
    (item) => item.id === bill.categoryId || item.children?.some((c) => c.id === bill.categoryId)
  )
  activeParentId.value = parent?.id || null
}

onMounted(async () => {
  try {
    if (isEdit.value) {
      await loadBill()
    } else {
      await loadCategories()
    }
    await loadDicts()
  } catch (e) {
    // 拦截器已提示
  }
})
</script>

<style scoped>
.add-bill {
  padding-bottom: 300px;
  background: #f5f7fb;
}

.add-bill__save {
  color: #4f6ef7;
  font-weight: 600;
  font-size: 15px;
}

.add-bill__save.disabled {
  color: #c3c9db;
}

.add-bill__type {
  display: flex;
  justify-content: center;
  gap: 10px;
  padding: 14px 0 4px;
}

.add-bill__type-item {
  padding: 6px 22px;
  border-radius: 999px;
  font-size: 14px;
  color: #6b7699;
  background: #fff;
  transition: all 0.2s;
}

.add-bill__type-item.active {
  background: linear-gradient(135deg, #4f6ef7, #7c5cff);
  color: #fff;
  font-weight: 600;
}

.add-bill__amount {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 4px;
  padding: 18px 0 10px;
  position: relative;
}

.add-bill__currency {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d5a;
}

.add-bill__value {
  font-size: 40px;
  font-weight: 700;
  color: #1f2d5a;
  letter-spacing: 1px;
  min-width: 60px;
  text-align: center;
}

.add-bill__value.placeholder {
  color: #c3c9db;
}

.add-bill__cat {
  position: absolute;
  right: 18px;
  bottom: 14px;
  font-size: 12px;
  color: #4f6ef7;
  background: #eef1ff;
  padding: 4px 10px;
  border-radius: 999px;
}

.add-bill__cats {
  padding: 12px 0 4px;
}

.add-bill__parents {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 0 14px 10px;
  scrollbar-width: none;
}

.add-bill__parents::-webkit-scrollbar {
  display: none;
}

.add-bill__parent {
  flex-shrink: 0;
  padding: 6px 14px;
  border-radius: 999px;
  background: #f3f5fb;
  color: #6b7699;
  font-size: 13px;
}

.add-bill__parent.active {
  background: #eef1ff;
  color: #4f6ef7;
  font-weight: 600;
}

.add-bill__children {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px 4px;
  padding: 10px 10px 6px;
}

.add-bill__child {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #4a5578;
  padding: 4px 0;
  border-radius: 10px;
}

.add-bill__child.active {
  background: #f5f7ff;
  color: #4f6ef7;
  font-weight: 600;
}

.add-bill__child-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-bill__form {
  margin-top: 4px;
}

.add-bill__hint {
  margin: 12px 20px;
  font-size: 12px;
  color: #8b95b3;
}
</style>
