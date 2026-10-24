const test = require('node:test');
const assert = require('node:assert/strict');
const appearance = require('../services/memberAppearance');

test('expired membership cannot retain a VIP frame or badge after renewal lapses', () => {
    const result = appearance.presentation(
        { id: 1, role: 'user', subscription_end_date: '2020-01-01T00:00:00.000Z' },
        { avatar_frame: 'champagne', badge_visible: 1 },
    );
    assert.deepEqual(result, { is_plus: false, avatar_frame: 'none', badge_visible: false });
});

test('admin accounts use live server membership and a safe default frame', () => {
    const result = appearance.presentation({ id: 2, role: 'admin', subscription_end_date: null }, null);
    assert.deepEqual(result, { is_plus: true, avatar_frame: 'obsidian', badge_visible: true });
});

test('saved Plus appearance is reflected only from the server allowlist', () => {
    const active = { id: 3, role: 'user', has_subscription: 1 };
    assert.deepEqual(
        appearance.presentation(active, { avatar_frame: 'azure', badge_visible: 0 }),
        { is_plus: true, avatar_frame: 'azure', badge_visible: false },
    );
    assert.deepEqual(
        appearance.presentation(active, { avatar_frame: 'injected-frame', badge_visible: 1 }),
        { is_plus: true, avatar_frame: 'obsidian', badge_visible: true },
    );
});

test('invalid appearance input and expired saves are rejected before any write', async () => {
    assert.throws(() => appearance.validate({ avatar_frame: 'x', badge_visible: true }), /有效/);
    await assert.rejects(
        appearance.save({ id: 4, role: 'user', subscription_end_date: null }, { avatar_frame: 'azure', badge_visible: true }),
        error => error.status === 403 && /续费/.test(error.message),
    );
});
