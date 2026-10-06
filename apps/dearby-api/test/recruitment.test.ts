import { test } from 'node:test';
import assert from 'node:assert/strict';
import { atTime, catalogId, verificationLifetime, type Activity } from '../src/catalog.js';

const checked = Date.parse('2026-10-06T00:00:00.000Z');
const now = checked + 60 * 60 * 1000;
const iso = (ms: number) => new Date(ms).toISOString();
const activity = (patch: Partial<Activity>): Activity => ({
  id:catalogId('activity','window-fixture'), programId:catalogId('program','window-fixture'), organizationId:catalogId('organization','window-fixture'),
  title:'Window fixture', summary:'', imageUrl:null, participationType:'registration', recruitmentStatus:'unknown', isRecruiting:false,
  recruitmentStartAt:null, recruitmentEndAt:null, dateLabel:'', location:null, cost:null, audience:null, qualification:null, roles:[], schedules:[],
  officialUrl:'https://example.com/fixture', applicationUrl:null, sourceCheckedAt:iso(checked), validUntil:iso(checked + verificationLifetime),
  freshness:'verified', sourceNote:'', ...patch,
});
const status = (patch: Partial<Activity>, at = now) => { const r = atTime(activity(patch), at); return [r.recruitmentStatus, r.isRecruiting]; };

test('verified recruitment window decides status: start inclusive, end exclusive, either date alone', () => {
  assert.deepEqual(status({recruitmentStartAt:iso(now + 1),recruitmentEndAt:iso(now + 2)}),['scheduled',false]);
  assert.deepEqual(status({recruitmentStartAt:iso(now),recruitmentEndAt:iso(now + 1)}),['open',true]);
  assert.deepEqual(status({recruitmentStartAt:iso(now - 1),recruitmentEndAt:iso(now + 1),recruitmentStatus:'scheduled'}),['open',true]);
  assert.deepEqual(status({recruitmentStartAt:iso(now - 2),recruitmentEndAt:iso(now)}),['closed',false]);
  assert.deepEqual(status({recruitmentEndAt:iso(now + 1)}),['open',true]);
  assert.deepEqual(status({recruitmentEndAt:iso(now)}),['closed',false]);
  assert.deepEqual(status({recruitmentStartAt:iso(now)}),['open',true]);
  assert.deepEqual(status({recruitmentStartAt:iso(now + 1),recruitmentStatus:'open'}),['scheduled',false]);
});

test('without dates the selected status stands; a selected close always wins', () => {
  for (const selected of ['open','scheduled','closed','unknown'] as const)
    assert.deepEqual(status({recruitmentStatus:selected}),[selected,selected === 'open']);
  assert.deepEqual(status({recruitmentStatus:'closed',recruitmentStartAt:iso(now - 1),recruitmentEndAt:iso(now + 1)}),['closed',false]);
  assert.deepEqual(status({recruitmentStatus:'closed',recruitmentStartAt:iso(now + 1)}),['closed',false]);
});

test('unverified data is unknown unless closed; never recruiting', () => {
  const window = {recruitmentStartAt:iso(now - 1),recruitmentEndAt:iso(now + 1)};
  assert.deepEqual(status({...window,freshness:'stale'}),['unknown',false]);
  assert.deepEqual(status({recruitmentStartAt:iso(checked),recruitmentEndAt:iso(checked + 2 * verificationLifetime)},checked + verificationLifetime),['unknown',false]);
  assert.deepEqual(status({recruitmentStatus:'open',freshness:'stale'}),['unknown',false]);
  assert.deepEqual(status({recruitmentStartAt:iso(now + 1),freshness:'stale'}),['unknown',false]);
  assert.deepEqual(status({recruitmentEndAt:iso(now),freshness:'stale'}),['closed',false]);
  assert.deepEqual(status({recruitmentStatus:'closed',freshness:'stale'}),['closed',false]);
  const failed = atTime(activity(window),now,true);
  assert.deepEqual([failed.freshness,failed.recruitmentStatus,failed.isRecruiting],['unavailable','unknown',false]);
});

test('failed re-verification note has no leading space when the public note is empty', () => {
  const notice = '현재 공식 안내를 다시 확인할 수 없어 마지막 확인 내용을 표시합니다.';
  assert.equal(atTime(activity({sourceNote:''}),now,true).sourceNote,notice);
  assert.equal(atTime(activity({sourceNote:'공식 안내 기준.'}),now,true).sourceNote,'공식 안내 기준. '+notice);
  assert.equal(atTime(activity({sourceNote:''}),now).sourceNote,'');
});
