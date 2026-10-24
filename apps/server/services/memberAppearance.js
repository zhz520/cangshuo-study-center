const pool = require('../config/db');
const FRAMES = Object.freeze(['none', 'obsidian', 'champagne', 'azure']);
let ready;
function ensureTable() {
    if (!ready) ready = pool.query(`CREATE TABLE IF NOT EXISTS user_appearance (
        user_id INT NOT NULL PRIMARY KEY,
        avatar_frame VARCHAR(24) NOT NULL DEFAULT 'obsidian',
        badge_visible BOOLEAN NOT NULL DEFAULT TRUE,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    )`).catch(error => { ready = null; throw error; });
    return ready;
}
function isPlus(user) {
    if (user.role === 'admin') return true;
    if (user.has_subscription != null) return Boolean(Number(user.has_subscription));
    return new Date(user.subscription_end_date).getTime() > Date.now();
}
function presentation(user, saved) {
    const active = isPlus(user);
    return {
        is_plus: active,
        avatar_frame: active ? (FRAMES.includes(saved?.avatar_frame) ? saved.avatar_frame : 'obsidian') : 'none',
        badge_visible: active && (saved?.badge_visible == null || Boolean(Number(saved.badge_visible))),
    };
}
async function forUsers(users) {
    const ids = users.filter(isPlus).map(u => Number(u.id ?? u.user_id)).filter(id => Number.isSafeInteger(id) && id > 0);
    let saved = [];
    if (ids.length) {
        await ensureTable();
        [saved] = await pool.query('SELECT user_id, avatar_frame, badge_visible FROM user_appearance WHERE user_id IN (?)', [ids]);
    }
    const map = new Map(saved.map(row => [Number(row.user_id), row]));
    return users.map(user => presentation(user, map.get(Number(user.id ?? user.user_id))));
}
function validate(input) {
    if (!input || !FRAMES.includes(input.avatar_frame) || typeof input.badge_visible !== 'boolean') {
        const error = new Error('请选择有效的头像装饰与会员标识'); error.status = 400; throw error;
    }
    return { avatar_frame: input.avatar_frame, badge_visible: input.badge_visible };
}
async function save(user, input) {
    if (!isPlus(user)) { const error = new Error('Plus 会员到期，请续费后设置专属装扮'); error.status = 403; throw error; }
    const value = validate(input);
    await ensureTable();
    await pool.query(`INSERT INTO user_appearance (user_id, avatar_frame, badge_visible) VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE avatar_frame = VALUES(avatar_frame), badge_visible = VALUES(badge_visible)`,
    [user.id, value.avatar_frame, value.badge_visible]);
    return presentation(user, value);
}
module.exports = { FRAMES, isPlus, presentation, forUsers, save, validate };
