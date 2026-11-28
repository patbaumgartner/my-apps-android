import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const workflow = readFileSync(join(root, '.github/workflows/dependabot-auto-merge.yml'), 'utf8');
const requiredContext = 'Test, lint, and package';

function preflightScript() {
  const lines = workflow.split('\n');
  const step = lines.indexOf('      - name: Verify main requires the aggregate CI check');
  assert.notEqual(step, -1, 'preflight step is missing');
  const run = lines.findIndex((line, index) => index > step && line === '        run: |');
  assert.notEqual(run, -1, 'preflight run block is missing');
  const script = [];
  for (let index = run + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (line && !line.startsWith('          ')) break;
    script.push(line ? line.slice(10) : '');
  }
  return script.join('\n');
}

const script = preflightScript();

function mergeScript() {
  const lines = workflow.split('\n');
  const step = lines.indexOf('      - name: Enable auto-merge for patch and minor updates');
  assert.notEqual(step, -1, 'merge step is missing');
  const run = lines.findIndex((line, index) => index > step && line === '        run: |');
  assert.notEqual(run, -1, 'merge run block is missing');
  const scriptLines = [];
  for (let index = run + 1; index < lines.length; index += 1) {
    const line = lines[index];
    if (line && !line.startsWith('          ')) break;
    scriptLines.push(line ? line.slice(10) : '');
  }
  return scriptLines.join('\n');
}

const mergeCommand = mergeScript();

function stepCondition(stepName) {
  const lines = workflow.split('\n');
  const step = lines.indexOf(`      - name: ${stepName}`);
  assert.notEqual(step, -1, `${stepName} is missing`);
  const nextStep = lines.findIndex((line, index) => index > step && line.startsWith('      - name:'));
  const end = nextStep === -1 ? lines.length : nextStep;
  const condition = lines.findIndex((line, index) => index > step && index < end && line === '        if: >-');
  assert.notEqual(condition, -1, `${stepName} needs an explicit update-type condition`);
  const clauses = [];
  for (let index = condition + 1; index < end && lines[index].startsWith('          '); index += 1) {
    clauses.push(lines[index].trim());
  }
  return clauses.join(' ');
}

function updateTypeAllowed(condition, updateType) {
  return condition.split('||').some((rawClause) => {
    const clause = rawClause.trim();
    const match = clause.match(/^steps\.metadata\.outputs\.update-type\s*(==|!=)\s*'([^']*)'$/);
    assert.ok(match, `unsupported update-type clause: ${clause}`);
    const equals = updateType === match[2];
    return match[1] === '==' ? equals : !equals;
  });
}

function requiredCheckRule(context = requiredContext) {
  return {
    type: 'required_status_checks',
    ruleset_id: 28283290,
    parameters: { required_status_checks: [{ context }] },
  };
}

