import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { prepareQuestionRequest, openQuestionResponse, protectedQuestionPath } from '../src/utils/question-transport.js';
const require = createRequire(import.meta.url);
const { questionTransport, protectedPath } = require('../../server/services/questionTransport.js');

function respond(request, payload, { url = '/api/data/questions?subject_id=10&page=2', method = 'GET', authorization = 'Bearer fixture-token' } = {}) {
    let response;
    const headers = {};
    const req = { originalUrl: url, method, get(name) { return { ...request?.headers, Authorization: authorization }[name]; } };
    const res = { statusCode: 200, set(name, value) { headers[name] = value; },
        status(value) { this.statusCode = value; return this; }, json(value) { response = value; return this; } };
    questionTransport(req, res, () => res.json(payload));
    return { status: res.statusCode, response, headers };
}

test('WebCrypto client decrypts the actual Node ECDH/HKDF/AES-GCM response with ephemeral non-extractable keys', async () => {
    const request = await prepareQuestionRequest('GET', '/api/data/questions?subject_id=10&page=2', 'Bearer fixture-token');
    assert.equal(request.privateKey.extractable, false);
    const payload = [{ content: '中文数学题 😀 $x^2$', options: { A: '正确' }, answer: null }];
    const result = respond(request, payload);
    assert.equal(result.status, 200);
    assert.match(result.headers['Cache-Control'], /no-store/);
    assert.ok(!JSON.stringify(result.response).includes('中文数学题'));
    assert.deepEqual(await openQuestionResponse(result.response, request), payload);
    const second = respond(request, payload);
    assert.notEqual(second.response.data, result.response.data);
    assert.notEqual(second.response.key, result.response.key);
});

test('wrong account token, request URL, method, private key and tampered response are rejected', async () => {
    const url = '/api/data/questions?subject_id=10&page=2';
    const request = await prepareQuestionRequest('GET', url, 'Bearer fixture-token');
    for (const change of [{ authorization: 'Bearer other-token' }, { url: url + '&type=single' }, { method: 'POST' }]) {
        await assert.rejects(openQuestionResponse(respond(request, ['secret'], change).response, request));
    }
    const result = respond(request, ['secret']).response;
    const other = await prepareQuestionRequest('GET', url, 'Bearer fixture-token');
    await assert.rejects(openQuestionResponse(result, { ...request, privateKey: other.privateKey }));
    const data = Buffer.from(result.data, 'base64'); data[0] ^= 1;
    await assert.rejects(openQuestionResponse({ ...result, data: data.toString('base64') }, request));
    await assert.rejects(openQuestionResponse(['plaintext fallback'], request));
    await assert.rejects(openQuestionResponse({ ...result, request: '0'.repeat(32) }, request));
});

test('missing or malformed client keys cannot downgrade a protected route to plaintext', () => {
    for (const headers of [undefined, {}, { 'X-Study-Key': 'bad', 'X-Study-Nonce': '0'.repeat(32) }]) {
        const result = respond({ headers }, ['must not be sent']);
        assert.equal(result.status, 426); assert.equal(result.response.code, 'QUESTION_TRANSPORT_REQUIRED');
        assert.ok(!JSON.stringify(result).includes('must not be sent'));
    }
});

test('server and Web protect all question entry points, with explicit export and unrelated APIs preserved', () => {
    for (const url of ['/api/data/questions', '/api/data/questions/1', '/api/data/questions/1/review', '/api/data/questions/search',
        '/api/data/favorites', '/api/data/favorites/practice', '/api/data/mistakes', '/api/data/recommendation', '/api/data/progress',
        '/api/data/exams/records', '/api/data/exams/1/start', '/api/data/exams/1/submit', '/api/admin/questions',
        '/api/admin/exams/records/1/details', '/api/admin/reports']) {
        assert.equal(protectedPath(url), true, url); assert.equal(protectedQuestionPath(url), true, url);
    }
    for (const url of ['/api/data/export/questions', '/api/data/subjects', '/api/auth/login', '/api/data/exams', '/api/data/favorites/ids']) {
        assert.equal(protectedPath(url), false, url); assert.equal(protectedQuestionPath(url), false, url);
    }
});
