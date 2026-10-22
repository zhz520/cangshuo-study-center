const fs = require('fs');
const path = require('path');

const file = path.resolve(__dirname, 'node_modules/qq-guild-bot/lib/index.js');
if (!fs.existsSync(file)) {
    console.error('qq-guild-bot not found in node_modules!');
    process.exit(1);
}

let code = fs.readFileSync(file, 'utf8');
let changed = false;

// ===== 1. 修改 REST API 鉴权头：Bot xxx -> QQBot xxx =====
const restTarget = "header['Authorization'] = \"Bot \".concat(appID, \".\").concat(token);";
if (code.includes(restTarget)) {
    code = code.replace(
        restTarget,
        "header['Authorization'] = \"QQBot \" + String(token).replace(/^Bot\\s/,\"\");"
    );
    changed = true;
    console.log('[PATCH] REST Auth header patched.');
}

// ===== 2. 修改 WebSocket 鉴权头：Bot xxx -> QQBot xxx =====
const wsTarget = 'token: "Bot ".concat(this.config.appID, ".").concat(this.config.token),';
if (code.includes(wsTarget)) {
    code = code.replace(
        wsTarget,
        'token: "QQBot " + String(this.config.token).replace(/^Bot\\s/,""),'
    );
    changed = true;
    console.log('[PATCH] WebSocket Auth token patched.');
}

// ===== 3. 添加 GROUP_AT_MESSAGE_CREATE intent (1 << 25) =====
// 在 IntentEvents 对象中添加 GROUP_AT_MESSAGE_CREATE
if (!code.includes('GROUP_AT_MESSAGE_CREATE')) {
    // 在 intents 映射 (如 PUBLIC_GUILD_MESSAGES: 1 << 30) 后面添加群聊 intent
    const intentsTarget = 'PUBLIC_GUILD_MESSAGES: 1 << 30';
    if (code.includes(intentsTarget)) {
        code = code.replace(
            intentsTarget,
            'PUBLIC_GUILD_MESSAGES: 1 << 30,\n  GROUP_AT_MESSAGE_CREATE: 1 << 25'
        );
        changed = true;
        console.log('[PATCH] GROUP_AT_MESSAGE_CREATE intent added.');
    }
}

// ===== 4. 让 WebSocket 对未知事件类型也 emit，这样可以监听 GROUP_AT_MESSAGE_CREATE =====
// 找到事件分发逻辑，在找不到对应 WsEventType 时直接 emit 原始事件类型名
// SDK 中事件分发的关键逻辑大致是: 
//   var eventType = WsEventType[eventName]; 
//   if(eventType) this.emit(eventType, ...); 
// 我们需要在 else 分支也 emit

// 用一种更稳健的方式: 用 monkey-patch 在 index.js 里直接处理原始 WebSocket 消息
// 所以这里只需要确保 intent 被正确添加

if (changed) {
    fs.writeFileSync(file, code, 'utf8');
    console.log('[PATCH] Successfully patched qq-guild-bot.');
} else {
    console.log('[PATCH] qq-guild-bot is already patched or targets not found.');
}
