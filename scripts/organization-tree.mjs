// Cross-record constraints cannot be expressed by the JSON Schema alone.
export function validateOrganizationTree(organizations) {
  const byId = new Map(organizations.map((organization) => [organization.id, organization]));
  if (byId.size !== organizations.length) throw new Error('Duplicate organization ID');
  const complete = new Set();
  for (const organization of organizations) {
    const path = new Set();
    let current = organization;
    while (current && !complete.has(current.id)) {
      if (path.has(current.id)) throw new Error(`Organization cycle: ${current.id}`);
      path.add(current.id);
      if (current.parentOrganizationId === null) break;
      const parent = byId.get(current.parentOrganizationId);
      if (!parent) throw new Error(`Missing parent: ${current.parentOrganizationId}`);
      current = parent;
    }
    for (const id of path) complete.add(id);
  }
}
