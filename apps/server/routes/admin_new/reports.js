const express = require('express');
const router = express.Router();
const pool = require('../../config/db');
const emailService = require('../../services/emailService');

// Get all reports, with pagination
router.get('/', async (req, res) => {
    try {
        const page = parseInt(req.query.page) || 1;
        const limit = parseInt(req.query.limit) || 20;
        const status = req.query.status; // optional filter
        const offset = (page - 1) * limit;

        let query = `
            SELECT r.*, u.username, q.content as question_content
            FROM question_reports r
            JOIN users u ON r.user_id = u.id
            JOIN questions q ON r.question_id = q.id
        `;
        let countQuery = `SELECT COUNT(*) as total FROM question_reports r`;
        let params = [];
        let countParams = [];

        if (status) {
            query += ` WHERE r.status = ?`;
            countQuery += ` WHERE r.status = ?`;
            params.push(status);
            countParams.push(status);
        }

        query += ` ORDER BY r.created_at DESC LIMIT ? OFFSET ?`;
        params.push(limit, offset);

        const [rows] = await pool.query(query, params);
        const [countRows] = await pool.query(countQuery, countParams);
        
        res.json({
            data: rows,
            total: countRows[0].total,
            page,
            limit
        });
    } catch (error) {
        console.error('Error fetching reports:', error);
        res.status(500).json({ error: error.message });
    }
});

// Update report status
router.patch('/:id/status', async (req, res) => {
    const { id } = req.params;
    const { status } = req.body;

    if (!['pending', 'resolved', 'rejected'].includes(status)) {
        return res.status(400).json({ error: 'Invalid status' });
    }

    try {
        const query = status === 'pending'
            ? 'UPDATE question_reports SET status = ?, resolved_at = NULL WHERE id = ?'
            : 'UPDATE question_reports SET status = ?, resolved_at = CURRENT_TIMESTAMP WHERE id = ?';

        await pool.query(query, [status, id]);

        // Send email notification to user
        try {
            const [reportRows] = await pool.query(
                `SELECT r.status, u.email, q.content as question_content
                 FROM question_reports r
                 JOIN users u ON r.user_id = u.id
                 JOIN questions q ON r.question_id = q.id
                 WHERE r.id = ?`,
                [id]
            );
            
            if (reportRows.length > 0) {
                const report = reportRows[0];
                if (report.email && (status === 'resolved' || status === 'rejected')) {
                    const qContent = typeof report.question_content === 'object' 
                        ? (report.question_content.question || '题目') 
                        : (report.question_content || '题目');
                    
                    await emailService.sendReportStatusEmail(
                        report.email,
                        status,
                        qContent
                    );
                }
            }
        } catch (emailError) {
            console.error('Failed to send report status email:', emailError);
        }

        res.json({ message: 'Status updated successfully' });
    } catch (error) {
        console.error('Error updating report status:', error);
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
