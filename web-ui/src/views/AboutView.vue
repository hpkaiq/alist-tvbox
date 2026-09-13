<template>
  <div class="about">
    <el-card class="diagnostics-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>诊断报告</span>
          <el-button type="primary" :loading="reportLoading" @click="generateReport">
            生成报告
          </el-button>
        </div>
      </template>
      <p class="diagnostics-hint">
        一键聚合版本、数据库、存储、追剧、搜索源与近期错误日志摘要(已脱敏:凭证参数/令牌打码)。
        遇到问题时可复制报告附在 issue 或群内,便于快速定位。
      </p>
    </el-card>
    <p>AList proxy server for TvBox, support playlist and search.</p>
    <p>
      源代码：
      <a href="https://github.com/power721/alist-tvbox" target="_blank">https://github.com/power721/alist-tvbox</a>
    </p>
    <p>
      电脑客户端：
      <a href="https://github.com/power721/atv-player" target="_blank">https://github.com/power721/atv-player</a>
    </p>
    <p>
      中文文档：<a href="https://har01d.cn/#/notes/alist-tvbox" target="_blank">https://har01d.cn/#/notes/alist-tvbox</a>
    </p>
    <p>
      <a href="https://github.com/power721/alist-tvbox/blob/master/doc/README_zh.md" target="_blank">https://github.com/power721/alist-tvbox/blob/master/doc/README_zh.md</a>
    </p>
    <p>
      OpenList：
      <a href="https://doc.oplist.org/guide/drivers/common" target="_blank">https://doc.oplist.org/</a>
    </p>
    <p>
      Docker：
      <a href="https://hub.docker.com/r/haroldli/xiaoya-tvbox" target="_blank">https://hub.docker.com/r/haroldli/xiaoya-tvbox</a>
    </p>
    <p>
      Telegram：
      <a :href="telegramInviteLink" target="_blank">https://t.me/alist_tvbox_group</a>
    </p>
    <p>
      手动部署Docker版：
      <code>docker run -d -p 4567:4567 -p 5344:80 -e ALIST_PORT=5344 -v /opt/alist-tvbox:/data --restart=always --name=xiaoya-tvbox haroldli/xiaoya-tvbox</code>
    </p>
    <p>
      手动部署Docker版：
      <code>docker run -d -p 4567:4567 -p 5344:5244 -e ALIST_PORT=5344 -v /opt/alist-tvbox:/data --restart=always --name=alist-tvbox haroldli/alist-tvbox</code>
    </p>
    <p>
      一键部署(系统服务版)：
      <code>curl -fsSL http://d.har01d.cn/install-service.sh -o install-atv.sh && sudo bash ./install-atv.sh</code><br>
    </p>
    <p>
      一键部署(系统服务版)：
      <code>wget -q http://d.har01d.cn/install-service.sh -O install-atv.sh && sudo bash ./install-atv.sh</code><br>
    </p>
    <p>
      一键部署（Docker版）：
      <code>curl -fsSL http://d.har01d.cn/alist-tvbox.sh | sudo bash</code><br>
    </p>
    <p>
      一键升级（Docker版）：
      <code>curl -fsSL http://d.har01d.cn/alist-tvbox.sh | sudo bash -s -- update -y</code><br>
    </p>
