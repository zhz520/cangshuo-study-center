const express = require('express');
const router = express.Router();
const pool = require('../../config/db');

// ================= STATISTICS =================

router.get('/', async (req, res) => {
    try {
        // 1. Questions per Major
        const [majorStats] = await pool.query(`
            SELECT m.name, COUNT(q.id) as count 
            FROM majors m 
            LEFT JOIN subjects s ON m.id = s.major_id 
            LEFT JOIN questions q ON s.id = q.subject_id 
            GROUP BY m.id 
            HAVING count > 0
            ORDER BY count DESC
        `);

        // 2. Questions per Type
        const [typeStats] = await pool.query(`
            SELECT type, COUNT(*) as count 
            FROM questions 
            GROUP BY type
        `);

        // 3. Recent Users (Last 7 days count)
        const [userTrend] = await pool.query(`
            SELECT DATE(created_at) as date, COUNT(*) as count 
            FROM users 
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) 
            GROUP BY DATE(created_at) 
            ORDER BY date ASC
        `);

        res.json({
            majorStats,
            typeStats,
            userTrend
        });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
