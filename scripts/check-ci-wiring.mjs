#!/usr/bin/env node
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (path) => readFileSync(join(root, path), 'utf8');

function job(workflow, name) {
  const lines = workflow.split('\n');
  const start = lines.indexOf(`  ${name}:`);
  assert.notEqual(start, -1, `missing job ${name}`);
  const nextJob = lines.findIndex((line, index) => index > start && /^  [A-Za-z0-9_-]+:/.test(line));
  return lines.slice(start, nextJob === -1 ? lines.length : nextJob).join('\n');
}

const sdkExecutable = '"${ANDROID_SDK_ROOT:-$ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"';
const sdkPackage = '"platforms;android-37.1"';
const ci = read('.github/workflows/ci.yml');
const security = read('.github/workflows/security.yml');
const release = read('.github/workflows/release.yml');
const autoMerge = read('.github/workflows/dependabot-auto-merge.yml');
const buildGradle = read('app/build.gradle.kts');
const wrapper = read('gradle/wrapper/gradle-wrapper.properties');

for (const [workflow, name] of [
  [ci, 'build'],
  [ci, 'instrumented'],
  [security, 'codeql'],
  [release, 'snapshot'],
  [release, 'release'],
]) {
  assert.ok(job(workflow, name).includes(sdkExecutable), `${name} must use the installed sdkmanager path`);
  assert.ok(job(workflow, name).includes(sdkPackage), `${name} must install Android SDK 37.1`);
}

const build = job(ci, 'build');
const instrumented = job(ci, 'instrumented');
const verify = job(ci, 'verify');
assert.match(build, /^ {4}name: Build and unit tests$/m);
assert.match(build, /testDebugUnitTest lintDebug assembleDebug assembleRelease/);
assert.match(build, /node scripts\/check-ci-wiring\.mjs/);
assert.match(build, /node --test scripts\/check-ci-wiring\.test\.mjs/);
assert.match(build, /node scripts\/check-manual-channel-switch\.mjs/);
assert.match(build, /node --test scripts\/auto-merge-preflight\.test\.mjs/);
assert.match(instrumented, /connectedDebugAndroidTest/);
assert.match(instrumented, /connected-build-reports-\$\{\{ github\.run_attempt \}\}/);
assert.match(verify, /^ {4}name: Test, lint, and package$/m);
assert.match(verify, /^ {4}if: \$\{\{ always\(\) \}\}$/m);
assert.match(verify, /^ {4}needs: \[build, instrumented\]$/m);
assert.match(verify, /^ {4}timeout-minutes: 5$/m);
assert.match(verify, /test "\$\{\{ needs\.build\.result \}\}" = "success"/);
assert.match(verify, /test "\$\{\{ needs\.instrumented\.result \}\}" = "success"/);

const preflightIndex = autoMerge.indexOf('- name: Verify main requires the aggregate CI check');
const mergeIndex = autoMerge.indexOf('gh pr merge --auto --rebase');
const tokenGuardIndex = autoMerge.indexOf('if [ -z "$GH_TOKEN" ]');
assert.ok(preflightIndex >= 0 && mergeIndex > preflightIndex, 'auto-merge preflight must run before gh pr merge');
assert.ok(tokenGuardIndex > preflightIndex && tokenGuardIndex < mergeIndex, 'merge token must be nonempty');
assert.ok(!autoMerge.includes('actions/checkout@'), 'pull_request_target workflow must not check out PR code');
assert.ok(autoMerge.includes('rules/branches/main?per_page=100'), 'preflight must read main branch-rules metadata');
assert.ok(!autoMerge.includes('/branches/main/protection'), 'classic protection requires unavailable Administration access');
assert.ok(!autoMerge.includes('/rulesets'), 'detailed ruleset APIs require unavailable Administration access');
assert.ok(autoMerge.includes('--paginate --slurp'), 'branch-rules enumeration must include every page');
assert.ok(autoMerge.includes('.parameters.required_status_checks[]?'), 'preflight must inspect required check contexts');
assert.ok(!autoMerge.includes("update-type != 'version-update:semver-major'"), 'unknown update types must not merge');
assert.equal(autoMerge.match(/update-type == 'version-update:semver-patch'/g)?.length, 2);
assert.equal(autoMerge.match(/update-type == 'version-update:semver-minor'/g)?.length, 2);
assert.equal(autoMerge.split('github-token: ${{ secrets.GITHUB_TOKEN }}').length - 1, 1);
assert.equal(autoMerge.split('GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}').length - 1, 1);
assert.equal(autoMerge.split('GH_TOKEN: ${{ secrets.AUTO_MERGE_TOKEN }}').length - 1, 1);
assert.ok(autoMerge.includes('AUTO_MERGE_TOKEN is empty or not configured'));

assert.match(buildGradle, /^ {4}compileSdk = 37$/m);
assert.match(buildGradle, /^ {4}compileSdkMinor = 1$/m);
assert.match(buildGradle, /^ {8}minSdk = 26$/m);
assert.match(buildGradle, /^ {8}targetSdk = 37$/m);
assert.match(buildGradle, /sourceCompatibility = JavaVersion\.VERSION_17/);
assert.match(buildGradle, /targetCompatibility = JavaVersion\.VERSION_17/);
assert.match(wrapper, /^distributionSha256Sum=[0-9a-f]{64}$/m);

assert.match(release, /:app:assembleRelease :app:bundleRelease/);
assert.match(release, /cp app\/build\/outputs\/apk\/release\/app-release\.apk dist\/my-apps-snapshot\.apk/);
assert.match(release, /sha256sum my-apps-snapshot\.apk > SHA256SUMS/);
assert.match(release, /cp app\/build\/outputs\/bundle\/release\/app-release\.aab "dist\/my-apps-\$v\.aab"/);
assert.match(release, /sha256sum my-apps-"\$v"\.apk my-apps-"\$v"\.aab > SHA256SUMS/);

const readme = read('README.md');
const contributing = read('CONTRIBUTING.md');
assert.ok(readme.includes('platform 37.1 (`compileSdkMinor = 1`)'));
assert.ok(contributing.includes('must configure the active main ruleset'));
assert.ok(contributing.includes('Classic branch protection alone is not visible'));
assert.ok(contributing.includes('**Contents: write**, **Pull requests: write**'));
assert.ok(contributing.includes('**Workflows: write**'));
assert.ok(contributing.includes('trigger CI and snapshot workflows'));
assert.ok(contributing.includes('signed universal APK + AAB'));

console.log('CI_WIRING_PASS');
