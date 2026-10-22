const path = require("path");
require("dotenv").config({ path: path.resolve(__dirname, ".env") });
const axios = require("axios");
const crypto = require("crypto");
const express = require("express");
const api = require("./api");

// 校验关键环境变量
if (!process.env.QQ_APPID || !process.env.QQ_APPSECRET) {
  console.error(
    "[启动错误] QQ_APPID 或 QQ_APPSECRET 未在 .env 中配置，请检查！"
  );
  process.exit(1);
}

const baseConfig = {
  appID: process.env.QQ_APPID,
  secret: process.env.QQ_APPSECRET,
  sandbox: process.env.QQ_SANDBOX === "true",
};

const WEBHOOK_PORT = parseInt(process.env.WEBHOOK_PORT) || 8080;

let currentAccessToken = "";

// 内存中记录用户的答题会话
const sessionMap = new Map();

// 消息序列号递增器
let msgSeqCounter = 1;

// 会话超时时间 (10分钟)
const SESSION_TIMEOUT_MS = 10 * 60 * 1000;

// 会话超时清理 (每分钟检查)
setInterval(() => {
  const now = Date.now();
  for (const [key, session] of sessionMap.entries()) {
    if (now - session.timestamp > SESSION_TIMEOUT_MS) {
      console.log(`[会话过期] ${key} 已超时清理`);
      sessionMap.delete(key);
    }
  }
}, 60 * 1000);

// ==========================================
// access_token 管理
// ==========================================
async function getAccessToken() {
  const res = await axios.post("https://bots.qq.com/app/getAppAccessToken", {
    appId: baseConfig.appID,
    clientSecret: baseConfig.secret,
  });
  return res.data;
}

async function refreshToken() {
  try {
    console.log("[BOT] 正在刷新 access_token...");
    const tokenData = await getAccessToken();
    currentAccessToken = tokenData.access_token;
    console.log(
      `[BOT] Token 刷新成功，有效期: ${tokenData.expires_in}秒`
    );
    setTimeout(refreshToken, (tokenData.expires_in - 60) * 1000);
  } catch (e) {
    console.error("[BOT ERROR] Token刷新失败:", e.message);
    setTimeout(refreshToken, 10000);
  }
}

// ==========================================
// ed25519 签名工具（QQ Webhook 验证）
// ==========================================

// 根据 Bot Secret 生成 ed25519 密钥对
function getEd25519Keys() {
  let seed = baseConfig.secret;
  // seed 需要至少 32 字节，不够就重复
  while (seed.length < 32) {
    seed = seed + seed;
  }
  seed = seed.slice(0, 32);
  const seedBuffer = Buffer.from(seed, "utf8");
  // Node.js crypto 使用 ed25519 seed 创建私钥
  const privateKey = crypto.createPrivateKey({
    key: Buffer.concat([
      // Ed25519 PKCS8 DER prefix (固定30字节)
      Buffer.from("302e020100300506032b657004220420", "hex"),
      seedBuffer,
    ]),
    format: "der",
    type: "pkcs8",
  });
  const publicKey = crypto.createPublicKey(privateKey);
  return { privateKey, publicKey };
}

// 用私钥签名
function ed25519Sign(message) {
  const { privateKey } = getEd25519Keys();
  const signature = crypto.sign(null, Buffer.from(message, "utf8"), privateKey);
  return signature.toString("hex");
}

// 验证签名
function ed25519Verify(message, signatureHex) {
  try {
    const { publicKey } = getEd25519Keys();
    const signature = Buffer.from(signatureHex, "hex");
    return crypto.verify(null, Buffer.from(message, "utf8"), publicKey, signature);
  } catch (e) {
    console.error("[签名验证错误]", e.message);
    return false;
  }
}

// ==========================================
// 发送群消息
// ==========================================
async function sendGroupMessage(groupOpenid, msgId, content) {
  try {
    const apiBase = baseConfig.sandbox
      ? "https://sandbox.api.sgroup.qq.com"
      : "https://api.sgroup.qq.com";

    const res = await axios.post(
      `${apiBase}/v2/groups/${groupOpenid}/messages`,
      { content, msg_type: 0, msg_id: msgId, msg_seq: msgSeqCounter++ },
      {
        headers: {
          Authorization: `QQBot ${currentAccessToken}`,
          "Content-Type": "application/json",
        },
        timeout: 10000,
      }
    );
    return res.data;
  } catch (e) {
    console.error(
      "[发送群消息失败]",
      e.response?.data?.message || e.message
    );
    return null;
  }
}

