const express = require('express');
const router = express.Router();
const pool = require('../../config/db');
const { writeQuestion, saveQuestion, keywordFilter } = require('../../services/questionStorage');
const { pageOptions } = require('../../services/questionAccess');

// ================= QUESTION MANAGEMENT =================

// Get questions (with pagination and filtering)
router.get('/', async (req, res) => {
    let paging;
    try { paging = pageOptions(req.query); } catch (error) { return res.status(400).json({ error: error.message }); }
    const { page, limit } = paging;
    const subject_id = req.query.subject_id;
    const major_id = req.query.major_id;
    const type = req.query.type;
    const search = req.query.search;
    // New filters
    const difficulty = req.query.difficulty;
    const tags = req.query.tags;

    const offset = (page - 1) * limit;

    try {
        // Select new columns
        let query = `
            SELECT q.id, q.subject_id, s.major_id, m.name as major_name, q.type, q.difficulty, q.tags, q.content, q.options, q.answer, q.analysis, q.is_active, s.name as subject_name
            FROM questions q
            LEFT JOIN subjects s ON q.subject_id = s.id
            LEFT JOIN majors m ON s.major_id = m.id
        `;
        let countQuery = `
            SELECT COUNT(*) as total
            FROM questions q
            LEFT JOIN subjects s ON q.subject_id = s.id
        `;
        const params = [];

        const conditions = [];
        if (subject_id) {
            conditions.push('q.subject_id = ?');
            params.push(subject_id);
        } else if (major_id) {
            conditions.push('s.major_id = ?');
            params.push(major_id);
        }
        if (type) {
            conditions.push('q.type = ?');
            params.push(type);
        }
        if (difficulty) {
            conditions.push('q.difficulty = ?');
            params.push(difficulty);
        }
        // Basic tag search (JSON_CONTAINS or LIKE) - simple LIKE for now
        if (tags) {
            conditions.push('JSON_SEARCH(q.tags, "one", ?) IS NOT NULL');
            params.push(tags);
        }

        if (search) {
            const filter = keywordFilter(String(search).slice(0, 80));
            conditions.push(filter.sql);
            params.push(...filter.params);
        }

        if (conditions.length > 0) {
            const whereClause = ' WHERE ' + conditions.join(' AND ');
            query += whereClause;
            countQuery += whereClause;
        }

        query += ' ORDER BY q.id DESC LIMIT ? OFFSET ?';

        // Count query params (slice of params)
        // Note: We need to be careful with params if we added extra ones for count (but here countQuery uses same WHERE structure)
        // However, countQuery params must match the WHERE clause params.
        // We constructed `params` for the WHERE clause + limit/offset at the end.
        // For countQuery, we need `params` excluding limit/offset.

        const countParams = [...params]; // Copy params before adding limit/offset

        const [countRows] = await pool.query(countQuery, countParams);
        const total = countRows[0].total;

        params.push(limit, offset);
        const [rows] = await pool.query(query, params);

        res.json({
            data: rows,
            pagination: {
                total,
                page,
                limit,
                totalPages: Math.ceil(total / limit)
            }
        });
    } catch (error) {
        res.status(error.status || 500).json({ error: error.status ? error.message : '题库操作失败，请稍后重试' });
    }
});

// Add new question
router.post('/', async (req, res) => {
    const { subject_id, type, difficulty, tags, content, options, answer, analysis, is_active } = req.body;
    try {
        await writeQuestion(pool, 'questions', { subject_id, type, difficulty: difficulty || 1,
            tags: JSON.stringify(tags || []), content, options: JSON.stringify(options), answer, analysis,
            is_active: is_active !== undefined ? (is_active ? 1 : 0) : 1 });
        res.status(201).json({ message: 'Question added' });
    } catch (error) {
        res.status(error.status || 500).json({ error: error.status ? error.message : '题库操作失败，请稍后重试' });
    }
});

// Update question
router.put('/:id', async (req, res) => {
    const { id } = req.params;
    const { subject_id, type, difficulty, tags, content, options, answer, analysis, is_active } = req.body;
    try {
        await writeQuestion(pool, 'questions', { subject_id, type, difficulty,
            tags: JSON.stringify(tags || []), content, options: JSON.stringify(options), answer, analysis,
            is_active: is_active !== undefined ? (is_active ? 1 : 0) : 1 }, id);
        res.json({ message: 'Question updated' });
    } catch (error) {
        res.status(error.status || 500).json({ error: error.status ? error.message : '题库操作失败，请稍后重试' });
    }
});

// Delete question
router.delete('/:id', async (req, res) => {
    const { id } = req.params;
    try {
        await pool.query('DELETE FROM questions WHERE id = ?', [id]);
        res.json({ message: 'Question deleted' });
    } catch (error) {
        res.status(error.status || 500).json({ error: error.status ? error.message : '题库操作失败，请稍后重试' });
    }
});

// ================= JSON BATCH IMPORT =================

// POST /admin/questions/import
// Body: { major_id, subject_id, questions: [ { type, stem, options, answer, analysis, difficulty, tags } ] }
router.post('/import', async (req, res) => {
    const { subject_id, questions } = req.body;

    if (!subject_id) {
        return res.status(400).json({ error: '缺少科目 ID (subject_id)' });
    }
    if (!Array.isArray(questions) || questions.length === 0) {
        return res.status(400).json({ error: '题目数据为空或格式不正确，需要 questions 数组' });
    }

    const connection = await pool.getConnection();

    try {
        await connection.beginTransaction();

        let successCount = 0;
        const errors = [];

        for (let i = 0; i < questions.length; i++) {
            const q = questions[i];
            const idx = i + 1;

            try {
                // Accept both "stem" and "content" as the question text field
                const content = q.stem || q.content || '';
                if (!content) {
                    errors.push(`题目 #${idx}: 题干为空`);
                    continue;
                }

                const type = q.type || 'single';
                const analysis = q.analysis || '';
                const difficulty = q.difficulty || 1;

                // --- Normalize tags ---
                let tags = q.tags || [];
                if (typeof tags === 'string') {
                    tags = tags.split(/[,，、]/).map(t => t.trim()).filter(t => t);
                }

                // The storage boundary owns normalization and rejects ambiguous labels.
                const options = q.options;
                const answer = q.answer;

                await saveQuestion(connection, 'questions', { subject_id, type, difficulty,
                    tags: JSON.stringify(tags), content, options, answer, analysis });

                successCount++;
            } catch (err) {
                errors.push(`题目 #${idx}: ${err.message}`);
            }
        }

        await connection.commit();

        res.json({
            message: `导入完成，成功 ${successCount} 道题目`,
            successCount,
            totalCount: questions.length,
            errors: errors.length > 0 ? errors : undefined
        });

    } catch (error) {
        await connection.rollback();
        res.status(500).json({ error: '导入失败: ' + error.message });
    } finally {
        connection.release();
    }
});

module.exports = router;
