const express = require("express");
const router = express.Router();
const pool = require("../../config/db");
const { sendPushNotification } = require("../../services/notificationService");
const emailService = require("../../services/emailService");

// GET /admin/articles — List all articles (admin, no content)
router.get("/", async (req, res) => {
  try {
    const [rows] = await pool.query(
      `SELECT a.id, a.title, a.summary, a.cover_url, a.category, a.is_published, a.is_pinned, a.view_count, a.created_at, a.updated_at,
                    u.username as author_name
             FROM articles a
             LEFT JOIN users u ON a.author_id = u.id
             ORDER BY a.is_pinned DESC, a.created_at DESC`,
    );
    res.json(rows);
  } catch (err) {
    console.error("Error fetching articles:", err);
    res.status(500).json({ error: "获取文章列表失败" });
  }
});

// GET /admin/articles/detail/:id — 单篇文章详情（含 content），显式路径避免与 PUT/DELETE :id 冲突
router.get("/detail/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const [rows] = await pool.query("SELECT * FROM articles WHERE id = ?", [
      id,
    ]);
    if (rows.length === 0) return res.status(404).json({ error: "文章不存在" });
    res.json(rows[0]);
  } catch (err) {
    console.error("Error fetching article:", err);
    res.status(500).json({ error: "获取文章失败" });
  }
});

// POST /admin/articles — Create article
router.post("/", async (req, res) => {
  try {
    const {
      title,
      summary,
      content,
      content_markdown,
      content_format,
      cover_url,
      category,
      is_published,
      is_pinned,
    } = req.body;
    if (!title) return res.status(400).json({ error: "标题不能为空" });

    const format = content_format || "html";

    const [result] = await pool.query(
      "INSERT INTO articles (title, summary, content, content_markdown, content_format, cover_url, category, is_published, is_pinned, author_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
      [
        title,
        summary || null,
        content || null,
        content_markdown || null,
        format,
        cover_url || null,
        category || "未分类",
        is_published !== undefined ? is_published : 1,
        is_pinned || 0,
        req.userId,
      ],
    );
    res.json({ id: result.insertId, message: "文章创建成功" });
  } catch (err) {
    console.error("Error creating article:", err);
    res.status(500).json({ error: "创建文章失败" });
  }
});

// PUT /admin/articles/:id — Update article (Supports partial updates)
router.put("/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const fields = req.body;

    if (Object.keys(fields).length === 0) {
      return res.status(400).json({ error: "没有需要更新的内容" });
    }

    const updates = [];
    const params = [];

    // Define allowed fields and their default/transformation logic
    const allowedFields = [
      "title",
      "summary",
      "content",
      "content_markdown",
      "content_format",
      "cover_url",
      "category",
      "is_published",
      "is_pinned",
    ];

    allowedFields.forEach((field) => {
      if (field in fields) {
        updates.push(`${field} = ?`);
        let value = fields[field];

        // Special handling for defaults/nulls as in original code
        if (field === "title" && !value) {
          // Title is still required if provided
          throw new Error("标题不能为空");
        }
        if (field === "category" && !value) value = "未分类";
        if (field === "is_published" && value === undefined) value = 1;
        if (field === "is_pinned" && value === undefined) value = 0;

        // For other fields, empty string becomes null for consistency
        if (
          ["summary", "content", "content_markdown", "cover_url"].includes(
            field,
          ) &&
          value === ""
        ) {
          value = null;
        }

        params.push(value);
      }
    });

    if (updates.length === 0) {
      return res.status(400).json({ error: "没有有效的更新字段" });
    }

    const query = `UPDATE articles SET ${updates.join(", ")} WHERE id = ?`;
    params.push(id);

    const [result] = await pool.query(query, params);
    if (result.affectedRows === 0)
      return res.status(404).json({ error: "文章不存在" });

    const is_published = fields.is_published;

    // --- Notify author if published (1) or rejected (2) ---
    if (is_published == 1 || is_published == 2) {
      try {
        const [articleRows] = await pool.query(
          "SELECT a.title, a.author_id, u.email FROM articles a LEFT JOIN users u ON a.author_id = u.id WHERE a.id = ?",
          [id],
        );
        if (articleRows.length > 0) {
          const article = articleRows[0];
          
          // 1. Send Email Notification
          if (article.email) {
            await emailService.sendArticleStatusEmail(
              article.email,
              article.title,
              is_published
            );
          }

          // 2. Send Push and Internal Notification
          if (article.author_id) {
            const [tokenRows] = await pool.query(
              "SELECT push_token FROM user_push_tokens WHERE user_id = ?",
              [article.author_id],
            );
            
            const title = is_published == 1 ? "文章已发布" : "文章解析未通过";
            const message = is_published == 1 
              ? `您的文章《${article.title}》已通过审核并发布！`
              : `您的文章《${article.title}》未通过审核，请检查内容后重新提交。`;
            const type = is_published == 1 ? "article_published" : "article_rejected";

            if (tokenRows.length > 0) {
              await sendPushNotification(
                tokenRows[0].push_token,
                title,
                message,
                { type, articleId: id },
              );
            }

            await pool.query(
              "INSERT INTO notifications (user_id, title, message, type, link_id) VALUES (?, ?, ?, ?, ?)",
              [article.author_id, title, message, "article", id],
            );
          }
        }
      } catch (notifyErr) {
        console.error("Failed to notify author:", notifyErr);
      }
    }
    // ---------------------------------------

    res.json({ message: "文章更新成功" });
  } catch (err) {
    if (err.message === "标题不能为空") {
      return res.status(400).json({ error: err.message });
    }
    console.error("Error updating article:", err);
    res.status(500).json({ error: "更新文章失败" });
  }
});

// DELETE /admin/articles/:id — Delete article
router.delete("/:id", async (req, res) => {
  try {
    const { id } = req.params;
    const [result] = await pool.query("DELETE FROM articles WHERE id = ?", [
      id,
    ]);
    if (result.affectedRows === 0)
      return res.status(404).json({ error: "文章不存在" });
    res.json({ message: "文章删除成功" });
  } catch (err) {
    console.error("Error deleting article:", err);
    res.status(500).json({ error: "删除文章失败" });
  }
});

module.exports = router;