<!--    <p>-->
<!--      一键部署(电报监控版)：-->
<!--      <code>sudo bash -c "$(curl -fsSL http://d.har01d.cn/update_new.sh)" -s -t tg</code><br>-->
<!--    </p>-->
<!--    <p>-->
<!--      一键部署(小雅版)：-->
<!--      <code>sudo bash -c "$(curl -fsSL http://d.har01d.cn/update_xiaoya.sh)"</code><br>-->
<!--    </p>-->
<!--    <p>-->
<!--      一键部署(内存优化版)：-->
<!--      <code>sudo bash -c "$(curl -fsSL http://d.har01d.cn/update_new.sh)" -s -t native</code><br>-->
<!--      <code>sudo bash -c "$(curl -fsSL http://d.har01d.cn/update_native.sh)"</code><br>-->
<!--      <code>sudo bash -c "$(curl -fsSL http://d.har01d.cn/update_native_host.sh)"</code><br>-->
<!--    </p>-->
<!--    <p>-->
<!--      一键部署(host网络模式)：-->
<!--      <code>sudo bash -c "$(curl -fsSL http://d.har01d.cn/update_hostmode.sh)"</code><br>-->
<!--    </p>-->
<!--    <p>-->
<!--      一键部署(开发版)：-->
<!--      <code>sudo bash -c "$(curl -fsSL http://d.har01d.cn/update_xiaoya.sh)" -s -t dev</code><br>-->
<!--    </p>-->
<!--    <p>-->
<!--      一键部署(NAS)：-->
<!--      <code>sudo bash -c "$(curl -fsSL http://d.har01d.cn/update_xiaoya.sh)" -s /volume2/docker/xiaoya</code>-->
<!--    </p>-->

    <el-dialog v-model="reportVisible" title="诊断报告" width="720px" top="5vh">
      <div v-if="report" class="report-summary">
        <el-tag v-if="report.errorCount > 0" type="danger">错误 {{ report.errorCount }}</el-tag>
        <el-tag v-else-if="report.warnCount > 0" type="warning">告警 {{ report.warnCount }}</el-tag>
        <el-tag v-else type="success">无告警</el-tag>
        <span class="report-generated-at">{{ report.generatedAt }}</span>
      </div>
      <div v-if="report?.findings?.length" class="report-findings">
        <div v-for="(finding, index) in report.findings" :key="index" class="report-finding">
          <el-tag :type="finding.severity === 'ERROR' ? 'danger' : 'warning'" size="small">
            {{ finding.severity }}
          </el-tag>
          <span>{{ finding.message }}</span>
        </div>
      </div>
      <pre class="report-text">{{ report?.text }}</pre>
      <template #footer>
        <el-button @click="reportVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyReport">复制报告</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import {ref} from 'vue'
import axios from 'axios'
import {ElMessage} from 'element-plus'

interface DiagnosticsFinding {
  severity: string
  message: string
}

interface DiagnosticsReport {
  generatedAt: string
  errorCount: number
  warnCount: number
  findings: DiagnosticsFinding[]
  text: string
}

const telegramInviteLink = import.meta.env.VITE_TELEGRAM_INVITE_LINK || 'https://t.me/alist_tvbox_group'

const reportVisible = ref(false)
const reportLoading = ref(false)
const report = ref<DiagnosticsReport | null>(null)

const generateReport = () => {
  reportLoading.value = true
  axios.get('/api/diagnostics/report').then(({data}) => {
    report.value = data
    reportVisible.value = true
  }).finally(() => {
    reportLoading.value = false
  })
}

const copyReport = () => {
  if (!report.value?.text) {
    return
  }
  const text = report.value.text
  // 局域网 http 访问不是安全上下文,navigator.clipboard 为 undefined:
  // 直接 .writeText 会同步抛 TypeError 且 .catch 接不到 → 必须先判再用
  if (window.isSecureContext && navigator.clipboard) {
    navigator.clipboard.writeText(text)
        .then(() => ElMessage.success('已复制到剪贴板'))
        .catch(() => fallbackCopy(text))
  } else {
    fallbackCopy(text)
  }
}

const fallbackCopy = (text: string) => {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const ok = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (ok) {
    ElMessage.success('已复制到剪贴板')
  } else {
    ElMessage.error('复制失败,请在报告文本中手动全选复制')
  }
}
</script>

<style scoped>
.diagnostics-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.diagnostics-hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.report-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.report-generated-at {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.report-findings {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.report-finding {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.report-text {
  max-height: 55vh;
  margin: 0;
  padding: 12px;
  overflow: auto;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
