const VERSION = 'study-question-v1';
const encoder = new TextEncoder();
const DATA_PATH = /^\/api\/data\/(?:questions(?:\/|$)|mistakes\/?$|favorites(?:\/practice)?\/?$|recommendation\/?$|progress\/?$|exams\/(?:records\/?$|\d+\/(?:start|submit)\/?$))/i;
const ADMIN_PATH = /^\/api\/admin\/(?:questions|reports|exams)(?:\/|$)/i;
export const protectedQuestionPath = url => DATA_PATH.test(new URL(url, 'https://local.invalid').pathname) || ADMIN_PATH.test(new URL(url, 'https://local.invalid').pathname);
const bytes = value => Uint8Array.from(atob(value), char => char.charCodeAt(0));
const base64 = value => btoa(String.fromCharCode(...new Uint8Array(value)));
const hex = value => [...new Uint8Array(value)].map(byte => byte.toString(16).padStart(2, '0')).join('');

export async function prepareQuestionRequest(method, url, authorization = '') {
    const path = new URL(url, 'https://local.invalid');
    if (!protectedQuestionPath(url)) return null;
    if (!globalThis.crypto?.subtle) throw new Error('安全题库需要 HTTPS，请使用安全连接或更新浏览器');
    const pair = await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits']);
    const nonce = hex(crypto.getRandomValues(new Uint8Array(16)));
    const hash = hex(await crypto.subtle.digest('SHA-256', encoder.encode(authorization)));
    const aad = encoder.encode(`${VERSION}\n${method.toUpperCase()}\n${path.pathname}${path.search}\n${nonce}\n${hash}`);
    return { privateKey: pair.privateKey, nonce, aad, headers: {
        'X-Study-Key': base64(await crypto.subtle.exportKey('spki', pair.publicKey)), 'X-Study-Nonce': nonce,
    } };
}

export async function openQuestionResponse(payload, request) {
    if (!request || payload?.protected !== VERSION || payload.request !== request.nonce) throw new Error('题库安全通道校验失败，请刷新后重试');
    const publicKey = await crypto.subtle.importKey('spki', bytes(payload.key), { name: 'ECDH', namedCurve: 'P-256' }, false, []);
    const shared = await crypto.subtle.deriveBits({ name: 'ECDH', public: publicKey }, request.privateKey, 256);
    const source = await crypto.subtle.importKey('raw', shared, 'HKDF', false, ['deriveKey']);
    new Uint8Array(shared).fill(0);
    const key = await crypto.subtle.deriveKey({ name: 'HKDF', hash: 'SHA-256', salt: bytes(payload.salt), info: request.aad }, source,
        { name: 'AES-GCM', length: 256 }, false, ['decrypt']);
    const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: bytes(payload.iv), additionalData: request.aad, tagLength: 128 }, key, bytes(payload.data));
    try { return JSON.parse(new TextDecoder().decode(plain)); }
    finally { new Uint8Array(plain).fill(0); }
}
