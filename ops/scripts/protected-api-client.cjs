// Test/deployment client only. Never included in Web bundles or APKs.
const crypto = require('node:crypto');
const root = process.env.STUDY_SERVER_ROOT || '/www/wwwroot/study_center';
const { protectedPath, context } = require(root + '/services/questionTransport');

function prepare(method, url, authorization) {
    const pair = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    const nonce = crypto.randomBytes(16).toString('hex');
    const aad = context(method, url, nonce, authorization);
    return { headers: { 'X-Study-Key': pair.publicKey.export({ type: 'spki', format: 'der' }).toString('base64'), 'X-Study-Nonce': nonce },
        open(payload) {
            if (payload?.protected !== 'study-question-v1' || payload.request !== nonce) throw new Error('Protected response required');
            const shared = crypto.diffieHellman({ privateKey: pair.privateKey, publicKey: crypto.createPublicKey({ key: Buffer.from(payload.key, 'base64'), type: 'spki', format: 'der' }) });
            const key = Buffer.from(crypto.hkdfSync('sha256', shared, Buffer.from(payload.salt, 'base64'), aad, 32));
            const data = Buffer.from(payload.data, 'base64');
            const decipher = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(payload.iv, 'base64'));
            decipher.setAAD(aad); decipher.setAuthTag(data.subarray(-16));
            return JSON.parse(Buffer.concat([decipher.update(data.subarray(0, -16)), decipher.final()]).toString('utf8'));
        } };
}
module.exports = { prepare, protectedPath };
