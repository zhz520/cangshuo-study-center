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

function parseExcelRow(row) {
    const typeRaw = String(row['type'] || row['类型'] || row['题目类型'] || '').trim();
    const content = row['content'] || row['题干'] || row['题目内容'];
    const optionsRaw = row['options'] || row['选项'];
    const answerRaw = row['answer'] || row['答案'] || row['正确答案'];
    const analysis = row['analysis'] || row['解析'] || row['答案解析'] || '';
    const difficultyRaw = row['difficulty'] || row['难度'];
    const tagsRaw = row['tags'] || row['标签'];
    if (!content) throw new Error('题干不能为空');

    let type = 'single';
    if (typeRaw) {
        if (typeRaw.includes('单') || typeRaw === 'single') type = 'single';
        else if (typeRaw.includes('多') || typeRaw === 'multiple') type = 'multiple';
        else if (typeRaw.includes('判') || typeRaw === 'judge') type = 'judge';
        else if (typeRaw.includes('填') || typeRaw === 'fill') type = 'fill';
    }

    let options = {};
    if (type === 'judge') options = { A: '正确', B: '错误' };
    else if (type !== 'fill') options = normalizeOptions(optionsRaw);

    let answer = [];
    if (type === 'fill') answer = answerRaw ? answerRaw.toString() : '';
    else if (type === 'judge') {
        const value = answerRaw ? answerRaw.toString().trim() : '';
        if (['正确', '对', '√', 'TRUE', 'YES', 'A'].includes(value.toUpperCase())) answer = ['A'];
        else if (['错误', '错', '×', 'FALSE', 'NO', 'B'].includes(value.toUpperCase())) answer = ['B'];
        else answer = normalizeAnswer(value);
    } else answer = normalizeAnswer(answerRaw);

    let difficulty = 1;
    if (difficultyRaw) {
        const text = String(difficultyRaw);
        const value = parseInt(text);
        if ([1, 2, 3].includes(value)) difficulty = value;
        else if (text.includes('中')) difficulty = 2;
        else if (text.includes('难')) difficulty = 3;
    }

    let tags = [];
    if (tagsRaw && typeof tagsRaw === 'string') {
        tags = tagsRaw.split(/[,，、]/).map(tag => tag.trim()).filter(Boolean);
    }
    return { type, content, options, answer, analysis, difficulty, tags };
}

// Import Excel endpoint
router.post('/excel', upload.single('file'), async (req, res) => {
    if (!req.file) {
        return res.status(400).json({ error: 'No file uploaded' });
    }

    const { subject_id, major_id } = req.body;
    const subjectId = Number(subject_id);
    const majorId = Number(major_id);
    if (!Number.isSafeInteger(subjectId) || subjectId <= 0 || !Number.isSafeInteger(majorId) || majorId <= 0) {
        return res.status(400).json({ error: '请选择有效的专业和科目' });
    }

    const connection = await pool.getConnection();

    try {
        const [subjects] = await connection.query(
            'SELECT id FROM subjects WHERE id = ? AND major_id = ?',
            [subjectId, majorId],
        );
        if (!subjects.length) {
            return res.status(400).json({ error: '科目不属于所选专业，请重新选择' });
        }
        const workbook = xlsx.read(req.file.buffer, { type: 'buffer' });
        const sheetName = workbook.SheetNames[0];
        const sheet = workbook.Sheets[sheetName];
        const data = xlsx.utils.sheet_to_json(sheet);

        if (data.length === 0) {
             return res.status(400).json({ error: 'Excel file is empty' });
        }
        if (data.length > 5000) {
             return res.status(400).json({ error: '单次最多导入 5000 道题目' });
        }

        await connection.beginTransaction();

        let successCount = 0;
        let errors = [];

        for (let i = 0; i < data.length; i++) {
            const row = data[i];
            const rowNum = i + 2; // +2 because header is 1, and 0-indexed

            try {
                const record = parseExcelRow(row);
                await saveQuestion(connection, 'questions', { subject_id: subjectId, ...record,
                    tags: JSON.stringify(record.tags), options: JSON.stringify(record.options),
                    answer: Array.isArray(record.answer) ? JSON.stringify(record.answer) : record.answer });

                successCount++;

            } catch (err) {
                errors.push(`Row ${rowNum}: ${err.message}`);
            }
        }

        await connection.commit();

        res.json({
            message: `导入完成，成功 ${successCount} 道题目`,
            successCount,
            failureCount: errors.length,
            totalCount: data.length,
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
module.exports.parseExcelRow = parseExcelRow;
