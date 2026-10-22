const express = require("express");
const router = express.Router();
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const crypto = require("crypto");
const pool = require("../config/db");
const nodemailer = require("nodemailer");
const { verifyToken } = require("../middleware/authMiddleware");
const { verifyGeetest } = require("../services/geetestService");

const { access: SECRET_KEY, refresh: REFRESH_SECRET } = require('../config/security').jwtConfig();
const { verifyQqIdentity } = require('../services/qqIdentity');
const { rateLimit, sendError } = require('../services/securityLimits');
router.use((req, res, next) => ['/me', '/refresh', '/logout'].includes(req.path) ? next() : rateLimit('auth-ip', 30)(req, res, next));

const ACCESS_TOKEN_EXPIRY = "15m"; // 15 minutes
const REFRESH_TOKEN_EXPIRY_DAYS = 30;

// Helper: validate password strength
function isValidPassword(password) {
  // At least 8 characters, containing at least one letter and one number
  const regex = /^(?=.*[a-zA-Z])(?=.*\d).{8,}$/;
  return regex.test(password);
}

// Helper: generate tokens
function generateAccessToken(user) {
  return jwt.sign(
    { id: user.id, username: user.username, role: user.role },
    SECRET_KEY,
    { expiresIn: ACCESS_TOKEN_EXPIRY },
  );
}

function generateRefreshToken(user) {
  return jwt.sign({ id: user.id, type: "refresh" }, REFRESH_SECRET, {
    expiresIn: `${REFRESH_TOKEN_EXPIRY_DAYS}d`,
  });
}

// Helper: save refresh token to database
async function saveRefreshToken(userId, token) {
  const expiresAt = new Date(
    Date.now() + REFRESH_TOKEN_EXPIRY_DAYS * 24 * 60 * 60 * 1000,
  );
  // Ensure refresh_tokens table exists
  await pool.query(`
        CREATE TABLE IF NOT EXISTS refresh_tokens (
            id INT AUTO_INCREMENT PRIMARY KEY,
            user_id INT NOT NULL,
            token VARCHAR(512) NOT NULL,
            expires_at DATETIME NOT NULL,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_token (token(255)),
            INDEX idx_user_id (user_id),
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    `);
  await pool.query(
    "INSERT INTO refresh_tokens (user_id, token, expires_at) VALUES (?, ?, ?)",
    [userId, token, expiresAt],
  );
}

// Helper: send email
async function sendVerificationEmail(email, code) {
  const [settings] = await pool.query('SELECT setting_key, setting_value FROM settings WHERE setting_key IN ("smtp_host", "smtp_user", "smtp_pass")');
  const config = {};
  settings.forEach(row => {
    config[row.setting_key] = row.setting_value;
  });

  if (!config.smtp_host || !config.smtp_user || !config.smtp_pass) {
    throw new Error("SMTP configuration is missing. Please contact administrator.");
  }

  const transporter = nodemailer.createTransport({
    host: config.smtp_host,
    port: 465,
    secure: true,
    auth: {
      user: config.smtp_user,
      pass: config.smtp_pass,
    },
  });

  const mailOptions = {
    from: `"烁学" <${config.smtp_user}>`,
    to: email,
    subject: "【烁学】验证码",
    html: `
      <div style="font-family: Arial, sans-serif; background-color: #f4f5f7; padding: 40px 0;">
        <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
          <h2 style="color: #333333; margin-top: 0;">验证码</h2>
          <p style="color: #666666; font-size: 16px; line-height: 1.5;">您好，您的验证码为：</p>
          <div style="text-align: center; margin: 30px 0;">
            <span style="display: inline-block; padding: 15px 30px; background-color: #f0fdf4; color: #16a34a; font-size: 32px; font-weight: bold; border-radius: 8px; letter-spacing: 4px;">${code}</span>
          </div>
          <p style="color: #666666; font-size: 14px;">该验证码在 5 分钟内有效。如果这不是您的操作，请忽略此邮件。</p>
        </div>
      </div>
    `,
  };

  await transporter.sendMail(mailOptions);
}

// POST /send-code
router.post("/send-code", async (req, res) => {
  const { email, lot_number, captcha_output, pass_token, gen_time, client_type } = req.body;
  if (!email) return res.status(400).json({ message: "请提供邮箱" });

  const isValid = await verifyGeetest(lot_number, captcha_output, pass_token, gen_time, client_type || "web");
  if (!isValid) return res.status(400).json({ message: "行为验证失败" });

  try {
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    const expiresAt = new Date(Date.now() + 5 * 60 * 1000); // 5 mins

    // Invalidate existing codes for this email
    await pool.query("DELETE FROM verification_codes WHERE email = ?", [email]);

    // Save new code
    await pool.query(
      "INSERT INTO verification_codes (email, code, expires_at) VALUES (?, ?, ?)",
      [email, code, expiresAt]
    );

    await sendVerificationEmail(email, code);
    res.json({ message: "验证码已发送" });
  } catch (error) {
    console.error("Send code error:", error);
    res.status(500).json({ error: error.message || "Failed to send code" });
  }
});

