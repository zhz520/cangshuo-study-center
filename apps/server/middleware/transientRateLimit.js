// Bounded burst protection; long-term question/AI budgets stay in MySQL.
function transientRateLimit(scope, limit, seconds = 60) {
    const buckets = new Map();
    return (req, res, next) => {
        const now = Date.now();
        const key = `${req.userId || req.ip}`;
        if (buckets.size >= 10000) for (const [id, value] of buckets) if (value.expires <= now) buckets.delete(id);
        const current = buckets.get(key);
        if (!current || current.expires <= now) {
            if (!current && buckets.size >= 10000) return res.status(503).json({ error: '服务繁忙，请稍后重试' });
            buckets.set(key, { count: 1, expires: now + seconds * 1000 });
            return next();
        }
        if (++current.count > limit) return res.set('Retry-After', String(Math.max(1, Math.ceil((current.expires - now) / 1000))))
            .status(429).json({ error: '操作过于频繁，请稍后重试', code: 'RATE_LIMITED' });
        next();
    };
}
module.exports = { transientRateLimit };
