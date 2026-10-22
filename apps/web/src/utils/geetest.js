const LEGACY_CAPTCHA_ID = '0c2ea0b252d4053d77b5e658f5654620';
let cache = null;

async function authConfig() {
    if (cache && Date.now() - cache.at < 300000) return cache.value;
    try {
        const response = await fetch('/api/public/auth-config', { headers: { Accept: 'application/json' } });
        if (!response.ok) return null;
        const data = await response.json();
        const value = data?.geetest || null;
        cache = { at: Date.now(), value };
        return value;
    } catch {
        return null;
    }
}

export const verifyGeetest = async () => {
    const config = await authConfig();
    if (config && config.enabled === false) return {};
    if (config && !config.webId) {
        throw new Error('极验尚未配置，请联系管理员');
    }
    const captchaId = config?.webId || LEGACY_CAPTCHA_ID;
    return new Promise((resolve, reject) => {
        if (!window.initGeetest4) {
            reject(new Error('极验 SDK 未加载完成，请刷新重试'));
            return;
        }
        window.initGeetest4({ captchaId, product: 'bind' }, (captcha) => {
            captcha.onReady(() => {
                captcha.showCaptcha();
            }).onSuccess(() => {
                const result = captcha.getValidate();
                if (!result) {
                    reject(new Error('验证失败'));
                    return;
                }
                resolve({
                    lot_number: result.lot_number,
                    captcha_output: result.captcha_output,
                    pass_token: result.pass_token,
                    gen_time: result.gen_time,
                    client_type: 'web',
                });
            }).onError((err) => {
                reject(err || new Error('极验验证发生错误'));
            }).onClose(() => {
                reject(new Error('用户取消了验证'));
            });
        });
    });
};
