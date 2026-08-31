<script setup lang="ts">
import { ref, computed } from 'vue'
import { Upload, FileText, Download, Loader2, CircleCheck, CircleX } from 'lucide-vue-next'

const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const file = ref<File | null>(null)
const taskId = ref('')
const status = ref('')
const progress = ref(0)
const message = ref('')
const error = ref('')
let timer: number | undefined

const busy = computed(() => ['QUEUED', 'PROCESSING'].includes(status.value))
const resultUrl = computed(() => taskId.value ? `${apiBase}/api/v2/tasks/${taskId.value}/result` : '')

function choose(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (f) setFile(f)
}
function setFile(f: File) {
  file.value = f
  error.value = ''
  status.value = ''
  progress.value = 0
}
function drop(e: DragEvent) {
  const f = e.dataTransfer?.files?.[0]
  if (f) setFile(f)
}
async function convert() {
  if (!file.value || busy.value) return
  error.value = ''
  const form = new FormData()
  form.append('file', file.value)
  try {
    const response = await fetch(`${apiBase}/api/v2/pdf/to-word`, { method: 'POST', body: form })
    if (!response.ok) throw new Error(await response.text() || '创建转换任务失败')
    const task = await response.json()
    taskId.value = task.id
    status.value = task.status
    progress.value = task.progress ?? 0
    message.value = task.progressMessage || '任务已创建'
    poll()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '转换失败'
  }
}
function poll() {
  if (timer) window.clearInterval(timer)
  const run = async () => {
    try {
      const response = await fetch(`${apiBase}/api/v2/tasks/${taskId.value}`, { cache: 'no-store' })
      if (!response.ok) throw new Error('无法读取任务状态')
      const task = await response.json()
      status.value = task.status
      progress.value = task.progress ?? 0
      message.value = task.progressMessage || ''
      if (task.status === 'SUCCESS' || task.status === 'FAILED') {
        if (timer) window.clearInterval(timer)
        if (task.status === 'FAILED') error.value = task.error || '转换失败'
      }
    } catch (e) {
      if (timer) window.clearInterval(timer)
      error.value = e instanceof Error ? e.message : '读取任务状态失败'
    }
  }
  run()
  timer = window.setInterval(run, 1000)
}
</script>

<template>
  <div class="tool">
    <div class="tool-head"><div><h2>PDF → Word</h2><p>将 PDF 转换为可编辑的 DOCX 文档</p></div><FileText :size="28"/></div>
    <label class="drop" @dragover.prevent @drop.prevent="drop">
      <Upload :size="28"/><b>{{ file ? file.name : '拖拽 PDF 到这里' }}</b><span>{{ file ? `${(file.size / 1024 / 1024).toFixed(2)} MB` : '或点击选择 PDF 文件' }}</span>
      <input type="file" accept="application/pdf,.pdf" @change="choose" hidden />
    </label>
    <button class="convert" :disabled="!file || busy" @click="convert"><Loader2 v-if="busy" class="spin" :size="17"/><span v-else>开始转换</span><span v-if="busy">{{ progress }}%</span></button>
    <div v-if="status" class="state">
      <div class="state-line"><span>{{ message }}</span><b>{{ progress }}%</b></div>
      <div class="bar"><i :style="{ width: `${progress}%` }"></i></div>
      <div v-if="status === 'SUCCESS'" class="success"><CircleCheck :size="18"/> 转换完成 · <a :href="resultUrl">下载 DOCX</a><Download :size="16"/></div>
      <div v-else-if="status === 'FAILED'" class="failure"><CircleX :size="18"/> {{ error || '转换失败' }}</div>
    </div>
    <p v-if="error && status !== 'FAILED'" class="failure">{{ error }}</p>
  </div>
</template>

<style scoped>
.tool{padding:28px}.tool-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:22px}.tool-head h2{margin:0 0 5px;font-size:24px}.tool-head p{margin:0;color:#778097}.drop{min-height:190px;border:2px dashed #d8ddea;border-radius:14px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:9px;cursor:pointer}.drop:hover{border-color:#7c6ee6;background:#faf9ff}.drop b{font-size:15px}.drop span{font-size:12px;color:#8991a2}.convert{margin-top:16px;width:100%;height:44px;border:0;border-radius:10px;background:#6658d9;color:white;font-weight:600;cursor:pointer;display:flex;justify-content:center;align-items:center;gap:10px}.convert:disabled{opacity:.55;cursor:not-allowed}.state{margin-top:20px}.state-line{display:flex;justify-content:space-between;font-size:13px}.bar{height:7px;background:#edf0f6;border-radius:10px;overflow:hidden;margin-top:8px}.bar i{display:block;height:100%;background:#6658d9;transition:width .3s}.success,.failure{display:flex;align-items:center;gap:7px;margin-top:14px;font-size:13px}.success{color:#348354}.success a{color:inherit;font-weight:700}.failure{color:#c84f5a}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
</style>
