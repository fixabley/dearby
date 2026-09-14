import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { validateOrganizationTree } from './organization-tree.mjs';

const root = new URL('../', import.meta.url);
const load = (path) => JSON.parse(readFileSync(new URL(path, root), 'utf8'));
const data = load('shared/contracts/activities/sample.json');
const schema = load('shared/contracts/activities/schema.json');
const validator = new Ajv2020({ allErrors: true, allowUnionTypes: true });
addFormats(validator);
const validate = validator.compile(schema);

test('organization hierarchy permits branches and rejects dangling parents and cycles', () => {
  assert.doesNotThrow(() => validateOrganizationTree(data.organizations));
  const tree = [
    { id: 'university', parentOrganizationId: null },
    { id: 'department', parentOrganizationId: 'university' },
    { id: 'nestnet', parentOrganizationId: 'department' },
    { id: 'another-club', parentOrganizationId: 'department' },
  ];
  assert.doesNotThrow(() => validateOrganizationTree(tree));
  assert.throws(() => validateOrganizationTree([...tree, { id: 'orphan', parentOrganizationId: 'missing' }]), /Missing parent/);
  assert.throws(() => validateOrganizationTree([{ id: 'self', parentOrganizationId: 'self' }]), /cycle/);
  const cycle = structuredClone(tree);
  cycle[0].parentOrganizationId = 'nestnet';
  assert.throws(() => validateOrganizationTree(cycle), /cycle/);
  const missingParent = structuredClone(data);
  delete missingParent.organizations[0].parentOrganizationId;
  assert.equal(validate(missingParent), false);
});

test('sample conforms to the versioned contract; missing eligibility is rejected', () => {
  assert.ok(validate(data), JSON.stringify(validate.errors));
  const broken = structuredClone(data);
  delete broken.activities[0].eligibility;
  assert.equal(validate(broken), false);
});

test('venue coordinates are optional complete WGS84 pairs; malformed coordinates are rejected', () => {
  const withCoordinates = (coordinates) => {
    const copy = structuredClone(data);
    copy.activities.find((row) => row.kind === 'career_event').location.venues[0].coordinates = coordinates;
    return copy;
  };
  for (const coordinates of [null, { latitude: 0, longitude: 0 },
    { latitude: -90, longitude: -180 }, { latitude: 90, longitude: 180 }]) {
    assert.ok(validate(withCoordinates(coordinates)), JSON.stringify(validate.errors));
  }
  for (const coordinates of [{ latitude: 37 }, { longitude: 127 }, {},
    { latitude: 91, longitude: 127 }, { latitude: 37, longitude: -181 },
    { latitude: '37', longitude: 127 }, { latitude: null, longitude: 127 },
    { latitude: Infinity, longitude: 127 }]) {
    assert.equal(validate(withCoordinates(coordinates)), false);
  }
  const legacy = structuredClone(data);
  for (const row of legacy.activities) {
    for (const venue of row.location.venues) {
      delete venue.coordinates;
      delete venue.coordinateEvidence;
    }
  }
  assert.ok(validate(legacy), JSON.stringify(validate.errors));
});

test('sample map points retain official evidence and unresolved venues remain without coordinates', () => {
  const mapped = data.activities.flatMap((row) => row.location.venues).filter((venue) => venue.coordinates);
  assert.equal(mapped.length, 3);
  for (const venue of mapped) {
    assert.ok(venue.coordinateEvidence.length > 0);
    assert.ok(venue.coordinateEvidence.every((entry) => data.sources.some((source) => source.id === entry.sourceId)));
  }
  const contest = data.activities.find((row) => row.kind === 'competition');
  assert.equal(contest.location.venues[0].coordinates, null);
  assert.match(contest.location.summary, /상세 장소 확인 필요/);
});

