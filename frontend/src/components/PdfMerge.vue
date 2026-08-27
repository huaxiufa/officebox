<script setup lang="ts">
import { ref } from 'vue'
import { FileText, GripVertical, Upload, X, Play, Download, Loader2 } from 'lucide-vue-next'
import { formatBytes, mergePdfs, type PdfFile } from '../pdfMerge'

const files = ref<PdfFile[]>([])
const busy = ref(false)
const error = ref('')
const resultUrl = ref('')

function addFiles(list: FileList | null) {
  if (!list) return
  for (const file of Array.from(list)) {
    if (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) files.value.push({ file, id: crypto.randomUUID() })
  }
  error.value = ''
}
function remove(id: string) { files.value = files.value.filter(x => x.id !== id) }
function move(index: number, delta: number) { const target = index + delta; if (target < 0 || target >= files.value.length) return; const a = files.value[index]; files.value[index] = files.value[target]; files.value[target] = a }
async function run() {
  if (files.value.length < 2) { error.value = '请至少添加两个 PDF 文件'; return }
  busy.value = true; error.value = ''; if (resultUrl.value) URL.revokeObjectURL(resultUrl.value)
  try { resultUrl.value = URL.createObjectURL(await mergePdfs(files.value)) } catch (e) { error.value = e instanceof Error ? e.message : '处理失败' } finally { busy.value = false }
}
</script>

<template>
  <div class="pdf-tool">
    <div class="tool-head"><div><div class="tool-kicker">PDF TOOL</div><h1>合并 PDF</h1><p>将多个 PDF 按指定顺序合并为一个文件。</p></div><label class="add-btn"><Upload :size="17"/> 添加文件<input type="file" accept="application/pdf" multiple hidden @change="addFiles(($event.target as HTMLInputElement).files)"/></label></div>
    <label class="pdf-drop"><Upload :size="24"/><b>拖拽 PDF 到这里</b><span>或点击选择文件，支持批量添加</span><input type="file" accept="application/pdf" multiple hidden @change="addFiles(($event.target as HTMLInputElement).files)"/></label>
    <div v-if="files.length" class="file-list"><div class="list-head"><b>{{ files.length }} 个文件</b><span>拖拽排序功能即将加入</span></div><div v-for="(item,index) in files" :key="item.id" class="file-row"><GripVertical class="grip" :size="18"/><div class="pdf-icon"><FileText :size="19"/></div><div class="file-info"><b>{{ item.file.name }}</b><span>{{ formatBytes(item.file.size) }}</span></div><button class="move" :disabled="index===0" @click="move(index,-1)">↑</button><button class="move" :disabled="index===files.length-1" @click="move(index,1)">↓</button><button class="remove" @click="remove(item.id)"><X :size="17"/></button></div></div>
    <div v-if="error" class="error">{{ error }}</div>
    <div class="actions"><button class="primary" :disabled="busy" @click="run"><Loader2 v-if="busy" class="spin" :size="17"/><Play v-else :size="17"/> {{ busy ? '正在合并…' : '开始合并' }}</button><a v-if="resultUrl" class="download" :href="resultUrl" download="officebox-merged.pdf"><Download :size="17"/> 下载合并后的 PDF</a></div>
  </div>
</template>

<style scoped>
.pdf-tool{max-width:900px;margin:auto;padding:40px 24px}.tool-head{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:26px}.tool-kicker{font-size:10px;letter-spacing:2px;color:#6b5cdf;font-weight:800}.tool-head h1{margin:7px 0 5px;font-size:30px}.tool-head p{margin:0;color:#81899b;font-size:13px}.add-btn,.primary,.download{display:flex;align-items:center;gap:8px;border:0;border-radius:9px;padding:10px 15px;font-size:13px;font-weight:650;cursor:pointer;text-decoration:none}.add-btn{background:#efedff;color:#5d50d4}.pdf-drop{border:1.5px dashed #cdd1df;border-radius:15px;min-height:155px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;color:#6b5cdf;background:#fafaff;cursor:pointer}.pdf-drop b{font-size:14px;color:#30384a}.pdf-drop span{font-size:12px;color:#969dad}.file-list{margin-top:24px;border:1px solid #e5e7ef;border-radius:13px;overflow:hidden;background:#fff}.list-head{padding:13px 16px;border-bottom:1px solid #eef0f5;display:flex;justify-content:space-between;font-size:12px}.list-head span{color:#99a0b0}.file-row{display:flex;align-items:center;gap:10px;padding:12px 14px;border-bottom:1px solid #f0f1f5}.file-row:last-child{border:0}.grip{color:#b4bac7}.pdf-icon{width:36px;height:36px;border-radius:9px;background:#f0edff;color:#6659df;display:grid;place-items:center}.file-info{flex:1;display:flex;flex-direction:column;gap:3px;min-width:0}.file-info b{font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file-info span{font-size:11px;color:#9aa1af}.move,.remove{border:0;background:transparent;color:#7d8596;cursor:pointer}.move:disabled{opacity:.25}.remove:hover{color:#d45c67}.error{margin-top:15px;padding:11px 13px;border-radius:9px;background:#fff0f0;color:#c94e5a;font-size:12px}.actions{display:flex;gap:10px;margin-top:22px}.primary{background:#6659df;color:#fff}.primary:disabled{opacity:.65;cursor:wait}.download{background:#edf8f1;color:#328252}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:600px){.tool-head{flex-direction:column;gap:18px}.actions{flex-direction:column}.download,.primary{justify-content:center}}
</style>
