const crypto = require("crypto");
const axios = require("axios");

const CONFIGS = {
  web: {
    id: process.env.GEETEST_WEB_ID,
    key: process.env.GEETEST_WEB_KEY,
  },
  android: {
    id: process.env.GEETEST_ANDROID_ID,
    key: process.env.GEETEST_ANDROID_KEY,
  },
};

const GEETEST_URL = "https://gcaptcha4.geetest.com/validate";

/**
 * 极验 4.0 服务端校验
 * @param {string} lot_number
 * @param {string} captcha_output
 * @param {string} pass_token
 * @param {string} gen_time
 * @param {string} client_type - 'web' | 'android'
 * @returns {Promise<boolean>} 是否验证成功
 */
async function verifyGeetest(lot_number, captcha_output, pass_token, gen_time, client_type) {
  if (!lot_number || !captcha_output || !pass_token || !gen_time || !client_type) {
    return false;
  }

  const config = CONFIGS[client_type];
  if (!config || !config.id || !config.key) {
    console.error("Unknown geetest client_type:", client_type);
    return false;
  }

  const sign_token = crypto
    .createHmac("sha256", config.key)
    .update(lot_number, "utf8")
    .digest("hex");

  try {
    const params = new URLSearchParams();
    params.append("lot_number", lot_number);
    params.append("captcha_output", captcha_output);
    params.append("pass_token", pass_token);
    params.append("gen_time", gen_time);
    params.append("sign_token", sign_token);
    params.append("captcha_id", config.id);

    const response = await axios.post(GEETEST_URL, params.toString(), {
      timeout: 8000,
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
    });

    return response.data && response.data.result === "success";
  } catch (error) {
    console.error("Geetest verification error:", error.message);
    return false;
  }
}

module.exports = {
  verifyGeetest,
};
