<script setup lang="ts">
import { ref, computed, onBeforeUnmount } from 'vue'
import { Upload, Download, Loader2, Trash2, Sparkles } from 'lucide-vue-next'

type Preset = 'web' | 'email' | 'document' | 'quality'
const files = ref<File[]>([])
const quality = ref(82)
const width = ref(0)
const height = ref(0)
const format = ref('jpg')
const rotate = ref(0)
const preset = ref<Preset>('web')
const busy = ref(false)
const error = ref('')
const resultUrl = ref('')
const resultSize = ref(0)

const totalSize = computed(() => files.value.reduce((n, f) => n + f.size, 0))
const saving = computed(() => totalSize.value && resultSize.value ? Math.max(0, Math.round((1 - resultSize.value / totalSize.value) * 100)) : 0)
const sizeText = (n:number) => n < 1024 * 1024 ? (n / 1024).toFixed(1) + ' KB' : (n / 1024 / 1024).toFixed(2) + ' MB'

function add(list: FileList | null) {
  if (!list) return
  const incoming = Array.from(list).filter(f => f.type.startsWith('image/'))
  files.value = [...files.value, ...incoming].slice(0, 50)
  error.value = ''
  revokeResult()
}
function remove(i:number) { files.value.splice(i, 1); revokeResult() }
function revokeResult() { if (resultUrl.value) URL.revokeObjectURL(resultUrl.value); resultUrl.value = ''; resultSize.value = 0 }
function applyPreset(p:Preset) {
  preset.value = p
  if (p === 'web') { quality.value = 78; width.value = 1600; format.value = 'jpg' }
  if (p === 'email') { quality.value = 65; width.value = 1200; format.value = 'jpg' }
  if (p === 'document') { quality.value = 80; width.value = 1800; format.value = 'jpg' }
  if (p === 'quality') { quality.value = 92; width.value = 0; format.value = 'jpg' }
}
async function run() {
  if (!files.value.length) { error.value = '请先选择图片'; return }
  busy.value = true; error.value = ''; revokeResult()
  try {
    const form = new FormData()
    files.value.forEach(f => form.append('files', f))
    const qs = new URLSearchParams({ format: format.value, quality: String(quality.value), width: String(width.value), height: String(height.value), rotate: String(rotate.value) })
    const r = await fetch('http://localhost:8080/api/image/batch/process?' + qs, { method: 'POST', body: form })
    if (!r.ok) throw new Error(await r.text() || '批量处理失败')
    const blob = await r.blob()
    resultSize.value = blob.size
    resultUrl.value = URL.createObjectURL(blob)
  } catch (e) { error.value = e instanceof Error ? e.message : '批量处理失败' }
  finally { busy.value = false }
}
onBeforeUnmount(revokeResult)
</script>

<template>
  <div class="batch">
    <div class="head"><div class="icon">🖼️</div><div><h1>批量图片处理</h1><p>最多 50 张图片，一次设置，统一压缩、缩放、旋转并 ZIP 下载。</p></div></div>
    <label class="drop"><Upload :size="25"/><b>点击或拖拽图片到这里</b><span>支持 JPG、PNG · 最多 50 张</span><input type="file" accept="image/jpeg,image/png" multiple hidden @change="add(($event.target as HTMLInputElement).files)"/></label>
    <div v-if="files.length" class="summary"><strong>{{ files.length }} 张图片</strong><span>原始大小 {{ sizeText(totalSize) }}</span><button @click="files=[];revokeResult()"><Trash2 :size="14"/>清空</button></div>
    <div v-if="files.length" class="list"><div v-for="(f,i) in files" :key="f.name + i" class="file"><img :src="URL.createObjectURL(f)"/><div><b>{{ f.name }}</b><span>{{ sizeText(f.size) }}</span></div><button @click="remove(i)">×</button></div></div>

    <div class="presets"><span><Sparkles :size="14"/>压缩预设</span><button :class="{active:preset==='web'}" @click="applyPreset('web')">网页</button><button :class="{active:preset==='email'}" @click="applyPreset('email')">邮件</button><button :class="{active:preset==='document'}" @click="applyPreset('document')">文档</button><button :class="{active:preset==='quality'}" @click="applyPreset('quality')">高质量</button></div>
    <div class="options">
      <label>输出格式<select v-model="format"><option value="jpg">JPG</option><option value="png">PNG</option></select></label>
      <label>质量 <input v-model.number="quality" type="number" min="10" max="100"/>%</label>
      <label>宽度 <input v-model.number="width" type="number" min="0" placeholder="原尺寸"/></label>
      <label>高度 <input v-model.number="height" type="number" min="0" placeholder="原尺寸"/></label>
      <label>旋转 <select v-model.number="rotate"><option :value="0">0°</option><option :value="90">90°</option><option :value="180">180°</option><option :value="270">270°</option></select></label>
    </div>
    <div v-if="resultSize" class="result"><div><b>处理完成</b><span>{{ sizeText(totalSize) }} → {{ sizeText(resultSize) }}</span></div><strong>约节省 {{ saving }}%</strong></div>
    <div v-if="error" class="error">{{ error }}</div>
    <div class="actions"><button class="primary" :disabled="busy" @click="run"><Loader2 v-if="busy" class="spin"/><span>{{ busy ? '处理中…' : '批量处理并打包 ZIP' }}</span></button><a v-if="resultUrl" class="download" :href="resultUrl" download="officebox-images.zip"><Download :size="16"/>下载 ZIP</a></div>
  </div>