// ==========================================
// 发送私聊消息
// ==========================================
async function sendC2CMessage(userOpenid, msgId, content) {
  try {
    const apiBase = baseConfig.sandbox
      ? "https://sandbox.api.sgroup.qq.com"
      : "https://api.sgroup.qq.com";

    const res = await axios.post(
      `${apiBase}/v2/users/${userOpenid}/messages`,
      { content, msg_type: 0, msg_id: msgId, msg_seq: msgSeqCounter++ },
      {
        headers: {
          Authorization: `QQBot ${currentAccessToken}`,
          "Content-Type": "application/json",
        },
        timeout: 10000,
      }
    );
    return res.data;
  } catch (e) {
    console.error(
      "[发送私聊消息失败]",
      e.response?.data?.message || e.message
    );
    return null;
  }
}

// ==========================================
// 题目工具函数
// ==========================================

// 解析答案字段 (兼容各种存储格式)
function parseAnswer(answer, type) {
  if (!answer && answer !== "") return "";

  if (type === "fill") {
    if (Array.isArray(answer)) return answer.join("");
    return String(answer).trim();
  }

  if (typeof answer === "string") {
    try {
      const parsed = JSON.parse(answer);
      if (Array.isArray(parsed)) {
        return parsed.join("").toUpperCase();
      }
    } catch (e) {
      // 不是 JSON
    }
    const letters = answer.toUpperCase().match(/[A-Z]/g);
    if (letters) return letters.sort().join("");
    return answer.trim();
  }

  if (Array.isArray(answer)) {
    return answer
      .map((a) => String(a).toUpperCase())
      .sort()
      .join("");
  }

  return String(answer).trim();
}

// 解析选项字段 (兼容多种存储格式)
function parseOptions(options) {
  if (!options) return null;
  let data = options;
  if (typeof data === "string") {
    try {
      data = JSON.parse(data);
    } catch (e) {
      return null;
    }
  }
  if (typeof data === "object" && !Array.isArray(data)) {
    return data;
  }
  if (Array.isArray(data)) {
    const obj = {};
    data.forEach((item, i) => {
      if (typeof item === "object" && item !== null) {
        const key =
          item.label || item.key || String.fromCharCode(65 + i);
        const value =
          item.content || item.value || item.text || JSON.stringify(item);
        obj[key] = value;
      } else {
        obj[String.fromCharCode(65 + i)] = String(item);
      }
    });
    return obj;
  }
  return null;
}

