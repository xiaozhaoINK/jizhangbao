<template>
  <div class="page page--plain categories">
    <van-nav-bar title="分类管理" left-arrow fixed placeholder @click-left="router.back()">
      <template #right>
        <span class="categories__nav" @click="openCreate(null)">添加</span>
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="type" color="#4f6ef7" @change="load">
      <van-tab title="支出分类" name="EXPENSE" />
      <van-tab title="收入分类" name="INCOME" />
    </van-tabs>

    <div class="categories__list">
      <div v-for="parent in categories" :key="parent.id" class="card categories__group">
        <div class="categories__parent">
          <div class="categories__parent-main">
            <div class="categories__icon" :style="{ background: `${parent.color}1a` }">
              <van-icon :name="iconName(parent.icon)" :color="parent.color" size="18" />
            </div>
            <div>
              <p class="categories__name">
                {{ parent.name }}
                <van-tag v-if="parent.system" plain size="mini" type="primary">内置</van-tag>
              </p>
              <p class="categories__count">
                {{ parent.billCount || 0 }} 笔账单 · {{ parent.children?.length || 0 }} 个子分类
              </p>
            </div>
          </div>
          <div class="categories__actions">
            <van-icon name="plus" size="16" @click="openCreate(parent)" />
            <van-icon name="edit" size="16" @click="openEdit(parent)" />
            <van-icon name="delete-o" size="16" @click="remove(parent)" />
          </div>
        </div>

        <div class="categories__children">
          <van-swipe-cell v-for="child in parent.children" :key="child.id">
            <div class="categories__child" @click="openEdit(child)">
              <span class="ellipsis">{{ child.name }}</span>
              <span class="text-weak">{{ child.billCount || 0 }} 笔</span>
            </div>
            <template #right>
              <van-button square type="danger" text="删除" class="categories__del" @click="remove(child)" />
            </template>
          </van-swipe-cell>
          <p v-if="!parent.children?.length" class="categories__empty">暂无子分类</p>
        </div>
      </div>

      <van-empty v-if="!categories.length" description="还没有分类" />
    </div>

    <van-popup v-model:show="showForm" position="bottom" round>
      <div class="categories__form">
        <p class="categories__form-title">{{ form.id ? '编辑分类' : '新增分类' }}</p>
        <van-field v-model="form.name" label="名称" placeholder="如：晚餐" maxlength="10" />
        <van-field
          v-if="!form.id"
          :model-value="form.parentName || '作为一级分类'"
          label="上级分类"
          readonly
          is-link
          @click="showParentPicker = true"
        />
        <van-field label="颜色">
          <template #input>
            <div class="categories__colors">
              <span
                v-for="color in colorOptions"
                :key="color"
                class="categories__color"
                :style="{ background: color }"
                :class="{ active: form.color === color }"
                @click="form.color = color"
              />
            </div>
          </template>
        </van-field>
        <div class="categories__form-btn">
          <van-button round plain @click="showForm = false">取消</van-button>
          <van-button type="primary" round :loading="saving" @click="save">保存</van-button>
        </div>
      </div>
    </van-popup>

    <van-popup v-model:show="showParentPicker" position="bottom" round>
      <div class="categories__picker">
        <div class="categories__picker-head">
          <span>上级分类</span>
          <van-icon name="cross" @click="showParentPicker = false" />
        </div>
        <div class="categories__picker-item" @click="pickParent(null)">作为一级分类</div>
        <div
          v-for="parent in categories"
          :key="parent.id"
          class="categories__picker-item"
          @click="pickParent(parent)"
        >
          {{ parent.name }}
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { categoryApi } from '@/api'

const router = useRouter()

const type = ref('EXPENSE')
const categories = ref([])
const showForm = ref(false)
const showParentPicker = ref(false)
const saving = ref(false)
const colorOptions = ['#4F6EF7', '#FF7A45', '#00B578', '#9254DE', '#FFC53D', '#36CFC9', '#F759AB', '#8C8C8C']

