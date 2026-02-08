/**
 * MiniClaw Desktop Build Script
 *
 * Orchestrates the full build:
 * 1. mvn clean package -DskipTests
 * 2. npm run build (miniclaw-ui)
 * 3. Copy JAR → electron/resources/app.jar
 * 4. Copy dist/ → electron/resources/webapp/
 * 5. Check JRE exists
 * 6. npx electron-builder
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const ELECTRON_DIR = path.resolve(__dirname, '..');
const RESOURCES_DIR = path.join(ELECTRON_DIR, 'resources');
const UI_DIR = path.join(ROOT, 'miniclaw-ui');

function run(cmd, cwd = ROOT) {
  console.log(`\n> ${cmd}`);
  execSync(cmd, { cwd, stdio: 'inherit' });
}

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}

try {
  // Step 1: Build Spring Boot JAR
  console.log('=== Step 1: Building Spring Boot JAR ===');
  run('mvn clean package -DskipTests');

  // Find the JAR (exclude original)
  const targetDir = path.join(ROOT, 'target');
  const jars = fs.readdirSync(targetDir).filter(
    (f) => f.endsWith('.jar') && !f.endsWith('-plain.jar') && !f.includes('original')
  );
  if (jars.length === 0) {
    throw new Error('No JAR found in target/');
  }
  const jarFile = jars[0];
  console.log(`Found JAR: ${jarFile}`);

  // Step 2: Build Vue frontend
  console.log('\n=== Step 2: Building Vue frontend ===');
  run('npm install', UI_DIR);
  run('npm run build', UI_DIR);

  // Step 3: Copy JAR
  console.log('\n=== Step 3: Copying JAR ===');
  fs.mkdirSync(RESOURCES_DIR, { recursive: true });
  const srcJar = path.join(targetDir, jarFile);
  const destJar = path.join(RESOURCES_DIR, 'app.jar');
  fs.copyFileSync(srcJar, destJar);
  console.log(`Copied ${jarFile} → resources/app.jar`);

  // Step 4: Copy webapp
  console.log('\n=== Step 4: Copying webapp ===');
  const distDir = path.join(UI_DIR, 'dist');
  const webappDir = path.join(RESOURCES_DIR, 'webapp');
  if (fs.existsSync(webappDir)) {
    fs.rmSync(webappDir, { recursive: true });
  }
  copyDir(distDir, webappDir);
  console.log(`Copied miniclaw-ui/dist/ → resources/webapp/`);

  // Step 5: Check JRE
  console.log('\n=== Step 5: Checking JRE ===');
  const jreDir = path.join(RESOURCES_DIR, 'jre');
  const javaExe = path.join(jreDir, 'bin', 'java.exe');
  if (!fs.existsSync(javaExe)) {
    throw new Error(
      `JRE not found at ${jreDir}\nRun "npm run download-jre" first.`
    );
  }
  console.log('JRE found.');

  // Step 6: electron-builder
  console.log('\n=== Step 6: Building Electron installer ===');
  const publishArg = process.argv.includes('--publish') ? ' --publish always' : '';
  run(`npx electron-builder${publishArg}`, ELECTRON_DIR);

  console.log('\n=== Build complete! ===');
  console.log(`Installer is in: ${path.join(ELECTRON_DIR, 'release')}`);
} catch (err) {
  console.error('\nBuild failed:', err.message);
  process.exit(1);
}