// 清理 Markdown 特殊符号
function cleanMarkdown(text) {
  if (!text) return "";
  return text
    .replace(/```[\s\S]*?```/g, (m) => m.replace(/```\w*/g, "").trim())
    .replace(/`([^`]+)`/g, "$1")
    .replace(/#{1,6}\s*/g, "")
    .replace(/\*\*(.+?)\*\*/g, "$1")
    .replace(/\*(.+?)\*/g, "$1")
    .replace(/__(.+?)__/g, "$1")
    .replace(/~~(.+?)~~/g, "$1")
    .replace(/^[\-\*]\s/gm, "• ")
    .replace(/^>\s?/gm, "")
    .replace(/\[(.+?)\]\(.+?\)/g, "$1")
    .trim();
}

// 获取题型显示名
function getTypeLabel(type) {
  const map = {
    single: "单选题",
    multiple: "多选题",
    judge: "判断题",
    fill: "填空题",
    material: "材料题",
    sort: "排序题",
  };
  return map[type] || type || "题目";
}

// 判断用户的回答是否正确
function checkAnswer(userAnswer, correctAnswer, type) {
  if (type === "fill") {
    const ua = userAnswer.replace(/\s+/g, "").toLowerCase();
    const ca = correctAnswer.replace(/\s+/g, "").toLowerCase();
    return ua === ca;
  }
  const sortedUser = userAnswer
    .toUpperCase()
    .split("")
    .filter((c) => /[A-Z]/.test(c))
    .sort()
    .join("");
  const sortedCorrect = correctAnswer
    .toUpperCase()
    .split("")
    .filter((c) => /[A-Z]/.test(c))
    .sort()
    .join("");
  return sortedUser === sortedCorrect;
}

// ==========================================
// 消息入口包装
// ==========================================
async function handleGroupMessage(data) {
  const groupOpenid = data.group_openid;
  const msgId = data.id;
  const memberOpenid = data.author?.member_openid || data.author?.id;
  const content = (data.content || "").replace(/\s+/g, " ").trim();

  const ctx = {
    userOpenid: memberOpenid,
    msgId,
    content,
    reply: async (text) => await sendGroupMessage(groupOpenid, msgId, text),
  };
  await handleMessage(ctx, "群聊");
}

async function handleC2CMessage(data) {
  const userOpenid = data.author?.user_openid || data.author?.id;
  const msgId = data.id;
  const content = (data.content || "").replace(/\s+/g, " ").trim();

  const ctx = {
    userOpenid,
    msgId,
    content,
    reply: async (text) => await sendC2CMessage(userOpenid, msgId, text),
  };
  await handleMessage(ctx, "私聊");
}

// ==========================================
// 统一消息处理逻辑 (与原版完全一致)
// ==========================================
async function handleMessage(ctx, chatType) {
  const { userOpenid, msgId, content, reply } = ctx;

  console.log(`[${chatType}消息] user=${userOpenid} content="${content}"`);

  // 1. 答题会话中
  if (sessionMap.has(userOpenid)) {
    const session = sessionMap.get(userOpenid);
    session.timestamp = Date.now();

    if (content === "/退出" || content === "退出") {
      sessionMap.delete(userOpenid);
      await reply("已退出刷题模式。");
      return;
    }

    const q = session.question;
    const type = q.type || "single";
    const correctAnswer = parseAnswer(q.answer, type);

    if (session.state === "answering") {
      let userAnswer = content;
      let isValid = false;

      if (type === "fill") {
        isValid = userAnswer.length > 0;
      } else if (type === "judge") {
        const judgeMap = {
          对: "A", 正确: "A", "√": "A", TRUE: "A",
          错: "B", 错误: "B", "×": "B", FALSE: "B",
          A: "A", B: "B",
        };
        const mapped = judgeMap[userAnswer.toUpperCase()];
        if (mapped) {
          userAnswer = mapped;
          isValid = true;
        }
      } else if (type === "multiple") {
        const upper = userAnswer.toUpperCase();
        isValid =
          /^[A-Z]{2,}$/.test(upper) &&
          new Set(upper).size === upper.length;
        if (isValid) userAnswer = upper;
      } else {
        const upper = userAnswer.toUpperCase();
        isValid = /^[A-D]$/.test(upper);
        if (isValid) userAnswer = upper;
      }

      if (isValid) {
        const isCorrect = checkAnswer(userAnswer, correctAnswer, type);
        await api.submitProgress(
          userOpenid, `${chatType}用户`, "", q.id,
          isCorrect ? 1 : 0, userAnswer
        );

        let replyMsg = "";
        if (type === "fill") {
          replyMsg += `你的答案: ${userAnswer}\n`;
        } else {
          replyMsg += `提交答案: ${userAnswer}\n`;
        }

        if (isCorrect) {
          replyMsg += "✅ 回答正确！\n";
        } else {
          if (type === "fill") {
            replyMsg += `❌ 回答错误！正确答案是: ${correctAnswer}\n`;
          } else if (type === "judge") {
            const answerText = correctAnswer === "A" ? "正确 (A)" : "错误 (B)";
            replyMsg += `❌ 回答错误！正确答案是: ${answerText}\n`;
          } else {
            replyMsg += `❌ 回答错误！正确答案是: ${correctAnswer}\n`;
          }
        }

        if (q.analysis) {
          replyMsg += `\n📖 解析：${q.analysis}\n`;
        }
        replyMsg += '\n回复"继续"下一题 | "解析"AI讲解 | "退出"结束';
        await reply(replyMsg);

        session.lastAnswer = userAnswer;
        session.lastCorrectAnswer = correctAnswer;
        const formattedOpts = parseOptions(q.options);
        session.lastOptionsStr = formattedOpts
          ? Object.entries(formattedOpts)
              .map(([k, v]) => `${k}. ${v}`)
              .join("\n")
          : "";
        session.state = "waiting_next";
        return;
      } else {
        let hint = "";
        if (type === "fill") hint = "请直接输入你的答案";
        else if (type === "judge") hint = "请回复 A(正确) 或 B(错误)";
        else if (type === "multiple") hint = "请回复选项组合（如 AB、ACD）";
        else hint = "请回复 A/B/C/D 中的一个";
        await reply(`${hint}，或回复"退出"结束。`);
        return;
      }
    } else if (session.state === "waiting_next") {
      if (content === "继续" || content === "下一题") {
        await requestQuestion(session.subjectId, userOpenid, reply);
        return;
      }
      if (
        content === "解析" ||
        content === "/解析" ||
        content === "AI解析"
      ) {
        await reply("⏳ AI解析生成中，请稍候...");
        try {
          const explain = await api.getAIExplain(
            q.content, session.lastOptionsStr,
            session.lastAnswer, session.lastCorrectAnswer
          );
          const cleanExplain = cleanMarkdown(explain);
          await reply(
            `【AI家教解析】\n${cleanExplain}\n\n回复"继续"下一题，回复"退出"结束。`
          );
        } catch (err) {
          console.error("[AI解析失败]", err.message);
          await reply("AI解析暂时不可用，请稍后再试。");
        }
        return;
      }
    }
  }

  // 2. 指令: /做题
  if (content.startsWith("/做题") || content.startsWith("做题")) {
    const match = content.match(/做题\s*(\d+)/);
    if (match) {
      const subjectId = parseInt(match[1]);
      await requestQuestion(subjectId, userOpenid, reply);
    } else {
      const subjects = await api.getSubjects();
      if (subjects.length > 0) {
        const subStr = subjects.map((s) => `${s.id}: ${s.name}`).join("\n");
        await reply(
          `请回复科目编号开始刷题，例如: /做题 1\n\n可选科目：\n${subStr}`
        );
      } else {
        await reply("抱歉，目前还没有可选科目。");
      }
    }
    return;
  }

  // 3. 指令: /成绩
  if (content === "/成绩" || content === "成绩") {
    const stats = await api.getStats(userOpenid);
    if (stats) {
      await reply(
        `📊 成绩单：\n总刷题量：${stats.total} 道\n回答正确：${stats.correct} 道\n综合正确率：${stats.accuracy}%`
      );
    } else {
      await reply("暂时没有获取到成绩，去做几道题吧！");
    }
    return;
  }

  // 4. 指令: /排行
  if (
    content === "/排行" ||
    content === "排行" ||
    content === "排行榜"
  ) {
    const list = await api.getLeaderboard();
    if (list && list.length > 0) {
      const medals = ["🥇", "🥈", "🥉"];
      let board = "🏆 刷题排行榜 Top 10\n━━━━━━━━━━━━━━━━\n";
      list.forEach((u, i) => {
        const prefix = i < 3 ? medals[i] : `${i + 1}.`;
        const name = u.nickname || "匿名用户";
        board += `${prefix} ${name}  |  ${u.total}题  |  正确率${u.accuracy}%\n`;
      });
      board += "━━━━━━━━━━━━━━━━\n发送 /做题 开始挑战！";
      await reply(board);
    } else {
      await reply("暂时还没有排行数据，快来第一个刷题吧！");
    }
    return;
  }

  // 5. 菜单
  if (
    content === "/菜单" ||
    content === "菜单" ||
    content === "帮助" ||
    content === "/帮助" ||
    content === ""
  ) {
    await reply(getMenuText());
    return;
  }

  // 6. 未识别指令
  await reply(
    `抱歉，没有理解指令"${content.substring(0, 20)}"。\n\n` + getMenuText()
  );
}

function getMenuText() {
  return `📚 刷题小助手 - 命令菜单
━━━━━━━━━━━━━━━━
🔴 /做题 - 选择科目开始刷题
🔴 /做题 [编号] - 指定科目快速开始
📊 /成绩 - 查看我的做题统计
🏆 /排行 - 群内刷题排行榜
⛔ /退出 - 退出当前刷题模式
━━━━━━━━━━━━━━━━
支持单选/多选/判断/填空题型
私聊或@我 发送指令即可使用`;
}

// ==========================================
// 出题逻辑
// ==========================================
async function requestQuestion(subjectId, userOpenid, reply) {
  const q = await api.getRandomQuestion(subjectId, userOpenid);
  if (!q) {
    await reply("该科目暂时没有题目啦。");
    sessionMap.delete(userOpenid);
    return;
  }

  const type = q.type || "single";
  const typeLabel = getTypeLabel(type);
  let text = `📝【${typeLabel}】\n${q.content}\n`;

  if (type !== "fill") {
    const opts = parseOptions(q.options);
    if (opts && Object.keys(opts).length > 0) {
      text += "\n";
      text += Object.entries(opts)
        .map(([k, v]) => `${k}. ${v}`)
        .join("\n");
    }
  }

  text += "\n\n";
  if (type === "fill") {
    text += '📝 请直接输入你的答案，或回复"退出"结束。';
  } else if (type === "judge") {
    text += '请回复 A(正确) 或 B(错误)，或回复"退出"结束。';
  } else if (type === "multiple") {
    text += '请回复选项组合（如 AB、ACD），或回复"退出"结束。';
  } else {
    text += '请回复 A/B/C/D 提交答案，或回复"退出"结束。';
  }

  await reply(text);

  sessionMap.set(userOpenid, {
    subjectId,
    question: q,
    state: "answering",
    timestamp: Date.now(),
  });
}

// ==========================================
// Express Webhook 服务器
// ==========================================
const app = express();

// 需要原始 body 来做签名验证，所以用 raw 解析再手动 JSON.parse
app.use(
  "/webhook",
  express.raw({ type: "application/json" })
);

app.get("/", (req, res) => {
  res.send("QQ Bot Webhook is running.");
});

app.post("/webhook", async (req, res) => {
  const rawBody = req.body.toString("utf8");

  // 1. 签名验证 (如果header中有签名)
  const signatureHeader = req.headers["x-signature-ed25519"];
  const timestampHeader = req.headers["x-signature-timestamp"];
  if (signatureHeader && timestampHeader) {
    const msg = timestampHeader + rawBody;
    const valid = ed25519Verify(msg, signatureHeader);
    if (!valid) {
      console.warn("[Webhook] 签名验证失败, 拒绝请求");
      return res.status(401).json({ error: "Invalid signature" });
    }
  }

  // 2. 解析 payload
  let payload;
  try {
    payload = JSON.parse(rawBody);
  } catch (e) {
    console.error("[Webhook] JSON解析失败:", rawBody.substring(0, 200));
    return res.status(400).json({ error: "Invalid JSON" });
  }

  const { op, d, t } = payload;
  console.log(`[Webhook] op=${op}, t=${t || "N/A"}`);

  // 3. 回调验证挑战 (op=13) — QQ 在配置回调地址时发送
  if (op === 13) {
    const { plain_token, event_ts } = d;
    console.log(`[Webhook] 收到回调验证挑战: plain_token=${plain_token}`);

    // 签名: ed25519Sign(event_ts + plain_token)
    const signature = ed25519Sign(event_ts + plain_token);

    console.log(`[Webhook] 返回验证: signature=${signature}`);
    return res.json({ plain_token, signature });
  }

  // 4. 事件分发 (op=0)
  if (op === 0) {
    // 先返回 200 告诉 QQ 已收到
    res.status(200).json({ status: "ok" });

    // 异步处理事件
    try {
      if (t === "GROUP_AT_MESSAGE_CREATE") {
        await handleGroupMessage(d);
      } else if (t === "C2C_MESSAGE_CREATE") {
        await handleC2CMessage(d);
      } else if (t === "GROUP_ADD_ROBOT") {
        console.log(`[BOT] 机器人被添加到群: ${d.group_openid}`);
      } else if (t === "GROUP_DEL_ROBOT") {
        console.log(`[BOT] 机器人被移出群: ${d.group_openid}`);
      } else {
        console.log(`[Webhook] 未处理事件: ${t}`);
      }
    } catch (err) {
      console.error(`[Webhook] 处理事件 ${t} 出错:`, err.message);
    }
    return;
  }

  // 其他 op
  console.log(`[Webhook] 未知 op=${op}`);
  res.status(200).json({ status: "ok" });
});

// ==========================================
// 启动
// ==========================================
async function startBot() {
  try {
    // 1. 获取 access_token
    console.log("[BOT] 正在获取 access_token...");
    const tokenData = await getAccessToken();
    currentAccessToken = tokenData.access_token;
    const expiresIn = tokenData.expires_in;
    console.log(`[BOT] 成功获取 access_token，有效期: ${expiresIn}秒`);

    // 设置自动刷新
    setTimeout(refreshToken, (expiresIn - 60) * 1000);

    // 2. 启动 Express 服务器
    app.listen(WEBHOOK_PORT, () => {
      console.log(`[BOT] Webhook 服务器已启动，监听端口: ${WEBHOOK_PORT}`);
      console.log(`[BOT] 回调地址: http://localhost:${WEBHOOK_PORT}/webhook`);
      console.log("");
      console.log("=== 部署说明 ===");
      console.log("1. 配置 nginx 将外部 HTTPS 请求代理到此端口");
      console.log("   例如: https://studyapi.zhzgo.cn/qq-webhook → http://127.0.0.1:8080/webhook");
      console.log("2. 在 QQ 开放平台 → 回调配置 中填入 HTTPS 地址");
      console.log("3. 勾选需要的事件: GROUP_AT_MESSAGE_CREATE, C2C_MESSAGE_CREATE 等");
      console.log("================");
    });
  } catch (e) {
    console.error("[BOT ERROR] 启动失败:", e.response?.data || e.message);
    process.exit(1);
  }
}

startBot();
