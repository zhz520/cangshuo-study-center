const FREE_LIMIT = 20;
const PAGE_LIMIT = 50;

function pageOptions(query = {}) {
  const page = Number(query.page ?? 1);
  const requested = Number(query.limit ?? PAGE_LIMIT);
  if (!Number.isSafeInteger(page) || page < 1 || page > 100000 || !Number.isSafeInteger(requested) || requested < 1) {
    const error = new Error('分页参数无效');
    error.status = 400;
    throw error;
  }
  const limit = Math.min(requested, PAGE_LIMIT);
  return { page, limit, offset: (page - 1) * limit };
}

function questionFilter(req, alias = 'q') {
  if (!/^[a-z]+$/i.test(alias)) throw new Error('Invalid SQL alias');
  if (!req.account) throw new Error('Question access requires an authenticated account');
  let sql = `${alias}.is_active = 1`;
  const params = [];
  if (req.account.role !== 'admin') {
    sql += ` AND (NOT EXISTS (SELECT 1 FROM major_access_control acl WHERE acl.user_id = ?)
      OR EXISTS (SELECT 1 FROM major_access_control acl JOIN subjects acl_subject ON acl_subject.major_id = acl.major_id
        WHERE acl.user_id = ? AND acl_subject.id = ${alias}.subject_id))`;
    params.push(req.userId, req.userId);
    if (!req.account.has_subscription) {
      // The preview is anchored to the first active IDs of the subject, before type/search filtering.
      sql += ` AND ${alias}.id IN (SELECT preview.id FROM
        (SELECT id, ROW_NUMBER() OVER (PARTITION BY subject_id ORDER BY id) AS preview_rank
          FROM questions WHERE is_active = 1) preview WHERE preview.preview_rank <= ${FREE_LIMIT})`;
    }
  }
  return { sql, params };
}

async function assertQuestion(pool, req, id) {
  if (!Number.isSafeInteger(Number(id)) || Number(id) <= 0) {
    const error = new Error('题目编号无效'); error.status = 400; throw error;
  }
  const filter = questionFilter(req);
  const [rows] = await pool.query(`SELECT q.id FROM questions q WHERE q.id = ? AND ${filter.sql}`, [id, ...filter.params]);
  if (!rows.length) {
    const error = new Error('题目不可访问，请检查题库权限或会员状态');
    error.status = 403; error.code = 'QUESTION_ACCESS_DENIED'; throw error;
  }
}

function questionHeaders(req, res) {
  res.set('Cache-Control', 'private, no-store, max-age=0');
  res.set('Pragma', 'no-cache');
  res.set('X-Question-Access', req.account.role === 'admin' || req.account.has_subscription ? 'full' : 'preview');
  res.set('X-Question-Page-Limit', String(PAGE_LIMIT));
  res.set('X-Question-Preview-Limit', String(FREE_LIMIT));
}

module.exports = { FREE_LIMIT, PAGE_LIMIT, pageOptions, questionFilter, assertQuestion, questionHeaders };
