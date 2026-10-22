const express = require("express");
const router = express.Router();
const pool = require("../../config/db");

// GET /admin/access-control — 获取所有用户的 major 访问权限
router.get("/", async (req, res) => {
  try {
    const [rows] = await pool.query(`
      SELECT mac.id, mac.user_id, mac.major_id, mac.created_at as granted_at,
        u.username, u.nickname, u.avatar_icon as avatar,
        m.name as major_name
      FROM major_access_control mac
      JOIN users u ON mac.user_id = u.id
      JOIN majors m ON mac.major_id = m.id
      ORDER BY mac.created_at DESC
    `);
    res.json(rows);
  } catch (err) {
    console.error("获取访问控制列表失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// GET /admin/access-control/user/:userId — 获取某用户的访问权限
router.get("/user/:userId", async (req, res) => {
  try {
    const [rows] = await pool.query(
      `
      SELECT mac.id, mac.major_id, mac.created_at as granted_at, m.name as major_name
      FROM major_access_control mac
      JOIN majors m ON mac.major_id = m.id
      WHERE mac.user_id = ?
      ORDER BY m.name
    `,
      [req.params.userId],
    );
    res.json(rows);
  } catch (err) {
    console.error("获取用户权限失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// POST /admin/access-control — 授予用户访问某 major
router.post("/", async (req, res) => {
  try {
    const { user_id, major_id } = req.body;
    if (!user_id || !major_id)
      return res.status(400).json({ error: "缺少 user_id 或 major_id" });

    await pool.query(
      "INSERT IGNORE INTO major_access_control (user_id, major_id) VALUES (?, ?)",
      [user_id, major_id],
    );
    res.json({ message: "权限授予成功" });
  } catch (err) {
    console.error("授予权限失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// POST /admin/access-control/batch — 批量授予
router.post("/batch", async (req, res) => {
  try {
    const { user_id, major_ids } = req.body;
    if (!user_id || !Array.isArray(major_ids))
      return res.status(400).json({ error: "参数错误" });

    // 先清除该用户的所有权限，再重新授予
    await pool.query("DELETE FROM major_access_control WHERE user_id = ?", [
      user_id,
    ]);

    if (major_ids.length > 0) {
      const values = major_ids.map((mid) => [user_id, mid]);
      await pool.query(
        "INSERT INTO major_access_control (user_id, major_id) VALUES ?",
        [values],
      );
    }

    res.json({ message: "权限更新成功" });
  } catch (err) {
    console.error("批量授权失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

// DELETE /admin/access-control/:id — 撤销访问权限
router.delete("/:id", async (req, res) => {
  try {
    const [result] = await pool.query(
      "DELETE FROM major_access_control WHERE id = ?",
      [req.params.id],
    );
    if (result.affectedRows === 0)
      return res.status(404).json({ error: "记录不存在" });
    res.json({ message: "权限撤销成功" });
  } catch (err) {
    console.error("撤销权限失败:", err);
    res.status(500).json({ error: "服务器错误" });
  }
});

module.exports = router;
