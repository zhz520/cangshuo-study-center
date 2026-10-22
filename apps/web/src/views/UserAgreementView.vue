<template>
  <div class="agreement-container">
    <div class="agreement-header">
      <el-button link @click="$router.push('/register')" class="back-btn">
        ← 返回
      </el-button>
      <h2>烁学 - 用户服务协议</h2>
    </div>

    <div class="agreement-content">
      <p class="update-time">最近更新日期：2026年03月21日</p>

      <h3>欢迎使用「烁学」！</h3>
      <p>
        本《用户服务协议》（以下简称“本协议”）是您（以下或称“用户”）与「烁学」APP及网页端（以下简称“本平台”）运营方之间就您注册、登录及使用本平台服务所订立的契约。<strong>在您注册成为本平台用户之前，请您务必审慎阅读、充分理解各条款内容。</strong>当您点击同意并完成注册，即表示您已阅读并完全接受本协议的全部条款。
      </p>

      <h4>1. 服务内容</h4>
      <p>
        1.1
        本平台「烁学」致力于为用户提供高效、智能的学习与刷题服务。主要功能包括但不限于：在线题库练习、AI
        智能题解辅助（基于 DeepSeek 等大语言模型）、学习数据记录与多端同步、QQ
        社交账号绑定登录、学情可视化分析、以及
        <strong>Plus 尊享订阅会员体系</strong>（包含全量题库解锁、高频 AI
        讲解等）。<br />
        1.2
        本平台有权根据实际运营需求，对服务内容、功能模块进行增加、删减、修改或调整。部分涉及第三方接口的服务（如
        AI 讲解、QQ
        登录）可能会受第三方平台政策、接口限制或不可抗力导致的服务中断影响。
      </p>

      <h4>2. Plus 订阅服务说明</h4>
      <p>
        2.1
        <strong>权益获得：</strong>用户可通过购买并激活“激活卡密”的方式升级为
        Plus 会员，享受全量题库解锁、每日更高频次的 AI 讲解等增值权益。<br />
        2.2 <strong>有效期管理：</strong>Plus
        会员有效期自卡密激活成功之日起计算。您可以多次激活以延长有效期，系统会自动累计时长。<br />
        2.3 <strong>退款与转让：</strong>由于 Plus
        订阅服务属于数字化虚拟商品，<strong>一旦激活成功，不提供退款服务</strong>。账号及会员权益仅限本人使用，禁止租借或转售给他人。<br />
        2.4
        <strong>服务终止：</strong
        >会员到期后，若未及时续费，您的账户将恢复为普通用户权限，相关锁定题目将无法访问。
      </p>

      <h4>3. 账号注册与使用</h4>
      <p>
        3.1
        <strong>账号注册：</strong
        >您确认，在您完成注册程序或实际使用本服务时，您应当具备中华人民共和国法律规定的与您行为相适应特民事行为能力。您需要提供真实、准确的用户名及密码。严禁使用违法、侵权或违背公序良俗的词语作为用户名。<br />
        3.2
        <strong>账号安全：</strong
        >您的账号仅限您本人使用，禁止转让、借用、赠与或出租。您应妥善保管您的账号及密码，对于因您自身原因导致账号密码泄露、遗失而造成的损失，由您自行承担。<br />
        3.3
        <strong>多端同步与 QQ 绑定：</strong
        >本平台支持移动端与网页端的数据同步。移动端版本支持绑定 QQ
        账号，绑定后将自动同步并展示您的 QQ
        昵称与头像，您可以在设置中随时解除绑定。<br />
        3.4 <strong>安全验证与风险防范：</strong>为保障全平台所有用户的集体权益及服务器的稳健运行，本平台在核心交互接口（如注册、登录、发送验证码等敏感操作）启用了强感知的行为安全验证（如极验 Geetest）。系统或将收集并判断您的操作行为是否为自动化脚本攻击。本平台有权随时要求您完成额外的人机交互滑条验证，并有权阻断被判定为高风险或疑似恶意的连续网络请求。
      </p>

      <h4>4. AI 服务使用规范</h4>
      <p>
        4.1 <strong>AI 智能讲解：</strong>本平台内置了基于先进大语言模型的“AI
        讲解”与“AI 学情分析”功能。您理解并同意，AI
        提供的解析、分析、总结等内容是由机器自动生成，<strong>仅供学习参考</strong>。本平台不对
        AI 产出内容的绝对准确性承担法律担保责任。<br />
        4.2 <strong>配额限制：</strong>为了保障服务器稳定，本平台对单日 AI
        服务使用次数进行合理的配额限制。<strong
          >Plus 用户享有显著高于普通用户的每日使用配额</strong
        >。您不得利用技术手段恶意刷量、绕过限制。<br />
        4.3 <strong>内容合规：</strong>您与 AI
        进行的任何交互，需遵守国家法律法规，严禁传播违法不良信息。
      </p>

      <h4>5. 用户行为规范</h4>
      <p>
        在使用本平台服务时，您承诺遵守以下规则：<br />
        5.1 不得利用本平台制作、复制、发布、传播法律法规禁止的信息；<br />
        5.2
        不得利用技术手段干扰本平台的正常运行，如实施黑客攻击、恶意爬取题库数据等行为；<br />
        5.3 未经明确授权，不得将平台内的题库、AI
        解析记录等用于商业变现或二次分发。<br />
        5.4 若您违反上述规定，本平台有权采取警告、暂停使用、永久封号等措施。
      </p>

      <h4>6. 免责声明</h4>
      <p>
        6.1
        在法律允许的范围内，本平台不对因不可抗力（如网络故障、黑客攻击、政策变化等）导致的服务中断、数据丢失承担赔偿责任。<br />
        6.2
        本平台部分资源（如“系统外链”）可能会跳转至第三方平台，其内容引发的安全风险由第三方承担，本平台不作保证。
      </p>

      <h4>7. 协议的修改与终止</h4>
      <p>
        本平台保留随时修改本协议的权利，修改后的协议将在平台内公示。如您继续使用，则视为您接受修改后的协议条款。
      </p>

      <p class="footer-contact">
        如对本协议有任何疑问，可联系本平台管理员进行咨询。
      </p>
    </div>
  </div>
</template>

<script setup></script>

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