// Register
router.post("/register", async (req, res) => {
  const { username, password, email, code, lot_number, captcha_output, pass_token, gen_time, client_type } = req.body;
  try {
    const isValid = await verifyGeetest(lot_number, captcha_output, pass_token, gen_time, client_type || "web");
    if (!isValid) return res.status(400).json({ message: "行为验证失败" });

    // Check if registration is open
    const [settings] = await pool.query(
      'SELECT setting_value FROM settings WHERE setting_key = "registration_open"',
    );
    if (settings.length > 0 && settings[0].setting_value === "false") {
      return res
        .status(403)
        .json({ message: "Registration is currently closed." });
    }

    if (!email || !code) {
      return res.status(400).json({ message: "请提供邮箱和验证码" });
    }

    if (!isValidPassword(password)) {
      return res
        .status(400)
        .json({ message: "密码长度至少8位，且必须包含字母和数字" });
    }

    // Verify code
    const [codeRows] = await pool.query(
      "SELECT * FROM verification_codes WHERE email = ? AND code = ? AND expires_at > NOW()",
      [email, code]
    );
    if (codeRows.length === 0) {
      return res.status(400).json({ message: "验证码无效或已过期" });
    }

    const hashedPassword = await bcrypt.hash(password, 10);
    let qqOpenId = req.body.qq_openid || null;

    try {
      await pool.query(
        "INSERT INTO users (username, password, qq_openid, email) VALUES (?, ?, ?, ?)",
        [username, hashedPassword, qqOpenId, email],
      );
      // Remove used code
      await pool.query("DELETE FROM verification_codes WHERE email = ?", [email]);
      res.status(201).json({ message: "User registered successfully" });
    } catch (insertError) {
      if (insertError.code === 'ER_DUP_ENTRY') {
        return res.status(400).json({ message: "用户名或邮箱已被使用" });
      }
      throw insertError;
    }
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Login — returns access token + refresh token
router.post("/login", async (req, res) => {
  const { username, password, lot_number, captcha_output, pass_token, gen_time, client_type } = req.body;

  const isValid = await verifyGeetest(lot_number, captcha_output, pass_token, gen_time, client_type || "web");
  if (!isValid) return res.status(400).json({ message: "行为验证失败" });

  try {
    const [rows] = await pool.query("SELECT * FROM users WHERE username = ?", [
      username,
    ]);
    if (rows.length === 0)
      return res.status(401).json({ message: "Invalid credentials" });

    const user = rows[0];
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch)
      return res.status(401).json({ message: "Invalid credentials" });

    const accessToken = generateAccessToken(user);
    const refreshToken = generateRefreshToken(user);

    // Save refresh token to database
    await saveRefreshToken(user.id, refreshToken);

    res.json({
      token: accessToken,
      refreshToken: refreshToken,
      user: {
        id: user.id,
        username: user.username,
        role: user.role,
        avatar_icon:
          user.avatar_icon ||
          "https://api.dicebear.com/9.x/fun-emoji/png?seed=Felix&size=120",
        nickname: user.nickname,
        qq_openid: user.qq_openid,
        email: user.email,
        subscription_end_date: user.subscription_end_date,
      },
    });
  } catch (error) {
    console.error("Login error:", error);
    res.status(500).json({ error: error.message });
  }
});

// Refresh — exchange refresh token for new access token
router.post("/refresh", async (req, res) => {
  const { refreshToken } = req.body;
  if (!refreshToken) {
    return res.status(400).json({ message: "Refresh token is required" });
  }

  try {
    // Verify the refresh token signature
    const decoded = jwt.verify(refreshToken, REFRESH_SECRET, { algorithms: ['HS256'] });
    if (decoded.type !== 'refresh' || !decoded.exp || !Number.isSafeInteger(Number(decoded.id)) || Number(decoded.id) <= 0) {
      return res.status(401).json({ message: 'Invalid refresh token' });
    }

    // Check if token exists in database and is not expired
    const [rows] = await pool.query(
      "SELECT * FROM refresh_tokens WHERE token = ? AND user_id = ? AND expires_at > NOW()",
      [refreshToken, decoded.id],
    );

    if (rows.length === 0) {
      return res
        .status(401)
        .json({ message: "Invalid or expired refresh token" });
    }

    // Get current user info (role may have changed)
    const [userRows] = await pool.query("SELECT * FROM users WHERE id = ?", [
      decoded.id,
    ]);
    if (userRows.length === 0) {
      return res.status(401).json({ message: "User not found" });
    }

    const user = userRows[0];
    const newAccessToken = generateAccessToken(user);

    res.json({
      token: newAccessToken,
      user: {
        id: user.id,
        username: user.username,
        role: user.role,
        avatar_icon:
          user.avatar_icon ||
          "https://api.dicebear.com/9.x/fun-emoji/png?seed=Felix&size=120",
        nickname: user.nickname,
        qq_openid: user.qq_openid,
        email: user.email,
        subscription_end_date: user.subscription_end_date,
      },
    });
  } catch (error) {
    if (
      error.name === "JsonWebTokenError" ||
      error.name === "TokenExpiredError"
    ) {
      return res
        .status(401)
        .json({ message: "Invalid or expired refresh token" });
    }
    console.error("Refresh token error:", error);
    res.status(500).json({ error: error.message });
  }
});

// Logout — revoke refresh token
router.post("/logout", async (req, res) => {
  const { refreshToken } = req.body;
  try {
    if (refreshToken) {
      await pool.query("DELETE FROM refresh_tokens WHERE token = ?", [
        refreshToken,
      ]);
    }
    res.json({ message: "Logged out successfully" });
  } catch (error) {
    console.error("Logout error:", error);
    res.status(500).json({ error: error.message });
  }
});

router.get('/me', verifyToken, async (req, res) => {
  try {
    const [rows] = await pool.query('SELECT id, username, role, avatar_icon, nickname, slogan, email, subscription_end_date FROM users WHERE id = ?', [req.userId]);
    res.set('Cache-Control', 'private, no-store');
    res.json({ user: rows[0] });
  } catch { res.status(503).json({ error: '暂时无法读取账号信息' }); }
});

// Change Password
router.post("/change-password", verifyToken, async (req, res) => {
  const userId = req.userId;
  const { oldPassword, newPassword } = req.body;
  try {
    const [rows] = await pool.query("SELECT password FROM users WHERE id = ?", [
      userId,
    ]);
    if (rows.length === 0)
      return res.status(404).json({ message: "用户不存在" });

    const user = rows[0];
    const isMatch = await bcrypt.compare(oldPassword, user.password);
    if (!isMatch) return res.status(400).json({ message: "原密码错误" });

    if (!isValidPassword(newPassword)) {
      return res
        .status(400)
        .json({ message: "新密码长度至少8位，且必须包含字母和数字" });
    }

    const hashedNewPassword = await bcrypt.hash(newPassword, 10);
    await pool.query("UPDATE users SET password = ? WHERE id = ?", [
      hashedNewPassword,
      userId,
    ]);

    // Revoke all refresh tokens for this user (force re-login)
    await pool.query("DELETE FROM refresh_tokens WHERE user_id = ?", [userId]);

    res.json({ message: "密码修改成功" });
  } catch (error) {
    console.error("Change password error:", error);
    res.status(500).json({ error: error.message });
  }
});

// Forgot Password
router.post("/forgot-password", async (req, res) => {
  const { email, code, newPassword, lot_number, captcha_output, pass_token, gen_time, client_type } = req.body;
  if (!email || !code || !newPassword) {
    return res.status(400).json({ message: "请填写完整信息" });
  }

  const isValid = await verifyGeetest(lot_number, captcha_output, pass_token, gen_time, client_type || "web");
  if (!isValid) return res.status(400).json({ message: "行为验证失败" });

  try {
    const [codeRows] = await pool.query(
      "SELECT * FROM verification_codes WHERE email = ? AND code = ? AND expires_at > NOW()",
      [email, code]
    );
    if (codeRows.length === 0) {
      return res.status(400).json({ message: "验证码无效或已过期" });
    }

    if (!isValidPassword(newPassword)) {
      return res.status(400).json({ message: "新密码长度至少8位，且必须包含字母和数字" });
    }

    const hashedNewPassword = await bcrypt.hash(newPassword, 10);

    const [updateResult] = await pool.query("UPDATE users SET password = ? WHERE email = ?", [
      hashedNewPassword,
      email,
    ]);

    if (updateResult.affectedRows === 0) {
      return res.status(404).json({ message: "未找到与该邮箱绑定的账号" });
    }

    // Revoke tokens
    await pool.query("DELETE FROM refresh_tokens WHERE user_id = (SELECT id FROM users WHERE email = ?)", [email]);
    await pool.query("DELETE FROM verification_codes WHERE email = ?", [email]);

    res.json({ message: "密码重置成功" });
  } catch (error) {
    console.error("Forgot password error:", error);
    res.status(500).json({ error: error.message });
  }
});

// Change Email
router.post("/change-email", verifyToken, async (req, res) => {
  const userId = req.userId;
  const { email, code } = req.body;
  if (!email || !code) {
    return res.status(400).json({ message: "请提供新邮箱和验证码" });
  }

  try {
    const [codeRows] = await pool.query(
      "SELECT * FROM verification_codes WHERE email = ? AND code = ? AND expires_at > NOW()",
      [email, code]
    );
    if (codeRows.length === 0) {
      return res.status(400).json({ message: "验证码无效或已过期" });
    }

    try {
      await pool.query("UPDATE users SET email = ? WHERE id = ?", [email, userId]);
      await pool.query("DELETE FROM verification_codes WHERE email = ?", [email]);

      const [updatedRows] = await pool.query(
        "SELECT id, username, role, avatar_icon, nickname, qq_openid, email, subscription_end_date FROM users WHERE id = ?",
        [userId]
      );

      res.json({ message: "邮箱绑定成功", user: updatedRows[0] });
    } catch (updateError) {
      if (updateError.code === 'ER_DUP_ENTRY') {
        return res.status(400).json({ message: "该邮箱已被其他账号绑定" });
      }
      throw updateError;
    }
  } catch (error) {
    console.error("Change email error:", error);
    res.status(500).json({ error: error.message });
  }
});

// QQ Login
router.post("/qq-login", async (req, res) => {
  const { openid, access_token } = req.body;
  if (!openid || !access_token) {
    return res.status(400).json({ message: "Missing openid or access_token" });
  }

  try {
    await verifyQqIdentity(openid, access_token);

    const [rows] = await pool.query("SELECT * FROM users WHERE qq_openid = ?", [
      openid,
    ]);
    if (rows.length === 0) {
      // Unbound, return special status
      return res.status(202).json({
        need_bind: true,
        bind_token: jwt.sign({ type: "qq-bind", openid }, SECRET_KEY, { expiresIn: "5m" }),
        message:
          "QQ account is not bound to any user. Please register or login to bind.",
      });
    }

    const user = rows[0];
    const accessToken = generateAccessToken(user);
    const refreshToken = generateRefreshToken(user);

    await saveRefreshToken(user.id, refreshToken);

    res.json({
      token: accessToken,
      refreshToken: refreshToken,
      user: {
        id: user.id,
        username: user.username,
        role: user.role,
        avatar_icon:
          user.avatar_icon ||
          "https://api.dicebear.com/9.x/fun-emoji/png?seed=Felix&size=120",
        nickname: user.nickname,
        qq_openid: user.qq_openid,
        email: user.email,
        subscription_end_date: user.subscription_end_date,
      },
    });
  } catch (error) {
    if (error.status) return sendError(res, error);
    console.error("QQ Login error:", error.message);
    res.status(500).json({ error: error.message });
  }
});

// Bind QQ (Authenticated or accompanied with credentials)
router.post("/bind-qq", async (req, res) => {
  const { openid, access_token, bind_token, username, password, nickname, avatar_icon } = req.body;

  try {
    if (bind_token) {
      const proof = jwt.verify(bind_token, SECRET_KEY, { algorithms: ['HS256'] });
      if (proof.type !== 'qq-bind' || proof.openid !== openid) throw Object.assign(new Error('QQ 绑定凭据无效'), { status: 401 });
    } else { await verifyQqIdentity(openid, access_token); }
  }
  catch (error) { return res.status(error.status || 401).json({ error: "QQ 授权已失效，请重新授权" }); }

  // Check if the request includes a valid JWT token via header (logged-in user)
  const authHeader = req.headers["authorization"];
  if (authHeader) {
    const token = authHeader.split(" ")[1];
    if (token) {
      try {
        const decoded = jwt.verify(token, SECRET_KEY, { algorithms: ['HS256'] });
        try {
          // Bind to current logged-in user
          await pool.query(
            "UPDATE users SET qq_openid = ?, nickname = ?, avatar_icon = ? WHERE id = ?",
            [openid, nickname, avatar_icon, decoded.id],
          );
        } catch (updateError) {
          if (updateError.code === 'ER_DUP_ENTRY') {
            return res.status(400).json({ message: "该 QQ 账号已被其他用户绑定" });
          }
          throw updateError;
        }

        const [updatedRows] = await pool.query(
          "SELECT id, username, role, avatar_icon, nickname, qq_openid, email, subscription_end_date FROM users WHERE id = ?",
          [decoded.id],
        );

        return res.json({
          message: "QQ bound successfully to current user.",
          user: {
            id: updatedRows[0].id,
            username: updatedRows[0].username,
            role: updatedRows[0].role,
            avatar_icon: updatedRows[0].avatar_icon,
            nickname: updatedRows[0].nickname,
            qq_openid: updatedRows[0].qq_openid,
            subscription_end_date: updatedRows[0].subscription_end_date,
          },
        });
      } catch (err) {
        // Ignore and fall back to username/password check if token is invalid
      }
    }
  }

  // Unauthenticated bind request (requires username/password)
  if (!username || !password || !openid) {
    return res
      .status(400)
      .json({ message: "Missing username, password, or openid" });
  }

  try {
    const [rows] = await pool.query("SELECT * FROM users WHERE username = ?", [
      username,
    ]);
    if (rows.length === 0)
      return res.status(401).json({ message: "Invalid credentials" });

    const user = rows[0];
    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch)
      return res.status(401).json({ message: "Invalid credentials" });

    try {
      // Bind QQ openid, nickname and avatar
      await pool.query(
        "UPDATE users SET qq_openid = ?, nickname = ?, avatar_icon = ? WHERE id = ?",
        [openid, nickname, avatar_icon, user.id],
      );
    } catch (updateError) {
      if (updateError.code === 'ER_DUP_ENTRY') {
        return res.status(400).json({ message: "该 QQ 账号已被其他用户绑定" });
      }
      throw updateError;
    }

    const [updatedRows] = await pool.query(
      "SELECT id, username, role, avatar_icon, nickname, qq_openid, email, subscription_end_date FROM users WHERE id = ?",
      [user.id],
    );
    const updatedUser = updatedRows[0];

    // Automatically log them in
    const accessToken = generateAccessToken(updatedUser);
    const refreshToken = generateRefreshToken(updatedUser);
    await saveRefreshToken(updatedUser.id, refreshToken);

    res.json({
      message: "QQ bound successfully.",
      token: accessToken,
      refreshToken: refreshToken,
      user: {
        id: updatedUser.id,
        username: updatedUser.username,
        role: updatedUser.role,
        avatar_icon: updatedUser.avatar_icon,
        nickname: updatedUser.nickname,
        qq_openid: updatedUser.qq_openid,
        subscription_end_date: updatedUser.subscription_end_date,
      },
    });
  } catch (error) {
    console.error("Bind QQ error:", error);
    res.status(500).json({ error: error.message });
  }
});

// Unbind QQ
router.post("/unbind-qq", verifyToken, async (req, res) => {
  const userId = req.userId;
  try {
    await pool.query(
      "UPDATE users SET qq_openid = NULL, nickname = NULL WHERE id = ?",
      [userId],
    );
    res.json({ message: "QQ account unbound successfully." });
  } catch (error) {
    console.error("Unbind QQ error:", error);
    res.status(500).json({ error: error.message });
  }
});

// Update Profile (Nickname and Slogan)
router.patch("/update-profile", verifyToken, async (req, res) => {
  const userId = req.userId;
  const { nickname, slogan } = req.body;

  try {
    // Basic validation
    if (nickname !== undefined && (nickname === "" || nickname.length > 50)) {
       return res.status(400).json({ error: "昵称长度必须在1-50个字符之间" });
    }
    if (slogan !== undefined && slogan.length > 255) {
       return res.status(400).json({ error: "个性签名不能超过255个字符" });
    }

    let query = "UPDATE users SET ";
    const params = [];
    const updates = [];

    if (nickname !== undefined) {
      updates.push("nickname = ?");
      params.push(nickname);
    }
    if (slogan !== undefined) {
      updates.push("slogan = ?");
      params.push(slogan);
    }

    if (updates.length === 0) {
      return res.status(400).json({ error: "没有提供需要更新的字段" });
    }

    query += updates.join(", ") + " WHERE id = ?";
    params.push(userId);

    await pool.query(query, params);

    // Fetch updated user to return
    const [updatedRows] = await pool.query(
      "SELECT id, username, nickname, slogan, email, role, avatar_icon, qq_openid, subscription_end_date FROM users WHERE id = ?",
      [userId]
    );

    res.json({
      message: "Profile updated successfully",
      user: updatedRows[0]
    });
  } catch (error) {
    console.error("Update profile error:", error);
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;