function runPreflight({ pages = [[]], failRules = false }) {
  const temporaryRoot = join(root, 'build');
  mkdirSync(temporaryRoot, { recursive: true });
  const directory = mkdtempSync(join(temporaryRoot, 'auto-merge-preflight-'));
  try {
    const bin = join(directory, 'bin');
    const fixtures = join(directory, 'fixtures');
    mkdirSync(bin);
    mkdirSync(fixtures);
    if (failRules) writeFileSync(join(fixtures, 'fail-rules'), '');
    writeFileSync(join(fixtures, 'pages.json'), JSON.stringify(pages));
    const log = join(directory, 'gh.log');
    const gh = join(bin, 'gh');
    writeFileSync(
      gh,
      `#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >> "$MOCK_GH_LOG"
if [ "$#" -lt 2 ] || [ "$1" != api ]; then
  echo 'only read-only gh api calls are allowed' >&2
  exit 64
fi
if [[ " $* " != *" --paginate "* ]] || [[ " $* " != *" --slurp "* ]]; then
  echo 'branch rules must be paginated and slurped' >&2
  exit 66
fi
case "$*" in
  *"/rules/branches/main?per_page=100"*)
    if [ -f "$MOCK_FIXTURES/fail-rules" ]; then exit 1; fi
    cat "$MOCK_FIXTURES/pages.json"
    ;;
  *)
    echo "unexpected gh invocation: $*" >&2
    exit 65
    ;;
esac
`,
    );
    chmodSync(gh, 0o755);
    const result = spawnSync('bash', ['-c', script], {
      cwd: root,
      env: {
        ...process.env,
        PATH: `${bin}:${process.env.PATH}`,
        GH_TOKEN: 'test-only-token',
        GITHUB_REPOSITORY: 'owner/repo',
        REQUIRED_CONTEXT: requiredContext,
        MOCK_FIXTURES: fixtures,
        MOCK_GH_LOG: log,
      },
      encoding: 'utf8',
      timeout: 10_000,
    });
    return {
      ...result,
      output: `${result.stdout}${result.stderr}`,
      calls: existsSync(log) ? readFileSync(log, 'utf8') : '',
    };
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

function runMerge(token) {
  const temporaryRoot = join(root, 'build');
  mkdirSync(temporaryRoot, { recursive: true });
  const directory = mkdtempSync(join(temporaryRoot, 'auto-merge-token-'));
  try {
    const bin = join(directory, 'bin');
    mkdirSync(bin);
    const log = join(directory, 'gh.log');
    const gh = join(bin, 'gh');
    writeFileSync(
      gh,
      `#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >> "$MOCK_GH_LOG"
if [ "$#" -lt 2 ] || [ "$1" != pr ] || [ "$2" != merge ]; then
  echo 'only the expected merge call is allowed' >&2
  exit 64
fi
`,
    );
    chmodSync(gh, 0o755);
    const result = spawnSync('bash', ['-c', mergeCommand], {
      cwd: root,
      env: {
        ...process.env,
        PATH: `${bin}:${process.env.PATH}`,
        GH_TOKEN: token,
        PR_URL: 'https://example.invalid/pull/1',
        MOCK_GH_LOG: log,
      },
      encoding: 'utf8',
      timeout: 10_000,
    });
    return {
      ...result,
      output: `${result.stdout}${result.stderr}`,
      calls: existsSync(log) ? readFileSync(log, 'utf8') : '',
    };
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
}

test('pull_request_target stays no-checkout and preflight precedes merge', () => {
  assert.ok(!workflow.includes('actions/checkout@'));
  assert.ok(workflow.indexOf('- name: Verify main requires the aggregate CI check') < workflow.indexOf('gh pr merge --auto'));
});

test('preflight uses only paginated main branch-rules metadata', () => {
  assert.ok(workflow.includes('repos/$GITHUB_REPOSITORY/rules/branches/main?per_page=100'));
  assert.ok(workflow.includes('--paginate --slurp'));
  assert.ok(!workflow.includes('/branches/main/protection'));
  assert.ok(!workflow.includes('/rulesets'));
});

test('preflight and merge allow only explicit patch or minor update types', () => {
  const conditions = [
    stepCondition('Verify main requires the aggregate CI check'),
    stepCondition('Enable auto-merge for patch and minor updates'),
  ];
  assert.equal(conditions[0], conditions[1], 'preflight and merge conditions must stay aligned');
  const controls = [
    ['version-update:semver-patch', true],
    ['version-update:semver-minor', true],
    ['version-update:semver-major', false],
    ['', false],
    ['version-update:unknown', false],
  ];
  for (const condition of conditions) {
    for (const [updateType, expected] of controls) {
      assert.equal(updateTypeAllowed(condition, updateType), expected, `update type ${JSON.stringify(updateType)}`);
    }
  }
});

test('exact aggregate context in active main branch rules allows auto-merge', () => {
  const result = runPreflight({ pages: [[requiredCheckRule()]] });
  assert.equal(result.status, 0, result.output);
  assert.match(result.output, /Active main branch rules require/);
  assert.ok(result.calls.includes('/rules/branches/main?per_page=100'));
  assert.ok(!result.calls.includes('/branches/main/protection'));
  assert.ok(!result.calls.includes('/rulesets'));
});

test('required context is found on later metadata pages', () => {
  const result = runPreflight({
    pages: [[{ type: 'creation' }, requiredCheckRule('CodeQL')], [{ type: 'deletion' }, requiredCheckRule()]],
  });
  assert.equal(result.status, 0, result.output);
});

test('missing rules and classic-only protection fail with actionable guidance', () => {
  const result = runPreflight({});
  assert.notEqual(result.status, 0);
  assert.match(result.output, /active main ruleset/);
  assert.match(result.output, /classic branch protection alone is not visible/);
  assert.ok(!result.calls.includes('gh pr merge'));
});

test('wrong or merely similar contexts do not satisfy the aggregate requirement', () => {
  for (const context of ['CodeQL', 'Test, lint, and package (build)', 'Test, lint, and packag']) {
    const result = runPreflight({ pages: [[requiredCheckRule(context)]] });
    assert.notEqual(result.status, 0, context);
  }
});

test('other rule types and malformed required-check parameters fail closed', () => {
  const result = runPreflight({
    pages: [[{ type: 'creation' }, { type: 'required_status_checks', parameters: {} }]],
  });
  assert.notEqual(result.status, 0);
});

test('branch-rules API failures prevent auto-merge', () => {
  const result = runPreflight({ failRules: true });
  assert.notEqual(result.status, 0);
  assert.match(result.output, /Could not read main branch-rules metadata/);
});

after(() => console.log('AUTO_MERGE_PREFLIGHT_PASS'));
