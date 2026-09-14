import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { inferActivityTarget } from './infer-activity-target.mjs';

const read = (file) => JSON.parse(readFileSync(new URL(`../shared/contracts/activities/${file}`, import.meta.url), 'utf8'));
const data = read('sample.json');
const { rules } = read('target-rules.json');
const career = data.activities.find((a) => a.favoriteOrganizationId === 'krc');
const infer = (activity, overrides = rules) => inferActivityTarget(activity, data.organizations, overrides);

test('approved cases resolve to canonical subjects and categories, with unique rules', () => {
  assert.equal(new Set(rules.map((r) => r.id)).size, rules.length);
  for (const rule of rules) {
    assert.ok(rule.approvedCases.length > 0);
    for (const entry of rule.approvedCases) {
      const activity = data.activities.find((a) => a.id === entry.activityId);
      assert.ok(activity);
      const result = infer(activity);
      assert.equal(result.status, 'approved');
      assert.equal(result.targetOrganizationId, activity.favoriteOrganizationId);
      assert.equal(result.ruleId, activity.targetSelection.ruleId);
      assert.deepEqual(result.categoryPath, activity.categoryPath);
    }
  }
});

test('future editions reuse a subject as a suggestion without silently approving or mutating it', () => {
  const next = structuredClone(career);
  next.id = 'future-year-notice';
  next.title = '다음 연도 채용설명회';
  next.favoriteOrganizationId = null;
  const before = structuredClone(next);
  assert.equal(infer(next).status, 'suggested');
  assert.equal(infer(next).targetOrganizationId, 'krc');
  assert.deepEqual(next, before);
});

test('publisher and contact alone never determine the interest target', () => {
  const changed = structuredClone(career);
  changed.organizationLinks = [{ role: 'publisher', organizationId: 'krc' }, { role: 'contact', organizationId: 'cbnu-career' }];
  assert.equal(infer(changed).reason, 'no_subject');
  assert.equal(infer(changed).targetOrganizationId, null);
});

test('multiple subjects, unknown IDs, and conflicting rules require review', () => {
  const changed = structuredClone(career);
  changed.organizationLinks.push({ role: 'subject', organizationId: 'db-insurance' });
  assert.equal(infer(changed).reason, 'multiple_subjects');
  changed.organizationLinks = [{ role: 'subject', organizationId: 'missing' }];
  assert.equal(infer(changed).reason, 'unknown_subject');
  assert.equal(infer(career, [...rules, rules.find((r) => r.id === 'career-krc')]).reason, 'conflicting_rules');
});

test('changed event context or activity kind cannot inherit exact-case approval', () => {
  const changed = structuredClone(career);
  changed.contexts = [];
  assert.equal(infer(changed).status, 'suggested');
  changed.kind = 'mentoring';
  assert.equal(infer(changed).reason, 'no_approved_precedent');
});
