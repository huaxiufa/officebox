<script setup lang="ts">
import { ref } from 'vue'
import { FileText, Upload, Download, Loader2, X, ShieldCheck, Scissors, Minimize2, KeyRound, FileOutput } from 'lucide-vue-next'

const files = ref<File[]>([])
const busy = ref(false)
const error = ref('')
const url = ref('')
const name = ref('officebox-result.pdf')
const pages = ref('1-3')
const encryptPassword = ref('')
const decryptPassword = ref('')
const mergePassword = ref('')
const compression = ref('ebook')
const api = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

function clearResult() { if (url.value) URL.revokeObjectURL(url.value); url.value = '' }
function pick(e: Event) { files.value = Array.from((e.target as HTMLInputElement).files || []); error.value = ''; clearResult() }
function remove(index: number) { files.value.splice(index, 1); error.value = ''; clearResult() }
function checkSingle() { if (!files.value.length) { error.value = '请选择 PDF'; return false }; if (files.value.length !== 1) { error.value = '此操作只能选择一个 PDF'; return false }; return true }
async function run(path: string, field = 'file', extra: Record<string, string> = {}, outputName?: string) {
  if (!files.value.length) { error.value = '请选择 PDF'; return }
  busy.value = true; error.value = ''; clearResult()
  try {
    const form = new FormData()
    if (field === 'files') files.value.forEach(file => form.append('files', file)); else form.append(field, files.value[0])
    Object.entries(extra).forEach(([key, value]) => form.append(key, value))
    const response = await fetch(`${api}${path}`, { method: 'POST', body: form })
    if (!response.ok) { const message = (await response.text()) || 'PDF 操作失败'; throw Error(message) }
    url.value = URL.createObjectURL(await response.blob())
    name.value = outputName || (path.includes('merge') ? 'officebox-merged.pdf' : path.includes('extract-pages') ? 'officebox-pages.zip' : path.includes('rotate') ? 'officebox-rotated.pdf' : path.includes('compress') ? 'officebox-compressed.pdf' : path.includes('encrypt') ? 'officebox-encrypted.pdf' : path.includes('decrypt') ? 'officebox-decrypted.pdf' : 'officebox-result.pdf')
  } catch (e) { error.value = e instanceof Error ? e.message : 'PDF 操作失败' } finally { busy.value = false }
}
function merge() {
  if (files.value.length < 2) { error.value = '合并至少需要选择 2 个 PDF'; return }
  run('/api/pdf/merge', 'files', mergePassword.value ? { password: mergePassword.value } : {})
}
function compress() { if (checkSingle()) run('/api/pdf/compress', 'file', { level: compression.value }) }
function rotate() { if (checkSingle()) run('/api/pdf/rotate', 'file', { degrees: '90' }) }
function split() { if (!checkSingle()) return; if (!pages.value.trim()) { error.value = '请输入页码，例如：1-3,5,8-10'; return }; run('/api/pdf/extract-pages', 'file', { pages: pages.value }, 'officebox-pages.zip') }
function encrypt() { if (!checkSingle()) return; if (!encryptPassword.value) { error.value = '请输入加密密码'; return }; run('/api/pdf/encrypt', 'file', { password: encryptPassword.value }, 'officebox-encrypted.pdf') }
function decrypt() { if (!checkSingle()) return; if (!decryptPassword.value) { error.value = '请输入 PDF 密码'; return }; run('/api/pdf/decrypt', 'file', { password: decryptPassword.value }, 'officebox-decrypted.pdf') }
function toWord() { if (checkSingle()) run('/api/pdf/to-word', 'file', {}, 'officebox-word.docx') }
</script>
<template>
  <div class="pdf">
    <div class="badge">PDF WORKSPACE · v4</div>
    <div class="head"><div class="title-icon"><FileText /></div><div><h1>PDF 工具箱</h1><p>合并 · 拆分 · 压缩 · 旋转 · 加密 · 解密 · 转 Word</p></div></div>
    <label class="drop"><Upload :size="25" /><b>点击选择 PDF，或拖拽到这里</b><span>支持多选；合并可一次选择多个文件</span><input type="file" accept=".pdf,application/pdf" multiple hidden @change="pick" /></label>
    <div v-if="files.length" class="info"><div class="info-title">已选择 {{ files.length }} 个文件</div><div v-for="(f,index) in files" :key="`${f.name}-${index}`" class="file-row"><span class="file-name">📄 {{ f.name }}</span><button class="remove" type="button" title="移除文件" @click="remove(index)"><X :size="17" /></button></div></div>
    <div class="option-grid">
      <label><span><Scissors :size="14"/>拆分页码</span><input v-model="pages" placeholder="1-3,5,8-10" /></label>
      <label><span><Minimize2 :size="14"/>压缩级别</span><select v-model="compression"><option value="screen">强压缩（最小）</option><option value="ebook">推荐（平衡）</option><option value="printer">高质量（较大）</option></select></label>
      <label><span><KeyRound :size="14"/>合并密码</span><input v-model="mergePassword" type="password" autocomplete="current-password" placeholder="有加密 PDF 时填写" /></label>
      <label><span><ShieldCheck :size="14"/>加密密码</span><input v-model="encryptPassword" type="password" autocomplete="new-password" placeholder="设置新密码" /></label>
      <label><span><ShieldCheck :size="14"/>解密密码</span><input v-model="decryptPassword" type="password" autocomplete="current-password" placeholder="输入原 PDF 密码" /></label>
    </div>
    <div class="tools"><button :disabled="busy" @click="merge">合并 PDF</button><button :disabled="busy || files.length !== 1" @click="split">拆分 PDF</button><button :disabled="busy || files.length !== 1" @click="compress">压缩 PDF</button><button :disabled="busy || files.length !== 1" @click="rotate">旋转 90°</button><button :disabled="busy || files.length !== 1" @click="encrypt">加密 PDF</button><button :disabled="busy || files.length !== 1" @click="decrypt">解密 PDF</button><button class="word" :disabled="busy || files.length !== 1" @click="toWord"><FileOutput :size="15"/>PDF 转 Word</button></div>
    <div class="hint">🔐 合并加密 PDF 时填写合并密码；如果多个加密 PDF 密码不同，请先分别解密后再合并。解密不会猜测或破解密码。PDF 转 Word 使用服务器端 LibreOffice 进行转换。</div>
    <div v-if="error" class="error">{{ error }}</div>
    <a v-if="url" class="download" :href="url" :download="name"><Download :size="16"/>下载结果：{{ name }}</a>
    <div v-if="busy" class="busy"><Loader2 class="spin" :size="17"/>正在处理 PDF…</div>
  </div>
