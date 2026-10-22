const express = require('express');
const router = express.Router();
const pool = require('../../config/db');
const emailService = require('../../services/emailService');

// GET /admin/comments - List all comments with user and article info
router.get('/', async (req, res) => {
    const { page = 1, limit = 20, search = '', status = '' } = req.query;
    const offset = (page - 1) * limit;

    try {
        let query = `
            SELECT 
                c.*, 
                u.username, u.avatar_icon,
                a.title as article_title
            FROM article_comments c
            JOIN users u ON c.user_id = u.id
            JOIN articles a ON c.article_id = a.id
            WHERE 1=1
        `;
        const params = [];

        if (status) {
            query += ` AND c.status = ?`;
            params.push(status);
        }

        if (search) {
            query += ` AND (c.content LIKE ? OR u.username LIKE ? OR a.title LIKE ?)`;
            const searchVal = `%${search}%`;
            params.push(searchVal, searchVal, searchVal);
        }

        query += ` ORDER BY c.created_at DESC LIMIT ? OFFSET ?`;
        params.push(parseInt(limit), parseInt(offset));

        const [rows] = await pool.query(query, params);
        
        // Get total count for pagination
        let countQuery = `
            SELECT COUNT(*) as total 
            FROM article_comments c
            JOIN users u ON c.user_id = u.id
            JOIN articles a ON c.article_id = a.id
            WHERE 1=1
        `;
        const countParams = [];
        if (status) {
            countQuery += ` AND c.status = ?`;
            countParams.push(status);
        }
        if (search) {
            countQuery += ` AND (c.content LIKE ? OR u.username LIKE ? OR a.title LIKE ?)`;
            const searchVal = `%${search}%`;
            countParams.push(searchVal, searchVal, searchVal);
        }
        const [countRows] = await pool.query(countQuery, countParams);

        res.json({
            data: rows,
            total: countRows[0].total,
            page: parseInt(page),
            limit: parseInt(limit)
        });
    } catch (err) {
        console.error('Error fetching admin comments:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// PATCH /admin/comments/:id/status - Update comment status (Audit)
router.patch('/:id/status', async (req, res) => {
    const { id } = req.params;
    const { status } = req.body;

    if (!['pending', 'approved', 'rejected'].includes(status)) {
        return res.status(400).json({ error: 'Invalid status' });
    }

    try {
        await pool.query('UPDATE article_comments SET status = ? WHERE id = ?', [status, id]);
        
        // Send email notification to user
        try {
            const [commentRows] = await pool.query(
                `SELECT c.content, u.email, a.title as article_title
                 FROM article_comments c
                 JOIN users u ON c.user_id = u.id
                 JOIN articles a ON c.article_id = a.id
                 WHERE c.id = ?`,
                [id]
            );
            
            if (commentRows.length > 0) {
                const comment = commentRows[0];
                if (comment.email && (status === 'approved' || status === 'rejected')) {
                    await emailService.sendCommentAuditEmail(
                        comment.email,
                        comment.article_title,
                        status,
                        comment.content
                    );
                }
            }
        } catch (emailError) {
            console.error('Failed to send comment audit email:', emailError);
        }

        res.json({ message: `Comment status updated to ${status}` });
    } catch (err) {
        console.error('Error updating comment status:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

// DELETE /admin/comments/:id - Delete a comment
router.delete('/:id', async (req, res) => {
    const { id } = req.params;
    try {
        const [result] = await pool.query('DELETE FROM article_comments WHERE id = ?', [id]);
        if (result.affectedRows === 0) {
            return res.status(404).json({ error: 'Comment not found' });
        }
        res.json({ message: 'Comment deleted successfully' });
    } catch (err) {
        console.error('Error deleting admin comment:', err);
        res.status(500).json({ error: 'Internal server error' });
    }
});

module.exports = router;
