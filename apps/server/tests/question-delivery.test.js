const { test } = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
process.env.QUESTION_ENCRYPTION_KEY = crypto.randomBytes(32).toString('base64');
const { readTicket, verifyReadTicket, practiceQuestion } = require('../services/questionDelivery');
const { scoreAnswer } = require('../utils/scoreAnswer');

test('reading tickets expire and cannot be forged, swapped across users or questions', () => {
    const req = { userId: 12 };
    const now = 100000;
    const ticket = readTicket(req, 18, now);
    verifyReadTicket(req, 18, ticket, now);
    for (const [user, question, value, time] of [[13, 18, ticket, now], [12, 19, ticket, now], [12, 18, ticket, now + 3600001],
        [12, 18, ticket + '.extra', now], [12, 18, ticket.slice(1), now], [12, 18, null, now]]) {
        assert.throws(() => verifyReadTicket({ userId: user }, question, value, time), { code: 'QUESTION_READ_REQUIRED' });
    }
});

test('unanswered material questions recursively remove nested answers without changing stored content', () => {
    const row = { id: 18, type: 'material', content: '题干', answer: 'private-top', analysis: 'private-analysis', options: JSON.stringify([
        { type: 'single', content: '小题', answer: 'private-single', analysis: 'private-detail', options: { A: '甲', B: '乙' } },
        { type: 'material', answer: 'private-nested', options: [{ type: 'fill', content: '填空', answer: 'private-fill' }] },
    ]) };
    const publicRow = practiceQuestion({ userId: 12 }, row);
    assert.equal(publicRow.answer, null); assert.equal(publicRow.analysis, null);
    assert.ok(!JSON.stringify(publicRow).includes('private-'));
    assert.ok(JSON.stringify(row).includes('private-single'));
    assert.equal(publicRow.options[0].options.A, '甲');
});

test('material scoring remains on the server and supports nested subquestion types', () => {
    const question = { type: 'material', options: [{ type: 'single', answer: 'A' }, { type: 'multiple', answer: 'A,C' }, { type: 'judge', answer: 'A' }] };
    assert.equal(scoreAnswer(question, { 0: 'A', 1: ['C', 'A'], 2: 'TRUE' }), true);
    assert.equal(scoreAnswer(question, { 0: 'A', 1: ['C'], 2: 'TRUE' }), false);
    assert.equal(scoreAnswer(question, {}), false);
});
