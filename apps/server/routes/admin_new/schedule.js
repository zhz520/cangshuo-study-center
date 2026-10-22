const express = require('express');
const router = express.Router();
const pool = require('../../config/db');

// ================= SCHEDULE MANAGEMENT =================

// Get full schedule
router.get('/', async (req, res) => {
    try {
        const [rows] = await pool.query(
            'SELECT * FROM schedules ORDER BY sort_order ASC, day_of_week ASC'
        );
        res.json(rows);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Batch save/update schedule (replaces entire schedule)
router.post('/batch', async (req, res) => {
    const { schedule_name, cells } = req.body;
    const name = schedule_name || '默认课程表';
    const conn = await pool.getConnection();
    try {
        await conn.beginTransaction();
        // Delete existing schedule with this name
        await conn.query('DELETE FROM schedules WHERE schedule_name = ?', [name]);
        // Insert all cells
        if (cells && cells.length > 0) {
            const values = cells.map((cell, idx) => [
                name,
                cell.period,
                cell.day_of_week,
                cell.subject || '',
                cell.location || '',
                cell.sort_order ?? idx
            ]);
            await conn.query(
                'INSERT INTO schedules (schedule_name, period, day_of_week, subject, location, sort_order) VALUES ?',
                [values]
            );
        }
        await conn.commit();
        res.json({ message: '课程表已保存' });
    } catch (error) {
        await conn.rollback();
        res.status(500).json({ error: error.message });
    } finally {
        conn.release();
    }
});

// Update single cell
router.put('/cell', async (req, res) => {
    const { id, subject, location } = req.body;
    try {
        if (id) {
            await pool.query(
                'UPDATE schedules SET subject = ?, location = ? WHERE id = ?',
                [subject || '', location || '', id]
            );
        }
        res.json({ message: '已更新' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Delete single cell
router.delete('/:id', async (req, res) => {
    try {
        await pool.query('DELETE FROM schedules WHERE id = ?', [req.params.id]);
        res.json({ message: '已删除' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
