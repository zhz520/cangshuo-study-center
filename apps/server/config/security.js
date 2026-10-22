// Secrets belong to the server environment, never to a Web bundle or APK.
function jwtConfig(env = process.env) {
  const access = env.JWT_SECRET || env.ACCESS_TOKEN_SECRET;
  const refresh = env.JWT_REFRESH_SECRET || env.REFRESH_TOKEN_SECRET;
  for (const [name, value] of [['JWT_SECRET', access], ['JWT_REFRESH_SECRET', refresh]]) {
    if (typeof value !== 'string' || Buffer.byteLength(value) < 32) {
      throw new Error(`${name} must contain at least 32 bytes of random secret material`);
    }
  }
  if (access === refresh) throw new Error('Access and refresh secrets must be different');
  return { access, refresh };
}

function encryptionKey(env = process.env) {
  const value = env.QUESTION_ENCRYPTION_KEY || '';
  if (!/^[A-Za-z0-9+/]{43}=$/.test(value)) {
    throw new Error('QUESTION_ENCRYPTION_KEY must be a base64 encoded 32-byte key');
  }
  const key = Buffer.from(value, 'base64');
  if (key.length !== 32) throw new Error('Invalid question encryption key');
  return key;
}

module.exports = { jwtConfig, encryptionKey };
