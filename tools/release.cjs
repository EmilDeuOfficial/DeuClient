/**
 * Publishes the installer as a GitHub release (the launcher updates itself from it).
 * The release carries only the installer; the updater reads version and checksum from the GitHub API.
 *
 * Usage:  1. raise "version" in package.json
 *         2. npm run build      (creates release/DeuClient-Setup.exe)
 *         3. npm run release    (creates the release, e.g. v1.0)
 *
 * Needs the GitHub CLI (gh) to be logged in.
 */
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const { version } = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8'));
// package.json needs the long form (1.0.0), the release is called v1.0 (1.4.2 stays v1.4.2)
const tag = `v${version.replace(/^(\d+\.\d+)\.0$/, '$1')}`;
const installer = path.join(root, 'release', 'DeuClient-Setup.exe');
// written by electron-builder next to the installer: only used to catch an outdated build, never uploaded
const buildInfo = path.join(root, 'release', 'latest.yml');

for (const f of [installer, buildInfo]) {
  if (!fs.existsSync(f)) {
    console.error(`Missing ${path.relative(root, f)} - run "npm run build" first.`);
    process.exit(1);
  }
}
if (!fs.readFileSync(buildInfo, 'utf8').includes(`version: ${version}`)) {
  console.error(`The installer in release/ was not built for version ${version} - run "npm run build" again.`);
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
gh(['release', 'create', tag, installer, '--title', tag, '--notes', `DeuClient ${tag}`]);
console.log(`Published ${tag}. Installed launchers update themselves.`);
