<script setup lang="ts">
import { computed } from 'vue'

import { ApiError } from '@/types/api'

const props = defineProps<{ error: Error | null; title?: string }>()
defineEmits<{ retry: [] }>()

const apiError = computed(() => (props.error instanceof ApiError ? props.error : null))
</script>

<template>
  <el-alert :title="title ?? '数据加载失败'" type="error" :closable="false" show-icon>
    <p>{{ error?.message ?? '未知错误' }}</p>
    <p v-if="apiError?.errorCode">错误代码：{{ apiError.errorCode }}</p>
    <p v-if="apiError?.requestId">请求编号：{{ apiError.requestId }}</p>
    <el-button type="primary" plain @click="$emit('retry')">重新加载</el-button>
  </el-alert>
</template>
