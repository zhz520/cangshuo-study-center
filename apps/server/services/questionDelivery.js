const crypto = require('node:crypto');
const { encryptionKey } = require('../config/security');
const { unpackJson } = require('../utils/questionFormat');

function ticketKey() {
    return crypto.createHmac('sha256', encryptionKey()).update('study-question-read-ticket-v1').digest();
}

function readTicket(req, questionId, now = Date.now()) {
    const payload = Buffer.from(JSON.stringify([Number(req.userId), Number(questionId), now + 3600000, crypto.randomBytes(12).toString('hex')])).toString('base64url');
    return `${payload}.${crypto.createHmac('sha256', ticketKey()).update(payload).digest('base64url')}`;
}

function verifyReadTicket(req, questionId, ticket, now = Date.now()) {
    try {
        if (typeof ticket !== 'string' || ticket.length > 400) throw new Error();
        const [payload, signature, extra] = ticket.split('.');
        if (extra !== undefined || !signature) throw new Error();
        const expected = crypto.createHmac('sha256', ticketKey()).update(payload).digest();
        const actual = Buffer.from(signature, 'base64url');
        if (actual.length !== expected.length || !crypto.timingSafeEqual(actual, expected)) throw new Error();
        const [user, question, expiry] = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
        if (user !== Number(req.userId) || question !== Number(questionId) || expiry <= now || expiry > now + 3600000) throw new Error();
    } catch {
        throw Object.assign(new Error('题目阅读凭证已失效，请重新加载本题后提交'), { status: 403, code: 'QUESTION_READ_REQUIRED' });
    }
}

function hideNestedAnswers(options, type) {
    if (type !== 'material') return options;
    const parsed = unpackJson(options);
    return Array.isArray(parsed) ? parsed.map(item => {
        const { answer, analysis, correct_answer, ...visible } = item;
        return { ...visible, options: hideNestedAnswers(item.options, item.type) };
    }) : [];
}

function practiceQuestion(req, row) {
    return { ...row, answer: null, analysis: null, options: hideNestedAnswers(row.options, row.type), answer_token: readTicket(req, row.id) };
}

module.exports = { readTicket, verifyReadTicket, practiceQuestion, hideNestedAnswers };
