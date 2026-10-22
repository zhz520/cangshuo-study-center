const express = require('express');
const router = express.Router();
const pool = require('../../config/db');

// ================= DAILY CONTENT MANAGEMENT =================

// Get all daily contents (with pagination and optional type filter)
router.get('/', async (req, res) => {
    const { type, page = 1, limit = 10 } = req.query;
    const offset = (page - 1) * limit;

    try {
        let query = 'SELECT * FROM daily_contents';
        let countQuery = 'SELECT COUNT(*) as total FROM daily_contents';
        const params = [];

        if (type) {
            query += ' WHERE type = ?';
            countQuery += ' WHERE type = ?';
            params.push(type);
        }
        
        query += ' ORDER BY id DESC LIMIT ? OFFSET ?';
        params.push(Number(limit), Number(offset));

        const [rows] = await pool.query(query, params);
        
        // Get total count
        const [countResult] = await pool.query(countQuery, type ? [type] : []);
        const total = countResult[0].total;

        res.json({
            data: rows,
            pagination: {
                total,
                page: Number(page),
                limit: Number(limit),
                totalPages: Math.ceil(total / limit)
            }
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Create or Update Daily Content
router.post('/', async (req, res) => {
    const { id, content, translation, type } = req.body;
    try {
        if (id) {
            await pool.query(
                'UPDATE daily_contents SET content=?, translation=?, type=? WHERE id=?',
                [content, translation, type, id]
            );
        } else {
            await pool.query(
                'INSERT INTO daily_contents (content, translation, type) VALUES (?, ?, ?)',
                [content, translation, type]
            );
        }
        res.json({ message: 'Content saved' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Delete Daily Content
router.delete('/:id', async (req, res) => {
    try {
        await pool.query('DELETE FROM daily_contents WHERE id=?', [req.params.id]);
        res.json({ message: 'Content deleted' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