test('all IDs are unique and all organization/source references resolve', () => {
  for (const rows of [data.activities, data.organizations, data.sources]) {
    assert.equal(new Set(rows.map((row) => row.id)).size, rows.length);
  }
  const organizations = new Set(data.organizations.map((row) => row.id));
  const sources = new Set(data.sources.map((row) => row.id));
  function walk(value) {
    if (!value || typeof value !== 'object') return;
    if (value.sourceId) assert.ok(sources.has(value.sourceId));
    if (value.sourceIds) value.sourceIds.forEach((id) => assert.ok(sources.has(id)));
    Object.values(value).forEach(walk);
  }
  walk(data);
  for (const activity of data.activities) {
    for (const context of activity.contexts) assert.ok(organizations.has(context.organizationId));
    for (const link of activity.organizationLinks) assert.ok(organizations.has(link.organizationId));
    if (activity.favoriteOrganizationId) {
      assert.ok(organizations.has(activity.favoriteOrganizationId));
      assert.ok(activity.organizationLinks.some((link) => link.organizationId === activity.favoriteOrganizationId));
    }
  }
});

test('date-only deadlines stay date-only and 24:00 rolls to the next day', () => {
  const english = data.activities.find((row) => row.id.endsWith('1153966'));
  assert.equal(english.application.closesAt, null);
  assert.equal(english.application.closesOn, '2026-09-16');
  const contest = data.activities.find((row) => row.id.endsWith('1154064'));
  assert.equal(contest.application.closesAt, '2026-10-08T00:00:00+09:00');
  assert.match(contest.application.originalDeadline, /24:00/);
  assert.equal(contest.location.venues[0].address, null);
});

test('conflicting source eligibility is preserved, never labeled known', () => {
  const mentoring = data.activities.find((row) => row.id.endsWith('7382'));
  assert.equal(mentoring.audience.status, 'conflicting');
  assert.equal(mentoring.eligibility.status, 'conflicting');
  assert.equal(mentoring.eligibility.rule, null);
  assert.ok(mentoring.qualityIssues.some((issue) => issue.resolveBy === 'source_review'));
});

test('team region condition is existential and allows school OR residence', () => {
  const contest = data.activities.find((row) => row.id.endsWith('1154064'));
  const condition = contest.eligibility.rule.all.find((node) => node.someMember);
  assert.deepEqual(condition.someMember.any.map((node) => node.field), ['member.schoolRegion', 'member.residenceRegion']);
  assert.ok(condition.someMember.any.every((node) => node.operator === 'in' && node.value.length === 5));
});

test('unrelated notices excluded; contest saves the user-selected program under its institution', () => {
  assert.equal(data.activities.find((row) => row.kind === 'academic_administration').demoVisible, false);
  const contest = data.activities.find((row) => row.kind === 'competition');
  assert.equal(contest.edition, 2);
  const program = data.organizations.find((row) => row.id === contest.favoriteOrganizationId);
  assert.equal(program.id, 'yeongnam-cyber-defense');
  assert.equal(program.parentOrganizationId, 'yeongnam-ai-security');
  assert.equal(contest.qualityIssues.some((issue) => issue.code === 'organization_unresolved'), false);
  const career = data.activities.filter((row) => row.kind === 'career_event');
  assert.equal(career.length, 2);
  assert.deepEqual(career.map((row) => row.favoriteOrganizationId).sort(), ['db-insurance', 'krc']);
  assert.ok(career.every((row) => row.contexts[0].organizationId === 'cbnu'));
  assert.equal(data.organizations.find((row) => row.id === 'cbnu').parentOrganizationId, null);
  assert.ok(career.every((row) => row.benefits[0].unit === 'CIEAT_POINT'));
});

test('iOS and Android bundle exactly the canonical samples', () => {
  const canonical = readFileSync(new URL('shared/contracts/activities/sample.json', root));
  for (const path of ['apps/ios/Dearby/Resources/activity-samples.json', 'apps/android/app/src/main/assets/activity-samples.json']) {
    assert.deepEqual(readFileSync(new URL(path, root)), canonical);
  }
});
