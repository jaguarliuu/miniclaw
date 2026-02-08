/**
 * Download Adoptium Temurin JRE 24 for Windows x64.
 * Extracts to electron/resources/jre/
 *
 * Usage: node scripts/download-jre.js
 */

const https = require('https');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const JRE_VERSION = '24';
const OS = 'windows';
const ARCH = 'x64';
const IMAGE_TYPE = 'jre';

const RESOURCES_DIR = path.resolve(__dirname, '..', 'resources');
const JRE_DIR = path.join(RESOURCES_DIR, 'jre');
const TEMP_DIR = path.join(RESOURCES_DIR, '_jre_temp');

const API_URL =
  `https://api.adoptium.net/v3/assets/latest/${JRE_VERSION}/hotspot` +
  `?architecture=${ARCH}&image_type=${IMAGE_TYPE}&os=${OS}&vendor=eclipse`;

function httpsGet(url) {
  return new Promise((resolve, reject) => {
    const request = (url) => {
      https.get(url, { headers: { 'User-Agent': 'MiniClaw-Build' } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          // Follow redirect
          request(res.headers.location);
          return;
        }
        if (res.statusCode !== 200) {
          return reject(new Error(`HTTP ${res.statusCode} for ${url}`));
        }
        resolve(res);
      }).on('error', reject);
    };
    request(url);
  });
}

function downloadFile(url, destPath) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(destPath);
    const request = (url) => {
      https.get(url, { headers: { 'User-Agent': 'MiniClaw-Build' } }, (res) => {
        if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
          request(res.headers.location);
          return;
        }
        if (res.statusCode !== 200) {
          file.close();
          fs.unlinkSync(destPath);
          return reject(new Error(`HTTP ${res.statusCode}`));
        }

        const totalBytes = parseInt(res.headers['content-length'], 10);
        let downloaded = 0;

        res.on('data', (chunk) => {
          downloaded += chunk.length;
          if (totalBytes) {
            const pct = ((downloaded / totalBytes) * 100).toFixed(1);
            process.stdout.write(`\rDownloading... ${pct}% (${(downloaded / 1048576).toFixed(1)} MB)`);
          }
        });

        res.pipe(file);
        file.on('finish', () => {
          file.close();
          console.log('\nDownload complete.');
          resolve();
        });
      }).on('error', (err) => {
        file.close();
        fs.unlinkSync(destPath);
        reject(err);
      });
    };
    request(url);
  });
}

async function main() {
  console.log(`Fetching Adoptium JRE ${JRE_VERSION} metadata...`);

  // Get asset info from API
  const res = await httpsGet(API_URL);
  let body = '';
  for await (const chunk of res) {
    body += chunk;
  }

  const assets = JSON.parse(body);
  if (!assets || assets.length === 0) {
    throw new Error('No JRE assets found from Adoptium API');
  }

  // Find the zip package
  const asset = assets.find((a) => a.binary?.package?.name?.endsWith('.zip'));
  if (!asset) {
    throw new Error('No .zip JRE package found');
  }

  const downloadUrl = asset.binary.package.link;
  const fileName = asset.binary.package.name;
  console.log(`Found: ${fileName}`);

  // Prepare directories
  fs.mkdirSync(RESOURCES_DIR, { recursive: true });
  const zipPath = path.join(RESOURCES_DIR, fileName);

  // Download
  if (fs.existsSync(zipPath)) {
    console.log('Archive already downloaded, skipping download.');
  } else {
    await downloadFile(downloadUrl, zipPath);
  }

  // Extract
  console.log('Extracting...');
  if (fs.existsSync(TEMP_DIR)) {
    fs.rmSync(TEMP_DIR, { recursive: true });
  }
  fs.mkdirSync(TEMP_DIR, { recursive: true });

  // Use PowerShell to extract (Windows)
  execSync(
    `powershell -NoProfile -Command "Expand-Archive -Path '${zipPath}' -DestinationPath '${TEMP_DIR}' -Force"`,
    { stdio: 'inherit' }
  );

  // The extracted folder usually has a name like jdk-24.0.1+9-jre
  const extracted = fs.readdirSync(TEMP_DIR);
  const jreFolder = extracted.find((d) =>
    fs.statSync(path.join(TEMP_DIR, d)).isDirectory()
  );

  if (!jreFolder) {
    throw new Error('Could not find extracted JRE directory');
  }

  // Move to final location
  if (fs.existsSync(JRE_DIR)) {
    fs.rmSync(JRE_DIR, { recursive: true });
  }
  fs.renameSync(path.join(TEMP_DIR, jreFolder), JRE_DIR);

  // Cleanup
  fs.rmSync(TEMP_DIR, { recursive: true });
  fs.unlinkSync(zipPath);

  // Verify
  const javaExe = path.join(JRE_DIR, 'bin', 'java.exe');
  if (!fs.existsSync(javaExe)) {
    throw new Error('java.exe not found after extraction');
  }

  console.log(`\nJRE installed to: ${JRE_DIR}`);
  execSync(`"${javaExe}" -version`, { stdio: 'inherit' });
}

main().catch((err) => {
  console.error('Failed:', err.message);
  process.exit(1);
});
