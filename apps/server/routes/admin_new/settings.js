const express = require('express');
const router = express.Router();
const pool = require('../../config/db');

// ================= SETTINGS MANAGEMENT =================

// Get settings
router.get('/settings', async (req, res) => {
    try {
        const [rows] = await pool.query('SELECT * FROM settings');
        const settings = rows.reduce((acc, row) => {
            acc[row.setting_key] = row.setting_value;
            return acc;
        }, {});
        res.json(settings);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Update settings
router.post('/settings', async (req, res) => {
    const { key, value } = req.body;
    try {
        await pool.query(
            'INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE setting_value = ?',
            [key, value, value]
        );
        res.json({ message: 'Setting updated' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ================= COUNTDOWN MANAGEMENT =================

router.get('/countdowns', async (req, res) => {
    try {
        const [rows] = await pool.query('SELECT * FROM countdowns ORDER BY sort_order ASC');
        res.json(rows);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

router.post('/countdowns', async (req, res) => {
    const { id, title, target_date, icon, sort_order } = req.body;
    try {
        if (id) {
            await pool.query('UPDATE countdowns SET title=?, target_date=?, icon=?, sort_order=? WHERE id=?', [title, target_date, icon, sort_order || 0, id]);
        } else {
            await pool.query('INSERT INTO countdowns (title, target_date, icon, sort_order) VALUES (?, ?, ?, ?)', [title, target_date, icon, sort_order || 0]);
        }
        res.json({ message: 'Countdown saved' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

router.delete('/countdowns/:id', async (req, res) => {
    try {
        await pool.query('DELETE FROM countdowns WHERE id=?', [req.params.id]);
        res.json({ message: 'Deleted' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ================= LINK MANAGEMENT =================

router.get('/links', async (req, res) => {
    try {
        const [rows] = await pool.query('SELECT * FROM external_links ORDER BY sort_order ASC');
        res.json(rows);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Create or Update Link
router.post('/links', async (req, res) => {
    const { id, name, description, url, icon, color, sort_order } = req.body;
    try {
        if (id) {
            await pool.query(
                'UPDATE external_links SET name=?, description=?, url=?, icon=?, color=?, sort_order=? WHERE id=?',
                [name, description, url, icon, color, sort_order, id]
            );
        } else {
            await pool.query(
                'INSERT INTO external_links (name, description, url, icon, color, sort_order) VALUES (?, ?, ?, ?, ?, ?)',
                [name, description, url, icon, color, sort_order || 0]
            );
        }
        res.json({ message: 'Link saved' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

router.delete('/links/:id', async (req, res) => {
    try {
        await pool.query('DELETE FROM external_links WHERE id=?', [req.params.id]);
        res.json({ message: 'Deleted' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
