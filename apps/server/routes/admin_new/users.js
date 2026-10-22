const express = require('express');
const router = express.Router();
const pool = require('../../config/db');
const bcrypt = require('bcryptjs');

// ================= USER MANAGEMENT =================

// Get all users
router.get('/', async (req, res) => {
    try {
        const [rows] = await pool.query('SELECT id, username, role, subscription_end_date, created_at FROM users ORDER BY created_at DESC');
        res.json(rows);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Create new user (Admin)
router.post('/', async (req, res) => {
    const { username, password, role, subscription_end_date } = req.body;
    if (!username || !password) {
        return res.status(400).json({ message: 'Username and password are required' });
    }
    try {
        const hashedPassword = await bcrypt.hash(password, 10);
        await pool.query(
            'INSERT INTO users (username, password, role, subscription_end_date) VALUES (?, ?, ?, ?)',
            [username, hashedPassword, role || 'user', subscription_end_date || null]
        );
        res.status(201).json({ message: 'User created successfully' });
    } catch (error) {
        if (error.code === 'ER_DUP_ENTRY') {
            return res.status(409).json({ message: 'Username already exists' });
        }
        res.status(500).json({ error: error.message });
    }
});

// Update user role
router.patch('/:id/role', async (req, res) => {
    const { id } = req.params;
    const { role } = req.body;
    
    if (!['user', 'admin', 'demo'].includes(role)) {
        return res.status(400).json({ message: 'Invalid role' });
    }

    try {
        await pool.query('UPDATE users SET role = ? WHERE id = ?', [role, id]);
        res.json({ message: 'User role updated' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Update user details
router.put('/:id', async (req, res) => {
    const { id } = req.params;
    const { username, password, role, subscription_end_date } = req.body;
    
    try {
        const updates = [];
        const params = [];

        if (username) {
            updates.push('username = ?');
            params.push(username);
        }
        if (role) {
            updates.push('role = ?');
            params.push(role);
        }
        if (subscription_end_date !== undefined) {
             updates.push('subscription_end_date = ?');
             params.push(subscription_end_date ? subscription_end_date : null);
        }
        if (password) {
            const hashedPassword = await bcrypt.hash(password, 10);
            updates.push('password = ?');
            params.push(hashedPassword);
        }

        if (updates.length === 0) {
            return res.status(400).json({ message: 'No fields to update' });
        }

        const query = `UPDATE users SET ${updates.join(', ')} WHERE id = ?`;
        params.push(id);

        await pool.query(query, params);
        res.json({ message: 'User updated successfully' });
    } catch (error) {
        if (error.code === 'ER_DUP_ENTRY') {
            return res.status(409).json({ message: 'Username already exists' });
        }
        res.status(500).json({ error: error.message });
    }
});

// Delete user
router.delete('/:id', async (req, res) => {
    const { id } = req.params;
    const connection = await pool.getConnection();
    try {
        await connection.beginTransaction();

        // 1. Delete user progress
        await connection.query('DELETE FROM user_progress WHERE user_id = ?', [id]);

        // 2. Delete user
        await connection.query('DELETE FROM users WHERE id = ?', [id]);

        await connection.commit();
        res.json({ message: 'User deleted' });
    } catch (error) {
        await connection.rollback();
        res.status(500).json({ error: error.message });
    } finally {
        connection.release();
    }
});

module.exports = router;
