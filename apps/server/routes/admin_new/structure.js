const express = require('express');
const router = express.Router();
const pool = require('../../config/db');

// ================= MAJOR MANAGEMENT =================

// Get Majors
router.get('/majors', async (req, res) => {
    try {
        const [rows] = await pool.query('SELECT * FROM majors ORDER BY id');
        res.json(rows);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Create/Update Major
router.post('/majors', async (req, res) => {
    const { id, name } = req.body;
    try {
        if (id) {
            await pool.query('UPDATE majors SET name = ? WHERE id = ?', [name, id]);
        } else {
            await pool.query('INSERT INTO majors (name) VALUES (?)', [name]);
        }
        res.json({ message: 'Major saved' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Delete Major
router.delete('/majors/:id', async (req, res) => {
    try {
        await pool.query('DELETE FROM majors WHERE id = ?', [req.params.id]);
        res.json({ message: 'Major deleted' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// ================= SUBJECT MANAGEMENT =================

// Get Subjects
router.get('/subjects', async (req, res) => {
    const { major_id } = req.query;
    try {
        let query = 'SELECT s.*, m.name as major_name FROM subjects s JOIN majors m ON s.major_id = m.id';
        const params = [];
        if (major_id) {
            query += ' WHERE s.major_id = ?';
            params.push(major_id);
        }
        query += ' ORDER BY s.id';
        const [rows] = await pool.query(query, params);
        res.json(rows);
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Create/Update Subject
router.post('/subjects', async (req, res) => {
    const { id, major_id, name } = req.body;
    try {
        if (id) {
            await pool.query('UPDATE subjects SET major_id = ?, name = ? WHERE id = ?', [major_id, name, id]);
        } else {
            await pool.query('INSERT INTO subjects (major_id, name) VALUES (?, ?)', [major_id, name]);
        }
        res.json({ message: 'Subject saved' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

// Delete Subject
router.delete('/subjects/:id', async (req, res) => {
    try {
        await pool.query('DELETE FROM subjects WHERE id = ?', [req.params.id]);
        res.json({ message: 'Subject deleted' });
    } catch (error) {
        res.status(500).json({ error: error.message });
    }
});

module.exports = router;
