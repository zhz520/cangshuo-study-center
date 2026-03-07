<template>
  <div class="agreement-container">
    <div class="agreement-header">
      <el-button link @click="$router.push('/register')" class="back-btn">
        ← 返回
      </el-button>
      <h2>烁学 - 用户服务协议</h2>
    </div>

    <div class="agreement-content">
      <p class="update-time">最近更新日期：2026年02月24日</p>
      
      <h3>欢迎使用「烁学」！</h3>
      <p>本《用户服务协议》（以下简称“本协议”）是您（以下或称“用户”）与「烁学」APP及网页端（以下简称“本平台”）运营方之间就您注册、登录及使用本平台服务所订立的契约。<strong>在您注册成为本平台用户之前，请您务必审慎阅读、充分理解各条款内容。</strong>当您点击同意并完成注册，即表示您已阅读并完全接受本协议的全部条款。</p>

      <h4>1. 服务内容</h4>
      <p>1.1 本平台「烁学」致力于为用户提供高效、智能的学习与刷题服务。主要功能包括但不限于：在线题库练习、AI 智能题解辅助（基于 DeepSeek 等大语言模型）、学习数据记录与多端同步、QQ 社交账号绑定登录、学情可视化分析等。<br>
      1.2 本平台有权根据实际运营需求，对服务内容、功能模块进行增加、删减、修改或调整。部分涉及第三方接口的服务（如 AI 讲解、QQ 登录）可能会受第三方平台政策、接口限制或不可抗力导致的服务中断影响。</p>

      <h4>2. 账号注册与使用</h4>
      <p>2.1 <strong>账号注册：</strong>您确认，在您完成注册程序或实际使用本服务时，您应当具备中华人民共和国法律规定的与您行为相适应特民事行为能力。您需要提供真实、准确的用户名及密码。严禁使用违法、侵权或违背公序良俗的词语作为用户名。<br>
      2.2 <strong>账号安全：</strong>您的账号仅限您本人使用，禁止转让、借用、赠与或出租。您应妥善保管您的账号及密码，对于因您自身原因导致账号密码泄露、遗失而造成的损失，由您自行承担。<br>
      2.3 <strong>多端同步与 QQ 绑定：</strong>本平台支持移动端与网页端的数据同步。移动端版本支持绑定 QQ 账号，绑定后将自动同步并展示您的 QQ 昵称与头像，您可以在设置中随时解除绑定。为了保证“数据中心”数据的准确性，您在执行“清空数据”等操作前，须自行核对数据完整性。</p>

      <h4>3. AI 服务使用规范</h4>
      <p>3.1 <strong>AI 智能讲解：</strong>本平台内置了基于先进大语言模型的“AI 讲解”与“AI 学情分析”功能。您理解并同意，AI 提供的解析、分析、总结等内容是由机器自动生成，<strong>仅供学习参考，不能完全替代专业的人工解析或官方标准答案</strong>。本平台不对 AI 产出内容的绝对准确性、完整性和专业权威性承担法律担保责任。<br>
      3.2 <strong>配额限制：</strong>为了保障服务器稳定运行和提供公平的服务，本平台可能会对单日 AI 服务使用次数进行合理的配额限制（具体剩余次数会在客户端内显示）。您不得利用技术手段恶意刷量、绕过限制或攻击 AI 接口。<br>
      3.3 <strong>内容合规：</strong>您与 AI 进行的任何交互（如有自定义提问输入），需遵守国家法律法规，严禁输入和传播涉政、涉黄、涉赌、暴力等违法不良信息。</p>

      <h4>4. 用户行为规范</h4>
      <p>在使用本平台服务时，您承诺遵守以下规则：<br>
      4.1 不得利用本平台制作、复制、发布、传播法律法规禁止的信息；<br>
      4.2 不得利用技术手段干扰本平台的正常运行，如实施黑客攻击、植入木马/病毒、进行恶意爬取题库数据等行为；<br>
      4.3 未经本平台明确授权，不得将平台内的题库、AI 解析记录、文章内容等用于任何形式的商业变现、非法出版或二次分发。<br>
      4.4 若您违反上述规定，本平台有权视情节轻重，对您的账号采取警告、暂停使用、永久封号等措施，并保留进一步追究法律责任的权利。</p>

      <h4>5. 免责声明</h4>
      <p>5.1 在法律允许的范围内，本平台不对因不可抗力（如网络故障、黑客攻击、政策变化、基础云服务商故障等）导致的服务中断、数据丢失承担赔偿责任。<br>
      5.2 本平台部分资源（如“系统外链”、“常用资源”）可能会跳转至第三方平台，第三方平台的内容及其引发的安全和隐私风险，由相应的第三方承担，本平台不作任何承诺或保证。</p>

      <h4>6. 协议的修改与终止</h4>
      <p>部分功能可能随应用更新发生变化。本平台保留随时修改本协议的权利，修改后的协议将在平台内公示或在相关更新中提示。如您不同意修改后的内容，请停止使用本平台服务；若您继续使用，则视为您接受修改后的协议条款。</p>
      
      <p class="footer-contact">如对本协议有任何疑问，可联系本平台管理员进行咨询。</p>
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
