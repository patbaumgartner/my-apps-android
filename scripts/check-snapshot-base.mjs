#!/usr/bin/env node
// Keeps the snapshot versionCode base in sync between the app and the release workflow:
//
//   app/src/main/kotlin/.../domain/model/UpdateDecision.kt
//     const val SNAPSHOT_VERSION_CODE_BASE = 2_000_000_000L
//   .github/workflows/release.yml
//     -PversionCode=$((2000000000 + GITHUB_RUN_NUMBER))
//
// UpdateDecision treats every versionCode >= SNAPSHOT_VERSION_CODE_BASE as a snapshot
// build, which is what makes the Snapshot → Stable channel switch detectable. If one
// side changes without the other, that switch silently breaks, so CI fails on mismatch.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const decisionPath = join(
  root,
  'app/src/main/kotlin/com/patbaumgartner/android/myapps/domain/model/UpdateDecision.kt',
);
const workflowPath = join(root, '.github/workflows/release.yml');

const decision = readFileSync(decisionPath, 'utf8');
const workflow = readFileSync(workflowPath, 'utf8');

const ktMatch = decision.match(/SNAPSHOT_VERSION_CODE_BASE\s*=\s*([\d_]+)L/);
const ymlMatch = workflow.match(/-PversionCode=\$\(\((\d+)\s*\+\s*GITHUB_RUN_NUMBER\)\)/);

if (!ktMatch) {
  console.error(`check-snapshot-base: no "SNAPSHOT_VERSION_CODE_BASE = <n>L" found in ${decisionPath}`);
  process.exit(1);
}
if (!ymlMatch) {
  console.error(`check-snapshot-base: no "-PversionCode=$((<n> + GITHUB_RUN_NUMBER))" found in ${workflowPath}`);
  process.exit(1);
}

const fromKotlin = Number(ktMatch[1].replaceAll('_', ''));
const fromWorkflow = Number(ymlMatch[1]);

if (fromKotlin !== fromWorkflow) {
  console.error(
    `check-snapshot-base: MISMATCH — UpdateDecision.kt SNAPSHOT_VERSION_CODE_BASE=${fromKotlin}` +
      ` but release.yml builds snapshots with $(( ${fromWorkflow} + GITHUB_RUN_NUMBER ))`,
  );
  console.error('Update both in the same commit; the Snapshot → Stable channel switch depends on this.');
  process.exit(1);
}

console.log(`check-snapshot-base: OK — snapshot base ${fromKotlin} matches in UpdateDecision.kt and release.yml`);
