const express = require('express');
const cors = require('cors');
require('dotenv').config();
require('./config/security').jwtConfig();
require('./config/security').encryptionKey();

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = process.env.LISTEN_HOST || '127.0.0.1';
if (process.env.TRUSTED_PROXIES) app.set('trust proxy', process.env.TRUSTED_PROXIES.split(',').map(value => value.trim()));
app.disable('x-powered-by');
app.use((req, res, next) => {
    res.set('X-Content-Type-Options', 'nosniff');
    if (req.path.startsWith('/api/')) res.set('Cache-Control', 'private, no-store');
    if (req.secure) res.set('Strict-Transport-Security', 'max-age=31536000');
    if (process.env.NODE_ENV === 'production' && !req.secure) return res.status(426).json({ error: 'HTTPS required' });
    next();
});

// Configure CORS to allow specific domains
app.use(cors({
    origin: ['https://study.zhzgo.cn', 'http://localhost:5174', 'http://localhost:5173'],
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization'],
    exposedHeaders: ['X-Question-Access', 'X-Question-Page-Limit', 'X-Question-Preview-Limit', 'X-Question-Has-More', 'Retry-After']
}));

// Simple request logger
app.use((req, res, next) => {
    console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
    next();
});

app.use(express.json({ limit: '1mb' }));
// Serve static files from 'public' directory
app.use(express.static('public'));


// Routes
const authRoutes = require('./routes/auth');
const dataRoutes = require('./routes/data');

app.use('/api/auth', authRoutes);
app.use('/api/data', dataRoutes);
app.use('/api/data', require('./routes/exports'));
app.use('/api/public', require('./routes/public'));
app.use('/api/admin', require('./routes/admin_new'));
app.use('/api/upload', require('./routes/upload'));
app.use('/api/app', require('./routes/app')); // Added App updates route
app.use('/api/bot', require('./routes/bot')); // Independent route for QQ Bot

app.get('/', (req, res) => {
    res.send('Study Center API is running.');
});

async function start() {
    const pool = require('./config/db');
    try {
        await require('./services/protectedStorageReady').protectedStorageReady(pool);
        return app.listen(PORT, HOST, () => console.log(`Server is running on ${HOST}:${PORT}`));
    } catch {
        console.error('Protected storage is not ready. Check server keys, run security:storage -- --apply, then --verify before starting.');
        await pool.end();
        process.exitCode = 1;
    }
}

if (require.main === module) start();
module.exports = app;
// Restart trigger 2026-02-16
