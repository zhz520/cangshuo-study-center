const axios = require('axios');

function parseIdentity(data) {
    if (data && typeof data === 'object') return data;
    const text = String(data).trim();
    const match = /^callback\s*\(\s*(\{[\s\S]*\})\s*\)\s*;?$/.exec(text);
    return JSON.parse(match ? match[1] : text);
}

async function verifyQqIdentity(openid, accessToken, http = axios, appId = process.env.QQ_LOGIN_APP_ID) {
    if (!appId) throw Object.assign(new Error('QQ 登录尚未配置'), { status: 503 });
    if (typeof openid !== 'string' || !/^[A-Fa-f0-9]{32}$/.test(openid) || typeof accessToken !== 'string' || accessToken.length < 16 || accessToken.length > 2048) {
        throw Object.assign(new Error('QQ 身份凭据无效，请重新授权'), { status: 401 });
    }
    let identity;
    try {
        const response = await http.get('https://graph.qq.com/oauth2.0/me', {
            params: { access_token: accessToken }, timeout: 8000, maxRedirects: 0,
        });
        identity = parseIdentity(response.data);
    } catch {
        // Never log an Axios error object: its request URL includes the provider token.
        throw Object.assign(new Error('无法验证 QQ 身份，请稍后重新授权'), { status: 401 });
    }
    if (identity.error || identity.openid !== openid || String(identity.client_id) !== String(appId)) {
        throw Object.assign(new Error('QQ 身份校验失败'), { status: 401 });
    }
    return identity;
}

module.exports = { parseIdentity, verifyQqIdentity };
