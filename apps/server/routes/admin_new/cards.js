const express = require('express');
const router = express.Router();
const pool = require('../../config/db');
const crypto = require('node:crypto');

// Generate Activation Cards
router.post('/generate', async (req, res) => {
    const { count, duration_days } = req.body;
    if (!Number.isSafeInteger(Number(count)) || Number(count) < 1 || Number(count) > 1000 || !Number.isSafeInteger(Number(duration_days)) || Number(duration_days) < 1 || Number(duration_days) > 36500) {
        return res.status(400).json({ error: '无效的数量或时长' });
    }

    try {
        const generatedCards = [];
        for (let i = 0; i < count; i++) {
            const code = 'PLUS-' + crypto.randomBytes(12).toString('hex').toUpperCase().match(/.{8}/g).join('-');
            generatedCards.push([code, duration_days]);
        }

        await pool.query(
            'INSERT INTO activation_cards (code, duration_days) VALUES ?',
            [generatedCards]
        );

        res.json({ message: `成功生成 ${count} 张卡密`, count });
    } catch (error) {
        console.error('Error generating cards:', error);
        res.status(500).json({ error: error.message });
    }
});

// List Activation Cards
router.get('/', async (req, res) => {
    try {
        const page = parseInt(req.query.page) || 1;
        const limit = parseInt(req.query.limit) || 20;
        const offset = (page - 1) * limit;
        const status = req.query.status;

        let query = 'SELECT c.*, u.username as used_by_name FROM activation_cards c LEFT JOIN users u ON c.used_by = u.id';
        let countQuery = 'SELECT COUNT(*) as total FROM activation_cards c';
        const queryParams = [];

        if (status === 'used') {
            query += ' WHERE c.is_used = TRUE';
            countQuery += ' WHERE c.is_used = TRUE';
        } else if (status === 'unused') {
            query += ' WHERE c.is_used = FALSE';
            countQuery += ' WHERE c.is_used = FALSE';
        }

        query += ' ORDER BY c.created_at DESC LIMIT ? OFFSET ?';
        queryParams.push(limit, offset);

        const [rows] = await pool.query(query, queryParams);
        const [countResult] = await pool.query(countQuery);

        res.json({
            data: rows,
            pagination: {
                total: countResult[0].total,
                page,
                limit,
                totalPages: Math.ceil(countResult[0].total / limit)
            }
        });
    } catch (error) {
        console.error('Error fetching cards:', error);
        res.status(500).json({ error: error.message });
    }
});

// Delete a Card (Only unused cards ideally, but admin can do whatever)
router.delete('/:id', async (req, res) => {
    try {
        await pool.query('DELETE FROM activation_cards WHERE id = ?', [req.params.id]);
        res.json({ message: '卡密已删除' });
    } catch (error) {
        console.error('Error deleting card:', error);
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
