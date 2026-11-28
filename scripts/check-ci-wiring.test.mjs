import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const fixtureFiles = [
  '.github/workflows/ci.yml',
  '.github/workflows/security.yml',
  '.github/workflows/release.yml',
  '.github/workflows/dependabot-auto-merge.yml',
  'app/build.gradle.kts',
  'gradle/libs.versions.toml',
  'gradle/wrapper/gradle-wrapper.properties',
  'README.md',
  'CONTRIBUTING.md',
  'scripts/check-ci-wiring.mjs',
];

function withFixture(run) {
  mkdirSync(join(root, 'build'), { recursive: true });
  const directory = mkdtempSync(join(root, 'build', 'check-ci-wiring-'));
  try {
    for (const path of fixtureFiles) {
      const destination = join(directory, path);
      mkdirSync(dirname(destination), { recursive: true });
      cpSync(join(root, path), destination);
    }
    run(directory);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

function check(directory) {
  return spawnSync(process.execPath, [join(directory, 'scripts/check-ci-wiring.mjs')], {
    cwd: directory,
    encoding: 'utf8',
    timeout: 10_000,
  });
}

function replace(fixture, path, pattern, replacement) {
  const filename = join(fixture, path);
  const original = readFileSync(filename, 'utf8');
  const updated = original.replace(pattern, replacement);
  assert.notEqual(updated, original, `fixture mutation did not apply: ${path}`);
  writeFileSync(filename, updated);
}

test('version-only catalog and wrapper updates pass the structural checker', () => {
  withFixture((fixture) => {
    replace(fixture, 'gradle/libs.versions.toml', /agp = "[^"]+"/, 'agp = "9.6.0"');
    replace(fixture, 'gradle/libs.versions.toml', /kotlin = "[^"]+"/, 'kotlin = "2.4.22"');
    replace(fixture, 'gradle/libs.versions.toml', /composeBom = "[^"]+"/, 'composeBom = "2026.12.00"');
    replace(fixture, 'gradle/wrapper/gradle-wrapper.properties', /gradle-9\.9\.2-bin\.zip/, 'gradle-9.9.3-bin.zip');
    replace(
      fixture,
      'gradle/wrapper/gradle-wrapper.properties',
      /distributionSha256Sum=[0-9a-f]{64}/,
      `distributionSha256Sum=${'a'.repeat(64)}`,
    );
    const result = check(fixture);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /CI_WIRING_PASS/);
  });
});

test('compileSdkMinor remains enforced', () => {
  withFixture((fixture) => {
    replace(fixture, 'app/build.gradle.kts', /compileSdkMinor = 1/, 'compileSdkMinor = 0');
    const result = check(fixture);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /compileSdkMinor/);
  });
});

test('workflow SDK installation remains enforced', () => {
  withFixture((fixture) => {
    replace(fixture, '.github/workflows/ci.yml', /platforms;android-37\.1/, 'platforms;android-37.0');
    const result = check(fixture);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /must install Android SDK 37\.1/);
  });
});

test('wrapper checksum presence remains enforced', () => {
  withFixture((fixture) => {
    replace(fixture, 'gradle/wrapper/gradle-wrapper.properties', /distributionSha256Sum=[0-9a-f]{64}/, 'distributionSha256Sum=');
    const result = check(fixture);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /distributionSha256Sum/);
  });
});

after(() => console.log('CI_WIRING_TEST_PASS'));
