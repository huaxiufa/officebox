<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import { Upload, Download, FileText, Loader2, Trash2, Archive } from 'lucide-vue-next'

const files = ref<File[]>([])
const busy = ref(false)
const error = ref('')
const resultUrl = ref('')
const resultName = ref('officebox-pdf.zip')
const supported = ['.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx']
const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const totalSize = computed(() => files.value.reduce((n, f) => n + f.size, 0))
const sizeText = (n: number) => n < 1024 * 1024 ? (n / 1024).toFixed(1) + ' KB' : (n / 1024 / 1024).toFixed(2) + ' MB'
const kind = (f: File) => { const ext = '.' + (f.name.split('.').pop() || '').toLowerCase(); if (['.doc','.docx'].includes(ext)) return 'Word'; if (['.xls','.xlsx'].includes(ext)) return 'Excel'; if (['.ppt','.pptx'].includes(ext)) return 'PowerPoint'; return 'Office' }
function add(list: FileList | null) { if (!list) return; const incoming = Array.from(list).filter(f => supported.includes('.' + (f.name.split('.').pop() || '').toLowerCase())); files.value = [...files.value, ...incoming].slice(0, 20); error.value = ''; revoke() }
function clear() { files.value = []; revoke() }
function remove(i: number) { files.value.splice(i, 1); revoke() }
function revoke() { if (resultUrl.value) URL.revokeObjectURL(resultUrl.value); resultUrl.value = '' }
async function convert() {
  if (!files.value.length) { error.value = '请先选择 Word、Excel 或 PowerPoint 文件'; return }
  busy.value = true; error.value = ''; revoke()
  try {
    const body = new FormData()
    const path = files.value.length === 1 ? '/api/office/to-pdf' : '/api/office/batch-to-pdf'
    if (files.value.length === 1) body.append('file', files.value[0]); else files.value.forEach(f => body.append('files', f))
    const r = await fetch(`${apiBase}${path}`, { method: 'POST', body })
    if (!r.ok) throw new Error(r.status === 422 ? '没有成功转换的文件，请检查 Office 文件是否损坏' : 'Office 转 PDF 失败')
    const blob = await r.blob()
    resultUrl.value = URL.createObjectURL(blob)
    resultName.value = files.value.length === 1 ? files.value[0].name.replace(/\.[^.]+$/, '') + '.pdf' : 'officebox-pdf.zip'
  } catch (e) { error.value = e instanceof Error ? e.message : 'Office 转 PDF 失败' }
  finally { busy.value = false }
}
onBeforeUnmount(revoke)
</script>

<template>
  <div class="office">
    <div class="head"><div class="icon"><FileText :size="23"/></div><div><h1>Office 转 PDF</h1><p>Word、Excel、PowerPoint 一键转换，多个文件自动打包 ZIP。</p></div></div>
    <label class="drop"><Upload :size="26"/><b>点击或拖拽 Office 文件到这里</b><span>支持 DOC、DOCX、XLS、XLSX、PPT、PPTX，最多 20 个</span><input type="file" :accept="supported.join(',')" multiple hidden @change="add(($event.target as HTMLInputElement).files)"/></label>
    <div v-if="files.length" class="summary"><strong>{{ files.length }} 个文件</strong><span>总大小 {{ sizeText(totalSize) }}</span><button @click="clear"><Trash2 :size="14"/>清空</button></div>
    <div v-if="files.length" class="list"><div v-for="(f,i) in files" :key="f.name+i" class="file"><div class="badge">{{ kind(f).slice(0,1) }}</div><div class="meta"><b>{{ f.name }}</b><span>{{ kind(f) }} · {{ sizeText(f.size) }}</span></div><button @click="remove(i)">×</button></div></div>
    <div v-if="error" class="error">{{ error }}</div>
    <div class="actions"><button class="primary" :disabled="busy || !files.length" @click="convert"><Loader2 v-if="busy" class="spin"/><Archive v-else :size="16"/><span>{{ busy ? '转换中…' : files.length > 1 ? '批量转换并下载 ZIP' : '转换为 PDF' }}</span></button><a v-if="resultUrl" class="download" :href="resultUrl" :download="resultName"><Download :size="16"/>{{ files.length > 1 ? '下载 ZIP' : '下载 PDF' }}</a></a></div>
  </div>
</template>

<style scoped>
.office{max-width:900px;margin:auto;padding:30px 24px}.head{display:flex;gap:14px;align-items:center;margin-bottom:22px}.icon{width:46px;height:46px;border-radius:12px;background:#eeeaff;color:#6659df;display:grid;place-items:center}.head h1{font-size:22px;margin:0 0 4px}.head p{font-size:12px;color:#8991a2;margin:0}.drop{min-height:190px;border:1.5px dashed #ccd1df;border-radius:15px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:9px;color:#6659df;background:#fbfdff;cursor:pointer}.drop b{font-size:13px;color:#343c4c}.drop span{font-size:11px;color:#969dad}.summary{display:flex;align-items:center;gap:12px;margin:16px 0 10px;font-size:12px}.summary span{color:#8991a2;flex:1}.summary button,.file button{border:0;background:transparent;cursor:pointer;color:#858da0;display:flex;gap:5px;align-items:center}.list{display:flex;flex-direction:column;gap:8px}.file{display:flex;align-items:center;gap:10px;padding:10px;border:1px solid #e7e9ef;border-radius:9px}.badge{width:36px;height:36px;border-radius:8px;background:#f0efff;color:#6659df;display:grid;place-items:center;font-weight:700;font-size:12px}.meta{display:flex;flex-direction:column;min-width:0;flex:1}.meta b{font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.meta span{font-size:10px;color:#9aa1af;margin-top:3px}.error{margin-top:14px;background:#fff0f0;color:#c84f5a;padding:10px;border-radius:8px;font-size:12px}.actions{display:flex;gap:9px;margin-top:20px}.primary,.download{border:0;border-radius:9px;padding:10px 15px;display:flex;align-items:center;gap:7px;font-size:12px;font-weight:650;text-decoration:none;cursor:pointer}.primary{background:#6659df;color:white}.primary:disabled{opacity:.55;cursor:not-allowed}.download{background:#eaf7ef;color:#348354}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
</style>
