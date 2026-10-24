const crypto = require('node:crypto');

const VERSION = 'study-question-v1';
const DATA_PATH = /^\/api\/data\/(?:questions(?:\/|$)|mistakes\/?$|favorites(?:\/practice)?\/?$|recommendation\/?$|progress\/?$|exams\/(?:records\/?$|\d+\/(?:start|submit)\/?$))/i;
const ADMIN_PATH = /^\/api\/admin\/(?:questions|reports|exams)(?:\/|$)/i;

function protectedPath(url) {
    const path = url.split('?')[0];
    return DATA_PATH.test(path) || ADMIN_PATH.test(path);
}

function context(method, url, nonce, authorization) {
    const tokenHash = crypto.createHash('sha256').update(authorization || '').digest('hex');
    return Buffer.from(`${VERSION}\n${method}\n${url}\n${nonce}\n${tokenHash}`);
}

// This is an extra layer over HTTPS, not a way to hide content from an authorized
// modified client. No database key, shared APK secret or permanent client key is used.
function questionTransport(req, res, next) {
    if (!protectedPath(req.originalUrl) || req.questionTransport) return next();
    let publicKey;
    const nonce = req.get('X-Study-Nonce');
    const encoded = req.get('X-Study-Key');
    try {
        if (!/^[a-f0-9]{32}$/.test(nonce || '') || !/^[A-Za-z0-9+/]{100,200}={0,2}$/.test(encoded || '')) throw new Error();
        publicKey = crypto.createPublicKey({ key: Buffer.from(encoded, 'base64'), type: 'spki', format: 'der' });
        if (publicKey.asymmetricKeyType !== 'ec' || publicKey.asymmetricKeyDetails.namedCurve !== 'prime256v1') throw new Error();
    } catch {
        return res.status(426).json({ error: '请刷新网页或更新 App 后使用安全题库通道', code: 'QUESTION_TRANSPORT_REQUIRED' });
    }
    req.questionTransport = true;
    const aad = context(req.method, req.originalUrl, nonce, req.get('Authorization'));
    const send = res.json.bind(res);
    res.set('Cache-Control', 'private, no-store, max-age=0');
    res.set('Pragma', 'no-cache');
    res.set('X-Study-Transport', VERSION);
    res.json = payload => {
        if (res.statusCode >= 400) return send(payload);
        const pair = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
        const shared = crypto.diffieHellman({ privateKey: pair.privateKey, publicKey });
        const salt = crypto.randomBytes(16);
        const key = Buffer.from(crypto.hkdfSync('sha256', shared, salt, aad, 32));
        shared.fill(0);
        const iv = crypto.randomBytes(12);
        const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
        cipher.setAAD(aad);
        const ciphertext = Buffer.concat([cipher.update(JSON.stringify(payload), 'utf8'), cipher.final(), cipher.getAuthTag()]);
        key.fill(0);
        return send({ protected: VERSION, request: nonce, key: pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'),
            salt: salt.toString('base64'), iv: iv.toString('base64'), data: ciphertext.toString('base64') });
    };
    next();
}

module.exports = { VERSION, protectedPath, context, questionTransport };
