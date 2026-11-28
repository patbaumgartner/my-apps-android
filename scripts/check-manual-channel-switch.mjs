#!/usr/bin/env node
import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (path) => readFileSync(join(root, path), 'utf8');

function kotlinFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return kotlinFiles(path);
    return entry.isFile() && entry.name.endsWith('.kt') ? [path] : [];
  });
}

const mainRoot = join(root, 'app/src/main');
const productionSources = kotlinFiles(join(mainRoot, 'kotlin'))
  .map((path) => readFileSync(path, 'utf8'))
  .join('\n');
const manifest = read('app/src/main/AndroidManifest.xml');
const forbidden = [
  /PackageInstaller\.uninstall/,
  /\.uninstall\s*\(/,
  /UninstallResultReceiver/,
  /AppStatus\.UNINSTALLING/,
];
for (const pattern of forbidden) {
  assert.doesNotMatch(productionSources, pattern, `automatic uninstall path remains: ${pattern}`);
}
assert.doesNotMatch(manifest, /REQUEST_DELETE_PACKAGES|UNINSTALL_RESULT|UninstallResultReceiver/);
assert.ok(!existsSync(join(mainRoot, 'kotlin/com/patbaumgartner/android/myapps/data/install/UninstallResultReceiver.kt')));

const viewModel = read('app/src/main/kotlin/com/patbaumgartner/android/myapps/ui/MyAppsViewModel.kt');
const updates = read('app/src/main/kotlin/com/patbaumgartner/android/myapps/ui/screens/UpdatesScreen.kt');
const details = read('app/src/main/kotlin/com/patbaumgartner/android/myapps/ui/screens/AppDetailsScreen.kt');
const decision = read('app/src/main/kotlin/com/patbaumgartner/android/myapps/domain/model/UpdateDecision.kt');
assert.match(viewModel, /if \(state\.status == AppStatus\.MANUAL_ACTION_REQUIRED\) return/);
assert.match(viewModel, /PipelineResult\.ManualActionRequired/);
assert.match(updates, /status == AppStatus\.MANUAL_ACTION_REQUIRED/);
assert.match(details, /status == AppStatus\.MANUAL_ACTION_REQUIRED/);
assert.match(decision, /Install\.MANUAL_ACTION_REQUIRED/);

const strings = read('app/src/main/res/values/strings.xml');
assert.match(strings, /name="status_manual_action">Manual action only</);
const warning = strings.match(/name="update_reinstall_hint">([^<]+)</)?.[1] ?? '';
for (const required of ['Export or back up', 'erases app data', 'decks', 'presets', 'credentials', 'settings', 'SHA256SUMS']) {
  assert.ok(warning.includes(required), `manual-action warning must mention ${required}`);
}

console.log('MANUAL_CHANNEL_SWITCH_STATIC_PASS');
