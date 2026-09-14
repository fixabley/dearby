import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

// Input is reviewed entity/role extraction, never a raw title or a publisher guess.
// A new notice receives a suggestion even when its organization has an approved precedent.
export function inferActivityTarget(activity, organizations, rules) {
  const known = new Set(organizations.map(({ id }) => id));
  const subjects = [...new Set(activity.organizationLinks
    .filter(({ role }) => role === 'subject').map(({ organizationId }) => organizationId))];
  const candidates = subjects.filter((id) => known.has(id));
  const review = (reason) => ({ status: 'needs_review', targetOrganizationId: null, categoryPath: null, ruleId: null, candidates, reason });
  if (subjects.some((id) => !known.has(id))) return review('unknown_subject');
  if (subjects.length !== 1) return review(subjects.length ? 'multiple_subjects' : 'no_subject');
  const matches = rules.filter((rule) => rule.kind === activity.kind && rule.subjectOrganizationId === subjects[0]);
  if (matches.length !== 1) return review(matches.length ? 'conflicting_rules' : 'no_approved_precedent');
  const rule = matches[0];
  if (rule.approvedBy !== 'user' || !known.has(rule.targetOrganizationId)
      || rule.targetOrganizationId !== subjects[0]) return review('invalid_rule');
  // Identity alone cannot override changed context on an already approved notice.
  const contextSignature = (contexts) => JSON.stringify(contexts.map(({ organizationId, role }) => `${role}:${organizationId}`).sort());
  const exactCase = rule.approvedCases?.find((entry) => entry.activityId === activity.id);
  const approved = exactCase && contextSignature(exactCase.contexts) === contextSignature(activity.contexts ?? []);
  return {
    status: approved ? 'approved' : 'suggested',
    targetOrganizationId: rule.targetOrganizationId,
    categoryPath: rule.categoryPath,
    ruleId: rule.id,
    candidates,
    reason: approved ? 'user_approved_case' : 'same_subject_and_activity_kind',
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const root = new URL('../', import.meta.url);
  const read = (path) => JSON.parse(readFileSync(new URL(path, root), 'utf8'));
  const catalog = process.argv[2]
    ? JSON.parse(readFileSync(process.argv[2], 'utf8'))
    : read('shared/contracts/activities/sample.json');
  const { rules } = read('shared/contracts/activities/target-rules.json');
  console.log(JSON.stringify(catalog.activities.map((activity) => ({
    activityId: activity.id,
    ...inferActivityTarget(activity, catalog.organizations, rules),
  })), null, 2));
}
