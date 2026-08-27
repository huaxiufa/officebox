<script setup lang="ts">
import { ref } from 'vue'
import { FileText, Upload, Download, Loader2, Trash2, RotateCw, Scissors } from 'lucide-vue-next'

const mode = ref<'split'|'delete'|'rotate'>('split')
const file = ref<File|null>(null)
const page = ref(1)
const pages = ref('2')
const degrees = ref(90)
const busy = ref(false)
const error = ref('')
const resultUrl = ref('')

function choose(f: File|null) { file.value = f; error.value = ''; if (resultUrl.value) URL.revokeObjectURL(resultUrl.value); resultUrl.value='' }
async function run() {
  if (!file.value) { error.value='请先选择 PDF 文件'; return }
  busy.value=true; error.value=''
  try {
    const form=new FormData(); form.append('file',file.value)
    let endpoint='';
    if(mode.value==='split'){ endpoint=`/api/pdf/split?page=${page.value}` }
    if(mode.value==='delete'){ endpoint=`/api/pdf/delete-pages?pages=${encodeURIComponent(pages.value)}` }
    if(mode.value==='rotate'){ endpoint=`/api/pdf/rotate?degrees=${degrees.value}` }
    const r=await fetch(`http://localhost:8080${endpoint}`,{method:'POST',body:form})
    if(!r.ok) throw new Error(await r.text() || '处理失败')
    resultUrl.value=URL.createObjectURL(await r.blob())
  } catch(e) { error.value=e instanceof Error?e.message:'处理失败' } finally { busy.value=false }
}
</script>
<template>
<div class="ops">
  <div class="tabs"><button :class="{active:mode==='split'}" @click="mode='split'"><Scissors :size="17"/>拆分</button><button :class="{active:mode==='delete'}" @click="mode='delete'"><Trash2 :size="17"/>删除页面</button><button :class="{active:mode==='rotate'}" @click="mode='rotate'"><RotateCw :size="17"/>旋转</button></div>
  <div class="title"><div class="icon"><FileText/></div><div><h2>{{ mode==='split'?'拆分 PDF':mode==='delete'?'删除 PDF 页面':'旋转 PDF' }}</h2><p>{{ mode==='split'?'提取指定页面生成一个新的 PDF。':mode==='delete'?'输入页码，例如 2,4-6，批量删除页面。':'将 PDF 所有页面按指定角度旋转。' }}</p></div></div>
  <label class="drop"><Upload :size="22"/><b>{{ file ? file.name : '点击或拖拽 PDF 到这里' }}</b><span>{{ file ? '已选择 · 可重新选择' : '仅支持 PDF 文件' }}</span><input type="file" accept="application/pdf" hidden @change="choose(($event.target as HTMLInputElement).files?.[0]||null)"/></label>
  <div v-if="file" class="options">
    <label v-if="mode==='split'">提取第 <input v-model.number="page" type="number" min="1"/> 页</label>
    <label v-if="mode==='delete'">删除页码 <input v-model="pages" placeholder="2,4-6"/></label>
    <label v-if="mode==='rotate'">旋转角度 <select v-model.number="degrees"><option :value="90">90°</option><option :value="180">180°</option><option :value="270">270°</option></select></label>
  </div>
  <div v-if="error" class="error">{{ error }}</div>
  <div class="actions"><button class="primary" :disabled="busy" @click="run"><Loader2 v-if="busy" class="spin"/><span v-else>开始处理</span><span v-if="busy">处理中…</span></button><a v-if="resultUrl" class="download" :href="resultUrl" download="officebox-result.pdf"><Download :size="17"/>下载结果</a></div>
</div>
</template>
<style scoped>
.ops{max-width:900px;margin:auto;padding:30px 24px}.tabs{display:flex;gap:7px;background:#f1f2f7;padding:5px;border-radius:11px;width:max-content}.tabs button{border:0;background:transparent;padding:9px 14px;border-radius:8px;display:flex;align-items:center;gap:7px;color:#7c8495;cursor:pointer;font-size:12px}.tabs button.active{background:#fff;color:#5f52d7;box-shadow:0 2px 7px #28345d12}.title{display:flex;align-items:center;gap:14px;margin:30px 0 22px}.icon{width:46px;height:46px;background:#eeebff;color:#6659df;border-radius:12px;display:grid;place-items:center}.title h2{margin:0 0 5px;font-size:22px}.title p{margin:0;color:#8a92a2;font-size:12px}.drop{border:1.5px dashed #ccd1df;border-radius:15px;min-height:170px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;cursor:pointer;color:#6659df;background:#fbfbff}.drop b{font-size:13px;color:#343c4c;max-width:90%;overflow:hidden;text-overflow:ellipsis}.drop span{font-size:11px;color:#969dad}.options{margin-top:18px;padding:16px;border:1px solid #e7e9ef;border-radius:11px;background:#fff}.options label{font-size:12px;color:#687083;display:flex;align-items:center;gap:9px}.options input,.options select{border:1px solid #dfe2ea;border-radius:7px;padding:7px 9px;outline:none}.options input{max-width:330px}.error{margin-top:15px;padding:10px 12px;border-radius:8px;background:#fff0f0;color:#c84f5a;font-size:12px}.actions{display:flex;gap:9px;margin-top:20px}.primary,.download{border:0;border-radius:9px;padding:10px 15px;display:flex;align-items:center;gap:8px;font-size:12px;font-weight:650;cursor:pointer;text-decoration:none}.primary{background:#6659df;color:#fff}.primary:disabled{opacity:.65}.download{background:#eaf7ef;color:#348354}.spin{width:16px;animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
</style>
