/**
 * Publishes the installer as a GitHub release (the launcher updates itself from it).
 *
 * Usage:  1. raise "version" in package.json
 *         2. npm run build      (creates release/DeuClient-Setup.exe and release/latest.yml)
 *         3. npm run release    (creates the release, e.g. v1.0, with the three files)
 *
 * Needs the GitHub CLI (gh) to be logged in.
 */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const { version } = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
// package.json needs the long form (1.0.0), the release is called v1.0 (1.4.2 stays v1.4.2)
const tag = `v${version.replace(/^(d+.d+).0$/, '$1')}`;
const files = ['DeuClient-Setup.exe', 'DeuClient-Setup.exe.blockmap', 'latest.yml'].map((f) => path.join(root, 'release', f));

for (const f of files) {
  if (!fs.existsSync(f)) {
    console.error(`Missing ${path.relative(root, f)} - run "npm run build" first.`);
    process.exit(1);
  }
}
const latest = fs.readFileSync(files[2], 'utf8');
if (!latest.includes(`version: ${version}`)) {
  console.error(`release/latest.yml does not contain version ${version} - run "npm run build" again.`);
  process.exit(1);
}

const gh = (args) => execFileSync('gh', args, { cwd: root, stdio: 'inherit' });
try {
  execFileSync('gh', ['release', 'view', tag], { cwd: root, stdio: 'ignore' });
  console.error(`Release ${tag} already exists. Raise the version in package.json first.`);
  process.exit(1);
} catch {
  // release does not exist yet: good
}
gh(['release', 'create', tag, ...files, '--title', `DeuClient ${tag}`, '--notes', `DeuClient ${tag}`]);
console.log(`Published ${tag}. Installed launchers update themselves.`);
