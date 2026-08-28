<script setup lang="ts">
import { ref } from 'vue'
import { FileText, Upload, Download, Loader2, X } from 'lucide-vue-next'

const files = ref<File[]>([])
const busy = ref(false)
const error = ref('')
const url = ref('')
const name = ref('officebox-result.pdf')
const pages = ref('1-3')
const encryptPassword = ref('')
const decryptPassword = ref('')
const api = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

function clearResult() {
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
}

function pick(e: Event) {
  files.value = Array.from((e.target as HTMLInputElement).files || [])
  error.value = ''
  clearResult()
}

function remove(index: number) {
  files.value.splice(index, 1)
  error.value = ''
  clearResult()
}

function checkSingle() {
  if (!files.value.length) { error.value = '请选择 PDF'; return false }
  if (files.value.length !== 1) { error.value = '此操作只能选择一个 PDF'; return false }
  return true
}

async function run(path: string, field = 'file', extra: Record<string, string> = {}, outputName?: string) {
  if (!files.value.length) { error.value = '请选择 PDF'; return }
  busy.value = true
  error.value = ''
  clearResult()
  try {
    const form = new FormData()
    if (field === 'files') files.value.forEach(file => form.append('files', file))
    else form.append(field, files.value[0])
    Object.entries(extra).forEach(([key, value]) => form.append(key, value))

    const response = await fetch(`${api}${path}`, { method: 'POST', body: form })
    if (!response.ok) {
      let message = 'PDF 操作失败'
      try { message = (await response.text()) || message } catch {}
      if (response.status === 400) message = message.includes('密码') ? message : '参数错误，请检查文件和输入内容'
      throw Error(message)
    }
    const blob = await response.blob()
    url.value = URL.createObjectURL(blob)
    name.value = outputName || (path.includes('merge') ? 'officebox-merged.pdf' : path.includes('extract-pages') ? 'officebox-pages.zip' : path.includes('rotate') ? 'officebox-rotated.pdf' : path.includes('compress') ? 'officebox-compressed.pdf' : path.includes('encrypt') ? 'officebox-encrypted.pdf' : 'officebox-decrypted.pdf')
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'PDF 操作失败'
  } finally {
    busy.value = false
  }
}

function merge() { if (files.value.length < 2) { error.value = '合并至少需要选择 2 个 PDF'; return } run('/api/pdf/merge', 'files') }
function compress() { if (checkSingle()) run('/api/pdf/compress') }
function rotate() { if (checkSingle()) run('/api/pdf/rotate', 'file', { degrees: '90' }) }
function split() {
  if (!checkSingle()) return
  if (!pages.value.trim()) { error.value = '请输入要拆分的页码，例如：1-3,5,8-10'; return }
  run('/api/pdf/extract-pages', 'file', { pages: pages.value }, 'officebox-pages.zip')
}
function encrypt() {
  if (!checkSingle()) return
  if (!encryptPassword.value) { error.value = '请输入加密密码'; return }
  run('/api/pdf/encrypt', 'file', { password: encryptPassword.value }, 'officebox-encrypted.pdf')
}
function decrypt() {
  if (!checkSingle()) return
  if (!decryptPassword.value) { error.value = '请输入 PDF 密码'; return }
  run('/api/pdf/decrypt', 'file', { password: decryptPassword.value }, 'officebox-decrypted.pdf')
}
</script>

<template>
  <div class="pdf">
    <div class="head">
      <FileText />
      <div><h1>PDF 工具箱</h1><p>合并、拆分、压缩、旋转、加密与解密。</p></div>
    </div>

    <label class="drop">
      <Upload :size="24" /><b>选择 PDF 文件</b><span>可多选，用于合并</span>
      <input type="file" accept=".pdf,application/pdf" multiple hidden @change="pick" />
    </label>

    <div v-if="files.length" class="info">
      <div class="info-title">已选择 {{ files.length }} 个 PDF</div>
      <div v-for="(f, index) in files" :key="`${f.name}-${index}`" class="file-row">
        <span class="file-name">{{ f.name }}</span>
        <button class="remove" type="button" title="移除" aria-label="移除文件" @click="remove(index)"><X :size="15" /></button>
      </div>
    </div>

    <div class="option-grid">
      <label><span>拆分页码</span><input v-model="pages" placeholder="例如 1-3,5,8-10" /></label>
      <label><span>加密密码</span><input v-model="encryptPassword" type="password" autocomplete="new-password" placeholder="设置新密码" /></label>
      <label><span>解密密码</span><input v-model="decryptPassword" type="password" autocomplete="current-password" placeholder="输入已有 PDF 密码" /></label>
    </div>

    <div class="tools">
      <button :disabled="busy" @click="merge">合并</button>
      <button :disabled="busy || files.length !== 1" @click="split">拆分</button>
      <button :disabled="busy || files.length !== 1" @click="rotate">旋转 90°</button>
      <button :disabled="busy || files.length !== 1" @click="compress">压缩</button>
      <button :disabled="busy || files.length !== 1" @click="encrypt">加密</button>
      <button :disabled="busy || files.length !== 1" @click="decrypt">解密</button>
    </div>

    <div class="hint">提示：PDF 解密需要输入正确的原密码；不知道密码时不能直接解除打开密码。加密时密码由你自己设置，不再使用固定密码。</div>
    <div v-if="error" class="error">{{ error }}</div>
    <a v-if="url" class="download" :href="url" :download="name"><Download :size="16" />下载结果</a>
    <Loader2 v-if="busy" class="spin" />
  </div>
</template>

<style scoped>
.pdf{max-width:900px;margin:auto;padding:30px 24px}.head{display:flex;gap:14px;align-items:center;margin-bottom:22px}.head h1{margin:0 0 5px;font-size:22px}.head p,.drop span{color:#8991a2;font-size:12px}.drop{min-height:170px;border:1.5px dashed #ccd1df;border-radius:15px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:9px;cursor:pointer;color:#6659df}.drop b{color:#343c4c;font-size:13px}.info{margin:14px 0;border:1px solid #e7e9f0;border-radius:10px;padding:10px 12px;font-size:12px}.info-title{color:#60697b;margin-bottom:7px}.file-row{display:flex;align-items:center;gap:8px;padding:6px 0;border-top:1px solid #f0f1f5}.file-name{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#8991a2}.remove{margin-left:auto;width:26px;height:26px;padding:0;border:0;border-radius:7px;background:transparent;color:#9aa2b1;display:grid;place-items:center;cursor:pointer}.remove:hover{background:#fff0f0;color:#c84f5a}.option-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:14px}.option-grid label{display:flex;flex-direction:column;gap:6px}.option-grid span{font-size:11px;color:#737c8e}.option-grid input{height:38px;border:1px solid #dfe2ea;border-radius:9px;padding:0 10px;outline:0;background:white;color:inherit}.tools{display:flex;flex-wrap:wrap;gap:9px;margin-top:18px}.tools button,.download{border:0;border-radius:9px;padding:10px 14px;background:#6659df;color:white;text-decoration:none;cursor:pointer}.tools button:disabled{opacity:.45;cursor:not-allowed}.hint{margin-top:12px;color:#8991a2;font-size:11px;line-height:1.6}.error{margin-top:14px;padding:10px;border-radius:8px;background:#fff0f0;color:#c84f5a;font-size:12px}.download{display:inline-flex;gap:7px;align-items:center;margin-top:18px;background:#eaf7ef;color:#348354}.spin{margin-top:16px;animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:700px){.option-grid{grid-template-columns:1fr}}
</style>