</template>
<style scoped>
.pdf{max-width:900px;margin:auto;padding:30px 24px}.badge{display:inline-flex;padding:5px 9px;border-radius:999px;background:#eeeafe;color:#5d50d0;font-size:10px;font-weight:700;letter-spacing:.08em;margin-bottom:10px}.head{display:flex;gap:14px;align-items:center;margin-bottom:22px}.title-icon{width:42px;height:42px;border-radius:11px;background:#eeeafe;color:#5d50d0;display:grid;place-items:center}.head h1{margin:0 0 5px;font-size:22px}.head p{margin:0;color:#8991a2;font-size:12px}.drop{min-height:170px;border:2px dashed #c9c3f2;border-radius:15px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:9px;cursor:pointer;color:#6659df;background:#faf9ff}.drop b{color:#343c4c;font-size:13px}.drop span{color:#8991a2;font-size:12px}.info{margin:14px 0;border:1px solid #e7e9f0;border-radius:10px;padding:10px 12px;font-size:12px}.info-title{color:#60697b;margin-bottom:7px;font-weight:600}.file-row{display:flex;align-items:center;gap:8px;padding:7px 0;border-top:1px solid #f0f1f5}.file-name{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#4e5667}.remove{margin-left:auto;flex:0 0 30px;width:30px;height:30px;padding:0;border:1px solid #e5e7ed;border-radius:8px;background:#fff;color:#7e8797;display:grid;place-items:center;cursor:pointer}.remove:hover{background:#fff0f0;color:#c84f5a}.option-grid{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;margin-top:14px}.option-grid label{display:flex;flex-direction:column;gap:6px}.option-grid span{font-size:11px;color:#737c8e;display:flex;align-items:center;gap:5px}.option-grid input,.option-grid select{height:38px;border:1px solid #dfe2ea;border-radius:9px;padding:0 10px;outline:0;background:white;color:inherit}.tools{display:flex;flex-wrap:wrap;gap:9px;margin-top:18px}.tools button{border:0;border-radius:9px;padding:10px 14px;background:#6659df;color:white;cursor:pointer;display:inline-flex;align-items:center;gap:6px}.tools button.word{background:#3f7fbd}.tools button:disabled{opacity:.45;cursor:not-allowed}.hint{margin-top:12px;color:#8991a2;font-size:11px;line-height:1.7}.error{margin-top:14px;padding:10px;border-radius:8px;background:#fff0f0;color:#c84f5a;font-size:12px}.download{display:inline-flex;gap:7px;align-items:center;margin-top:18px;background:#eaf7ef;color:#348354;padding:10px 14px;border-radius:9px;text-decoration:none;font-size:12px}.busy{display:flex;gap:8px;align-items:center;margin-top:14px;color:#6659df;font-size:12px}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:900px){.option-grid{grid-template-columns:repeat(3,1fr)}}@media(max-width:760px){.option-grid{grid-template-columns:1fr 1fr}}@media(max-width:480px){.option-grid{grid-template-columns:1fr}}
</style>
