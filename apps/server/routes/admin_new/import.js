const express = require('express');
const router = express.Router();
const pool = require('../../config/db');
const { saveQuestion } = require('../../services/questionStorage');
const multer = require('multer');
const xlsx = require('xlsx');

// Determine upload destination (using memory storage for processing without saving to disk, or temporary file)
// For simplicity and to avoid disk cleanup, we can use memory storage for small files.
const storage = multer.memoryStorage();
const upload = multer({ 
    storage: storage,
    limits: { fileSize: 5 * 1024 * 1024 } // 5MB limit
});

// Helper: Normalize options
function normalizeOptions(optionsRaw) {
    if (!optionsRaw) return { A: '', B: '', C: '', D: '' };
    
    // If it's already JSON string
    if (typeof optionsRaw === 'string' && (optionsRaw.startsWith('{') || optionsRaw.startsWith('['))) {
        try {
            return JSON.parse(optionsRaw);
        } catch (e) {
            // Fallthrough to text parsing
        }
    }

    // Parse text format: "A:Option1\nB:Option2" or "A. Option1"
    const opts = {};
    const lines = optionsRaw.toString().split(/\r?\n|;/); // Split by newline or semicolon
    
    lines.forEach(line => {
        line = line.trim();
        if (!line) return;
        
        // Match A: ... or A. ...
        const match = line.match(/^([A-Z])[:.](.*)/);
        if (match) {
            opts[match[1]] = match[2].trim();
        }
    });

    if (Object.keys(opts).length === 0) {
        // Fallback: try to just map lines to A, B, C...
        lines.forEach((line, idx) => {
            if (idx < 26) {
                 const label = String.fromCharCode(65 + idx);
                 opts[label] = line;
            }
        });
    }

    return opts;
}

// Helper: Normalize Answer
function normalizeAnswer(answerRaw) {
    if (!answerRaw) return [];
    
    let str = answerRaw.toString().trim().toUpperCase();
    // Remove non-alphabetic chars except commas if any
    // If "A,B", keep it. If "A B", join.
    
    // Simple extraction of A-Z
    const matches = str.match(/[A-Z]/g);
    return matches ? matches.sort() : [];
}

// Import Excel endpoint
router.post('/excel', upload.single('file'), async (req, res) => {
    if (!req.file) {
        return res.status(400).json({ error: 'No file uploaded' });
    }

    const { subject_id, major_id } = req.body;
    if (!subject_id || !major_id) {
        return res.status(400).json({ error: 'Subject and Major IDs are required' });
    }

    const connection = await pool.getConnection();

    try {
        const workbook = xlsx.read(req.file.buffer, { type: 'buffer' });
        const sheetName = workbook.SheetNames[0];
        const sheet = workbook.Sheets[sheetName];
        const data = xlsx.utils.sheet_to_json(sheet);

        if (data.length === 0) {
             return res.status(400).json({ error: 'Excel file is empty' });
        }

        await connection.beginTransaction();

        let successCount = 0;
        let errors = [];

        for (let i = 0; i < data.length; i++) {
            const row = data[i];
            const rowNum = i + 2; // +2 because header is 1, and 0-indexed

            try {
                // Map fields (Support English and Chinese headers)
                const typeRaw = row['type'] || row['类型'] || row['题目类型'];
                const content = row['content'] || row['题干'] || row['题目内容'];
                const optionsRaw = row['options'] || row['选项'];
                const answerRaw = row['answer'] || row['答案'] || row['正确答案'];
                const analysis = row['analysis'] || row['解析'] || row['答案解析'] || '';
                const difficultyRaw = row['difficulty'] || row['难度'];
                const tagsRaw = row['tags'] || row['标签'];

                if (!content) throw new Error('Existing Question content is empty');

                // Normalize Type
                let type = 'single';
                if (typeRaw) {
                    if (typeRaw.includes('单') || typeRaw === 'single') type = 'single';
                    else if (typeRaw.includes('多') || typeRaw === 'multiple') type = 'multiple';
                    else if (typeRaw.includes('判') || typeRaw === 'judge') type = 'judge';
                    else if (typeRaw.includes('填') || typeRaw === 'fill') type = 'fill';
                }

                // Normalize Options
                let options = {};
                // If judge, force options
                if (type === 'judge') {
                    options = { A: '正确', B: '错误' };
                } else if (type !== 'fill') {
                     options = normalizeOptions(optionsRaw);
                }

                // Normalize Answer
                let answer = [];
                if (type === 'fill') {
                    answer = answerRaw ? answerRaw.toString() : ''; // Keep raw string for fill
                } else if (type === 'judge') {
                     // Handle True/False, Yes/No, Correct/Incorrect
                     const a = answerRaw ? answerRaw.toString().trim() : '';
                     if (['正确', '对', '√', 'TRUE', 'YES', 'A'].includes(a.toUpperCase())) answer = ['A'];
                     else if (['错误', '错', '×', 'FALSE', 'NO', 'B'].includes(a.toUpperCase())) answer = ['B'];
                     else answer = normalizeAnswer(a);
                } else {
                    answer = normalizeAnswer(answerRaw);
                }

                // Normalize Difficulty
                let difficulty = 1;
                if (difficultyRaw) {
                    const d = parseInt(difficultyRaw);
                    if ([1, 2, 3].includes(d)) difficulty = d;
                    else if (difficultyRaw.includes('中')) difficulty = 2;
                    else if (difficultyRaw.includes('难')) difficulty = 3;
                }

                // Normalize Tags
                let tags = [];
                if (tagsRaw) {
                    if (typeof tagsRaw === 'string') {
                        tags = tagsRaw.split(/[,，、]/).map(t => t.trim()).filter(t => t);
                    }
                }

                await saveQuestion(connection, 'questions', { subject_id, type, difficulty,
                    tags: JSON.stringify(tags), content, options: JSON.stringify(options),
                    answer: Array.isArray(answer) ? JSON.stringify(answer) : answer, analysis });

                successCount++;

            } catch (err) {
                errors.push(`Row ${rowNum}: ${err.message}`);
            }
        }

        await connection.commit();

        res.json({
            message: `Import completed. ${successCount} imported.`,
            successCount,
            errors: errors.length > 0 ? errors : undefined
        });

    } catch (error) {
        await connection.rollback();
        res.status(500).json({ error: error.message });
    } finally {
        connection.release();
    }
});

module.exports = router;
