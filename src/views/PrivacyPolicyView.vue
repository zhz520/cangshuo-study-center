<template>
  <div class="agreement-container">
    <div class="agreement-header">
      <el-button link @click="$router.push('/register')" class="back-btn">
        ← 返回
      </el-button>
      <h2>烁学 - 隐私政策</h2>
    </div>

    <div class="agreement-content">
      <p class="update-time">最近更新日期：2026年02月24日</p>
      
      <h3>欢迎使用「烁学」！</h3>
      <p>「烁学」（以下简称“我们”或“本平台”）深知个人信息对您的重要性。我们致力于保护您的隐私，并确保您的数据在平台内得到安全、合规的处理。本《隐私政策》将详细向您说明我们在您使用我们的移动终端（App）及网页端服务时，如何收集、使用、存储和保护您的个人信息。</p>

      <h4>1. 我们如何收集您的信息</h4>
      <p>在您使用本平台期间，为了向您提供核心的学习与刷题服务，我们只会收集必要的信息：<br>
      1.1 <strong>账号信息：</strong>为了创建和维护您的专属进度档案，在您注册和登录时，您需要提供**用户名**与**密码**。我们采用加密技术存储密码，不获取任何与功能无关的实名身份信息或通讯设备硬件标识。<br>
      1.2 <strong>社交账号信息：</strong>当您在 App 中选择绑定 QQ 账号时，我们会经您授权收集您的 **QQ OpenID、昵称与头像 URL**。这仅用于为您展示个性化个人资料并支持社交账号一键登录。<br>
      1.3 <strong>学习与进度数据：</strong>当您在各个科目库（如计算机专业基础、真题、文章等）进行刷题或阅读操作时，为了实现“多端同步”和“继续学习”功能，我们会自动收集并并上传您的**选中答案、答题对错状态、考试进度、收藏内容**等数据。<br>
      1.4 <strong>AI 交互记录：</strong>当您点击“AI 讲解”或请求“生成学情分析”时，我们会将当前题目的文本或您的历史学习概况发送给合作的第三方 AI 模型（如 DeepSeek，且均经过数据脱敏，不涉及个人身份绑定），以便为您实时生成解析与分析报告。<br>
      1.5 <strong>设备与稳定性数据：</strong>为保障移动 App 稳定运行（如热修复功能），在您使用 App 时，我们会收集必要的**设备型号、系统版本、网络状态**等基础描述信息，用于崩溃排查和热修复补丁推送。</p>

      <h4>2. 我们如何使用您的信息</h4>
      <p>我们承诺收集的信息仅用于以下用途：<br>
      2.1 <strong>提供并优化核心服务：</strong>精准保存并恢复您的刷题进度，为您呈现直观的“学情分析”与“正确率图表”；<br>
      2.2 <strong>云端数据同步：</strong>支撑您的“数据中心”功能，实现移动端与网页端的数据无缝同步，允许您随时随地导入和导出备份数据；<br>
      2.3 <strong>安全保障：</strong>Token 等机制用于验证您的登录状态，防止盗号、未授权访问和恶意 API 请求；<<br>
      2.4 <strong>服务改进：</strong>利用设备信息排查系统崩溃（如加载缓慢、白屏问题）及推送热修复补丁，优化前端展示逻辑。</p>

      <h4>3. 信息的共享、转让与公开披露</h4>
      <p>我们承诺**绝不出售、出租**您的个人信息给任何第三方用于商业营销。仅在以下必要情形下，您的信息可能涉及共享：<br>
      3.1 <strong>第三方 SDK 服务：</strong>App 集成了腾讯 QQ SDK 用于提供社交登录，集成 Tinker SDK 用于紧急 Bug 修复。详情请参阅 App 设置中的《第三方信息共享清单》。<br>
      3.2 <strong>提供 AI 服务：</strong>因为技术需要，我们会将部分请求文本脱敏后发送至第三方 AI 接口（如 DeepSeek）以获取返回内容；此类数据共享受严格的 API 保密协议约束。<br>
      3.3 <strong>法定要求：</strong>根据适用法律法规规定、主管行政机关或司法机关的强制性要求，我们需依法定程序提供必要信息。</p>

      <h4>4. 我们如何保护和存储您的信息</h4>
      <p>4.1 <strong>存储位置：</strong>本平台产生的数据集中存储在位于中国大陆境内的私有云服务器，受到严格的安全防护措施保护。<br>
      4.2 <strong>传输安全：</strong>我们全平台强制使用 HTTPS 加密协议进行所有数据（尤其是 Token 与密码）的传输，防止被中间人窃听。<br>
      4.3 <strong>密码保护：</strong>您的账户密码经过前端预处理及后端高强度哈希算法单向加密存储，任何人（包括管理员）均无法读取您的明文密码。<br>
      4.4 <strong>注销机制与数据销毁：</strong>您可以在“数据中心-危险区域”中点击“清空所有数据”，此操作将不可逆地彻底涂抹您在服务端的全部刷题与账号记录。</p>

      <h4>5. 您的权利</h4>
      <p>在您的账号存续期间，您享有访问、更新、注销账号及清空数据的权利。您可以随时在应用内修改密码，或在 App 的账号设置中随时解除 QQ 账号绑定。</p>

      <h4>6. 未成年人保护</h4>
      <p>我们高度重视未成年人的个人信息保护。如果您是未成年人，建议您在监护人的指导和陪同下阅读本政策，并在取得监护人同意后使用本平台的服务。</p>

      <p class="footer-contact">如对本隐私政策或个人信息保护事宜有任何疑问，请联系平台管理员。本页条款最终解释权归「烁学」项目团队所有。</p>
    </div>
  </div>
</template>

<script setup>
</script>

<style scoped>
.agreement-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 30px 20px;
  background-color: var(--el-bg-color);
  min-height: 100vh;
}

.agreement-header {
  display: flex;
  align-items: center;
  margin-bottom: 30px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 15px;
}

.back-btn {
  margin-right: 20px;
  font-size: 16px;
}

h2 {
  margin: 0;
  font-size: 22px;
  color: var(--el-text-color-primary);
}

.agreement-content {
  line-height: 1.8;
  color: var(--el-text-color-regular);
  font-size: 15px;
}

.update-time {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-bottom: 25px;
  text-align: right;
}

h3 {
  color: var(--el-text-color-primary);
  margin-bottom: 15px;
  font-size: 18px;
}

h4 {
  color: var(--el-text-color-primary);
  margin-top: 25px;
  margin-bottom: 10px;
  font-size: 16px;
}

p {
  margin-bottom: 15px;
}

strong {
  color: var(--el-color-primary);
  font-weight: 600;
}

.footer-contact {
  margin-top: 40px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  padding-top: 20px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
</style>
