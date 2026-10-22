const express = require("express");
const router = express.Router();
const pool = require("../../config/db");
const { writeQuestion } = require("../../services/questionStorage");

// GET /admin/exams — 获取所有试卷列表
router.get("/", async (req, res) => {
  try {
    const [papers] = await pool.query(`
      SELECT ep.*, 
        (SELECT COUNT(*) FROM exam_paper_questions WHERE paper_id = ep.id) as question_count,
        (SELECT COUNT(*) FROM exam_records WHERE paper_id = ep.id AND status = 'completed') as attempt_count
      FROM exam_papers ep
      ORDER BY ep.created_at DESC
    `);
    res.json(papers);
  } catch (err) {
    console.error("获取试卷列表失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// GET /admin/exams/:id — 获取试卷详情
router.get("/:id", async (req, res) => {
  try {
    const [papers] = await pool.query(
      "SELECT * FROM exam_papers WHERE id = ?",
      [req.params.id],
    );
    if (papers.length === 0)
      return res.status(404).json({ error: "试卷不存在" });

    const [questions] = await pool.query(
      "SELECT * FROM exam_paper_questions WHERE paper_id = ? ORDER BY question_index",
      [req.params.id],
    );

    res.json({ ...papers[0], questions });
  } catch (err) {
    console.error("获取试卷详情失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// GET /admin/exams/:id/records — 查看某试卷的所有考试记录
router.get("/:id/records", async (req, res) => {
  try {
    const paperId = req.params.id;
    const page = parseInt(req.query.page) || 1;
    const limit = parseInt(req.query.limit) || 10;
    const offset = (page - 1) * limit;

    const [records] = await pool.query(
      `
      SELECT er.*, u.username, u.nickname, u.avatar_icon as avatar
      FROM exam_records er
      JOIN users u ON er.user_id = u.id
      WHERE er.paper_id = ?
      ORDER BY er.start_time DESC
      LIMIT ? OFFSET ?
    `,
      [paperId, limit, offset],
    );

    const [[{ total }]] = await pool.query(
      "SELECT COUNT(*) as total FROM exam_records WHERE paper_id = ?",
      [paperId],
    );

    res.json({ records, total });
  } catch (err) {
    console.error("获取考试记录失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// GET /admin/exams/records/:recordId/details — 获取考试记录详情（含题目与答案）
router.get("/records/:recordId/details", async (req, res) => {
  try {
    const recordId = req.params.recordId;

    // 1. 获取记录、用户和试卷基础信息
    const [recordInfo] = await pool.query(
      `
      SELECT er.*, u.username, u.nickname, ep.title as paper_title, ep.pass_score, ep.total_score
      FROM exam_records er
      JOIN users u ON er.user_id = u.id
      JOIN exam_papers ep ON er.paper_id = ep.id
      WHERE er.id = ?
    `,
      [recordId],
    );

    if (recordInfo.length === 0)
      return res.status(404).json({ error: "考试记录不存在" });
    const record = recordInfo[0];

    // 2. 获取该试卷的所有题目
    const [questions] = await pool.query(
      "SELECT * FROM exam_paper_questions WHERE paper_id = ? ORDER BY question_index ASC",
      [record.paper_id],
    );

    // 3. 获取用户在该场考试的答题详情
    const [userAnswers] = await pool.query(
      "SELECT * FROM exam_record_answers WHERE record_id = ?",
      [recordId],
    );

    // 4. 将用户答案映射到题目中
    const detailedQuestions = questions.map((q) => {
      const userAnswer = userAnswers.find((ua) => ua.question_id === q.id);
      return {
        ...q,
        options:
          typeof q.options === "string" ? JSON.parse(q.options) : q.options,
        user_answer: userAnswer ? userAnswer.user_answer : null,
        is_correct: userAnswer ? !!userAnswer.is_correct : false,
        earned_score: userAnswer ? userAnswer.earned_score : 0,
      };
    });

    res.json({
      record: {
        id: record.id,
        username: record.username,
        nickname: record.nickname,
        paper_title: record.paper_title,
        score: record.score,
        total_score: record.total_score,
        pass_score: record.pass_score,
        status: record.status,
        start_time: record.start_time,
        end_time: record.end_time,
        passed: !!record.passed,
      },
      questions: detailedQuestions,
    });
  } catch (err) {
    console.error("获取考试详情失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// PATCH /admin/exams/records/:recordId/retry — 允许/取消重考
router.patch("/records/:recordId/retry", async (req, res) => {
  try {
    const { can_retry } = req.body;
    const [result] = await pool.query(
      "UPDATE exam_records SET can_retry = ? WHERE id = ?",
      [can_retry ? 1 : 0, req.params.recordId],
    );
    if (result.affectedRows === 0)
      return res.status(404).json({ error: "记录不存在" });
    res.json({ message: can_retry ? "已允许重考" : "已取消重考权限" });
  } catch (err) {
    console.error("更新重考权限失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// PATCH /admin/exams/:id — 更新试卷状态
router.patch("/:id", async (req, res) => {
  try {
    const {
      is_active,
      title,
      description,
      duration_minutes,
      pass_score,
      instructions,
    } = req.body;
    const updates = [];
    const values = [];

    if (is_active !== undefined) {
      updates.push("is_active = ?");
      values.push(is_active ? 1 : 0);
    }
    if (title) {
      updates.push("title = ?");
      values.push(title);
    }
    if (description !== undefined) {
      updates.push("description = ?");
      values.push(description);
    }
    if (duration_minutes) {
      updates.push("duration_minutes = ?");
      values.push(duration_minutes);
    }
    if (pass_score) {
      updates.push("pass_score = ?");
      values.push(pass_score);
    }
    if (instructions !== undefined) {
      updates.push("instructions = ?");
      values.push(instructions);
    }

    if (updates.length === 0)
      return res.status(400).json({ error: "无更新内容" });

    values.push(req.params.id);
    await pool.query(
      `UPDATE exam_papers SET ${updates.join(", ")} WHERE id = ?`,
      values,
    );
    res.json({ message: "更新成功" });
  } catch (err) {
    console.error("更新试卷失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// --- 题目管理 ---

// POST /admin/exams/:id/questions — 添加题目
router.post("/:id/questions", async (req, res) => {
  try {
    const paperId = req.params.id;
    const { type, content, options, answer, analysis, score, question_index, is_active } =
      req.body;

    // 获取当前最大索引
    let qIndex = question_index;
    if (qIndex === undefined) {
      const [[{ maxIndex }]] = await pool.query(
        "SELECT MAX(question_index) as maxIndex FROM exam_paper_questions WHERE paper_id = ?",
        [paperId],
      );
      qIndex = (maxIndex || 0) + 1;
    }

    const id = await writeQuestion(pool, 'exam_paper_questions', {
      paper_id: paperId, type, content, options: typeof options === 'string' ? options : JSON.stringify(options),
      answer, analysis, score: score || 1, question_index: qIndex,
      is_active: is_active !== undefined ? (is_active ? 1 : 0) : 1,
    });
    res.json({ message: "添加成功", id });
  } catch (err) {
    console.error("添加题目失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// PATCH /admin/exams/questions/:id — 编辑题目
router.patch("/questions/:id", async (req, res) => {
  try {
    const { type, content, options, answer, analysis, score, question_index, is_active } =
      req.body;
    const changes = Object.fromEntries(Object.entries({ type, content,
      options: options === undefined ? undefined : (typeof options === 'string' ? options : JSON.stringify(options)),
      answer, analysis, score, question_index,
      is_active: is_active === undefined ? undefined : (is_active ? 1 : 0),
    }).filter(([, value]) => value !== undefined));
    if (!Object.keys(changes).length) return res.status(400).json({ error: '无更新内容' });
    await writeQuestion(pool, 'exam_paper_questions', changes, req.params.id);
    res.json({ message: "更新成功" });
  } catch (err) {
    console.error("更新题目失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// DELETE /admin/exams/questions/:id — 删除题目
router.delete("/questions/:id", async (req, res) => {
  try {
    await pool.query("DELETE FROM exam_paper_questions WHERE id = ?", [
      req.params.id,
    ]);
    res.json({ message: "删除成功" });
  } catch (err) {
    console.error("删除题目失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

module.exports = router;
