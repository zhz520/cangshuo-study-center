const nodemailer = require('nodemailer');
const pool = require('../config/db');

/**
 * Get SMTP settings and create a transporter
 */
async function getTransporter() {
    const [settings] = await pool.query('SELECT setting_key, setting_value FROM settings WHERE setting_key IN ("smtp_host", "smtp_user", "smtp_pass")');
    const config = {};
    settings.forEach(row => {
        config[row.setting_key] = row.setting_value;
    });

    if (!config.smtp_host || !config.smtp_user || !config.smtp_pass) {
        throw new Error("SMTP configuration is missing. Please contact administrator.");
    }

    return nodemailer.createTransport({
        host: config.smtp_host,
        port: 465,
        secure: true,
        auth: {
            user: config.smtp_user,
            pass: config.smtp_pass,
        },
    });
}

/**
 * Send a generic email
 */
async function sendEmail(to, subject, html) {
    if (!to) return;
    try {
        const transporter = await getTransporter();
        const [settings] = await pool.query('SELECT setting_value FROM settings WHERE setting_key = "smtp_user"');
        const fromEmail = settings[0]?.setting_value || "烁学";
        
        const mailOptions = {
            from: `"烁学" <${fromEmail}>`,
            to,
            subject,
            html,
        };

        await transporter.sendMail(mailOptions);
    } catch (error) {
        console.error('Email sending error:', error);
    }
}

/**
 * Notify user about report status change
 */
async function sendReportStatusEmail(userEmail, reportStatus, questionContent) {
    const subject = `【烁学】题目报错处理通知`;
    const statusText = reportStatus === 'resolved' ? '已修正' : '已驳回';
    const html = `
        <div style="font-family: Arial, sans-serif; background-color: #f4f5f7; padding: 40px 0;">
            <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                <h2 style="color: #333333; margin-top: 0;">报错处理通知</h2>
                <p style="color: #666666; font-size: 16px; line-height: 1.5;">您好，您提交的题目报错工作已有新进展：</p>
                <div style="background-color: #f8fafc; padding: 20px; border-radius: 8px; margin: 20px 0;">
                    <p><strong>题目内容：</strong>${questionContent}</p>
                    <p><strong>处理结果：</strong><span style="color: ${reportStatus === 'resolved' ? '#16a34a' : '#dc2626'}; font-weight: bold;">${statusText}</span></p>
                </div>
                <p style="color: #666666; font-size: 14px;">感谢您对题库建设的支持！</p>
            </div>
        </div>
    `;
    await sendEmail(userEmail, subject, html);
}

/**
 * Notify author about article review status
 */
async function sendArticleStatusEmail(userEmail, articleTitle, status) {
    const isPublished = status == 1;
    const subject = `【烁学】文章审核通知`;
    const statusText = isPublished ? '已通过并发布' : '未通过';
    const html = `
        <div style="font-family: Arial, sans-serif; background-color: #f4f5f7; padding: 40px 0;">
            <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                <h2 style="color: #333333; margin-top: 0;">文章审核通知</h2>
                <p style="color: #666666; font-size: 16px; line-height: 1.5;">您好，您的投稿文章审核状态已更新：</p>
                <div style="background-color: #f8fafc; padding: 20px; border-radius: 8px; margin: 20px 0;">
                    <p><strong>文章标题：</strong>《${articleTitle}》</p>
                    <p><strong>审核结果：</strong><span style="color: ${isPublished ? '#16a34a' : '#dc2626'}; font-weight: bold;">${statusText}</span></p>
                </div>
                <p style="color: #666666; font-size: 14px;">${isPublished ? '您的文章已对所有用户可见。' : '请检查文章内容是否符合规范后重新提交。'}</p>
            </div>
        </div>
    `;
    await sendEmail(userEmail, subject, html);
}

/**
 * Notify author about new comment
 */
async function sendCommentNotificationEmail(authorEmail, articleTitle, commenterName) {
    const subject = `【烁学】您的文章有新评论`;
    const html = `
        <div style="font-family: Arial, sans-serif; background-color: #f4f5f7; padding: 40px 0;">
            <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                <h2 style="color: #333333; margin-top: 0;">新评论通知</h2>
                <p style="color: #666666; font-size: 16px; line-height: 1.5;">您好，您的文章收到了新的评论：</p>
                <div style="background-color: #f8fafc; padding: 20px; border-radius: 8px; margin: 20px 0;">
                    <p><strong>文章：</strong>《${articleTitle}》</p>
                    <p><strong>评论者：</strong>${commenterName}</p>
                </div>
                <p style="color: #666666; font-size: 14px;">您可以登录 App 查看详细内容并回复。</p>
            </div>
        </div>
    `;
    await sendEmail(authorEmail, subject, html);
}

/**
 * Notify user about comment audit status
 */
async function sendCommentAuditEmail(userEmail, articleTitle, status, commentContent) {
    const isApproved = status === 'approved';
    const subject = `【烁学】评论审核通知`;
    const statusText = isApproved ? '已通过' : '未通过';
    const html = `
        <div style="font-family: Arial, sans-serif; background-color: #f4f5f7; padding: 40px 0;">
            <div style="max-width: 600px; margin: 0 auto; background-color: #ffffff; padding: 40px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                <h2 style="color: #333333; margin-top: 0;">评论审核通知</h2>
                <p style="color: #666666; font-size: 16px; line-height: 1.5;">您好，您的评论审核状态已更新：</p>
                <div style="background-color: #f8fafc; padding: 20px; border-radius: 8px; margin: 20px 0;">
                    <p><strong>文章：</strong>《${articleTitle}》</p>
                    <p><strong>评论内容：</strong>${commentContent}</p>
                    <p><strong>审核结果：</strong><span style="color: ${isApproved ? '#16a34a' : '#dc2626'}; font-weight: bold;">${statusText}</span></p>
                </div>
                <p style="color: #666666; font-size: 14px;">${isApproved ? '您的评论已对所有用户可见。' : '请确保您的评论内容符合社区规范。'}</p>
            </div>
        </div>
    `;
    await sendEmail(userEmail, subject, html);
}

module.exports = {
    sendEmail,
    sendReportStatusEmail,
    sendArticleStatusEmail,
    sendCommentNotificationEmail,
    sendCommentAuditEmail
};
