// Explicit allowlist: new operational settings stay private by default.
const PUBLIC_SETTINGS = Object.freeze([
    'allow_registration', 'site_name', 'site_title', 'site_description', 'site_keywords',
    'site_notice', 'announcement', 'announcement_enabled', 'notice', 'footer_text',
    'app_announcement', 'app_announcement_enabled', 'app_download_url', 'android_download_url',
    'contact_email', 'contact_qq', 'contact_wechat', 'qq_group', 'qq_group_link',
    'icp_number', 'police_record', 'ai_day_limit_free', 'ai_day_limit_plus',
]);
const allowed = new Set(PUBLIC_SETTINGS);

function publicSettings(rows) {
    return Object.fromEntries(rows.filter(row => allowed.has(row.setting_key)).map(row => [row.setting_key, row.setting_value]));
}

module.exports = { PUBLIC_SETTINGS, publicSettings };