const form = reactive({
  id: null,
  name: '',
  type: 'EXPENSE',
  parentId: 0,
  parentName: '',
  icon: 'other',
  color: '#4F6EF7',
  sort: 100
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

const load = async () => {
  try {
    categories.value = (await categoryApi.tree(type.value)) || []
  } catch (e) {
    categories.value = []
  }
}

const openCreate = (parent) => {
  form.id = null
  form.name = ''
  form.type = type.value
  form.parentId = parent?.id || 0
  form.parentName = parent?.name || ''
  form.icon = parent?.icon || 'other'
  form.color = parent?.color || '#4F6EF7'
  form.sort = 100
  showForm.value = true
}

const openEdit = (category) => {
  form.id = category.id
  form.name = category.name
  form.type = category.type
  form.parentId = category.parentId
  form.parentName = ''
  form.icon = category.icon || 'other'
  form.color = category.color || '#4F6EF7'
  form.sort = category.sort || 100
  showForm.value = true
}

const pickParent = (parent) => {
  form.parentId = parent?.id || 0
  form.parentName = parent?.name || ''
  showParentPicker.value = false
}

const save = async () => {
  if (!form.name.trim()) {
    showToast('请输入分类名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      type: form.type,
      parentId: form.parentId,
      icon: form.icon,
      color: form.color,
      sort: form.sort
    }
    if (form.id) {
      await categoryApi.update(form.id, payload)
    } else {
      await categoryApi.create(payload)
    }
    showToast({ message: '已保存', position: 'top' })
    showForm.value = false
    await load()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

const remove = async (category) => {
  try {
    await showConfirmDialog({
      title: '删除分类',
      message: `确定删除「${category.name}」吗？`,
      confirmButtonColor: '#ff5b5b'
    })
    await categoryApi.remove(category.id)
    showToast({ message: '已删除', position: 'top' })
    await load()
  } catch (e) {
    // 取消或后端拒绝（已有账单 / 系统分类）
  }
}

onMounted(load)
</script>

<style scoped>
.categories__nav {
  font-size: 13px;
  color: #4f6ef7;
}

.categories__list {
  padding-bottom: 24px;
}

.categories__group {
  padding: 12px 14px 6px;
}

.categories__parent {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.categories__parent-main {
  display: flex;
  align-items: center;
  gap: 10px;
}

.categories__icon {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.categories__name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.categories__count {
  margin: 3px 0 0;
  font-size: 11px;
  color: #8b95b3;
}

.categories__actions {
  display: flex;
  gap: 14px;
  color: #8b95b3;
}

.categories__children {
  margin-top: 8px;
  border-top: 1px solid #f4f6fb;
}

.categories__child {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 2px;
  font-size: 14px;
  border-bottom: 1px solid #f8f9fd;
}

.categories__child:last-child {
  border-bottom: none;
}

.categories__del {
  height: 100%;
}

.categories__empty {
  font-size: 12px;
  color: #b4bcd4;
  text-align: center;
  padding: 12px 0;
  margin: 0;
}

.categories__form {
  padding: 18px 0 24px;
}

.categories__form-title {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px;
}

.categories__colors {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.categories__color {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: inline-block;
  border: 2px solid transparent;
}

.categories__color.active {
  border-color: #1f2d5a;
  transform: scale(1.1);
}

.categories__form-btn {
  display: flex;
  gap: 12px;
  padding: 20px 18px 0;
}

.categories__form-btn :deep(.van-button) {
  flex: 1;
}

.categories__picker {
  max-height: 60vh;
  display: flex;
  flex-direction: column;
}

.categories__picker-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  font-size: 15px;
  font-weight: 600;
  border-bottom: 1px solid #f2f4fa;
}

.categories__picker-item {
  padding: 13px 18px;
  font-size: 14px;
}
</style>