</template>

<style scoped>
.batch{max-width:900px;margin:auto;padding:30px 24px}.head{display:flex;gap:14px;align-items:center;margin-bottom:22px}.icon{width:46px;height:46px;border-radius:12px;background:#e8f2ff;display:grid;place-items:center}.head h1{font-size:22px;margin:0 0 4px}.head p{font-size:12px;color:#8991a2;margin:0}.drop{min-height:170px;border:1.5px dashed #ccd1df;border-radius:15px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;color:#4382d4;background:#fbfdff;cursor:pointer}.drop b{font-size:13px;color:#343c4c}.drop span{font-size:11px;color:#969dad}.summary{display:flex;align-items:center;gap:12px;margin:16px 0 10px;font-size:12px}.summary span{color:#8991a2;flex:1}.summary button,.file button{border:0;background:transparent;cursor:pointer;color:#858da0;display:flex;gap:5px;align-items:center}.list{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;max-height:260px;overflow:auto}.file{display:flex;align-items:center;gap:9px;padding:8px;border:1px solid #e7e9ef;border-radius:9px}.file img{width:42px;height:42px;object-fit:cover;border-radius:6px}.file div{display:flex;flex-direction:column;min-width:0;flex:1}.file b{font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file span{font-size:10px;color:#9aa1af;margin-top:3px}.presets{display:flex;align-items:center;gap:7px;margin-top:16px}.presets>span{display:flex;align-items:center;gap:5px;font-size:11px;color:#687083;margin-right:4px}.presets button{border:1px solid #dfe2ea;background:white;border-radius:7px;padding:7px 11px;font-size:11px;cursor:pointer}.presets button.active{background:#6659df;color:white;border-color:#6659df}.options{display:flex;flex-wrap:wrap;gap:10px;margin-top:12px;padding:15px;border:1px solid #e7e9ef;border-radius:10px}.options label{font-size:11px;color:#687083;display:flex;align-items:center;gap:6px}.options input,.options select{width:95px;border:1px solid #dfe2ea;border-radius:7px;padding:7px;outline:none}.result{margin-top:14px;padding:12px 14px;border:1px solid #dbe9df;background:#f7fcf8;border-radius:9px;display:flex;justify-content:space-between;align-items:center;font-size:12px}.result div{display:flex;gap:8px;align-items:center}.result span{color:#778080}.result strong{color:#348354}.error{margin-top:14px;background:#fff0f0;color:#c84f5a;padding:10px;border-radius:8px;font-size:12px}.actions{display:flex;gap:9px;margin-top:20px}.primary,.download{border:0;border-radius:9px;padding:10px 15px;display:flex;align-items:center;gap:7px;font-size:12px;font-weight:650;text-decoration:none;cursor:pointer}.primary{background:#6659df;color:white}.download{background:#eaf7ef;color:#348354}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}@media(max-width:600px){.list{grid-template-columns:1fr}.presets{flex-wrap:wrap}.options{display:grid;grid-template-columns:1fr 1fr}.result{align-items:flex-start;gap:8px;flex-direction:column}}
</style>