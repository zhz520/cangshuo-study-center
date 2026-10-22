const JPush = require('jpush-sdk');
const path = require('path');
const fs = require('fs');

let client = null;

try {
  const configPath = path.join(__dirname, '../config/jpush-config.json');
  if (fs.existsSync(configPath)) {
    const config = require(configPath);
    if (config.appKey && config.masterSecret) {
      client = JPush.buildClient(config.appKey, config.masterSecret);
      console.log('JPush Client initialized successfully.');
    } else {
      console.warn('JPush config is missing appKey or masterSecret.');
    }
  } else {
    // Ensure config directory exists
    const configDir = path.join(__dirname, '../config');
    if (!fs.existsSync(configDir)) {
      fs.mkdirSync(configDir, { recursive: true });
    }
    // Create template
    fs.writeFileSync(configPath, JSON.stringify({ appKey: "YOUR_APP_KEY", masterSecret: "YOUR_MASTER_SECRET" }, null, 2));
    console.warn('JPush config file not found. Created template at:', configPath);
  }
} catch (error) {
  console.error('Error initializing JPush:', error);
}

const sendPushNotification = async (registrationId, title, body, extras = {}) => {
  if (!client || !registrationId) {
    console.log('JPush notification skipped: Client not initialized or no registrationId.');
    return;
  }

  try {
    // Process extras to ensure all values are strings (JPush requirement)
    const formattedExtras = Object.keys(extras).reduce((acc, key) => {
      acc[key] = String(extras[key]);
      return acc;
    }, {});

    client.push().setPlatform(JPush.ALL)
      .setAudience(JPush.registration_id(registrationId))
      .setNotification(
        JPush.android(body, title, 1, formattedExtras),
        JPush.ios(body, 'default', 1, false, formattedExtras)
      )
      .send(function(err, res) {
        if (err) {
          console.error('JPush push error:', err.message);
        } else {
          console.log('JPush push success:', res);
        }
      });
  } catch (error) {
    console.error('Error calling JPush API:', error);
  }
};

module.exports = {
  sendPushNotification
};
