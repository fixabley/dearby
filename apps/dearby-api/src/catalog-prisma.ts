import { atTime, catalogSchema, type Catalog } from './catalog.js';
import type { DB } from './postgres.js';
import { ApiError } from './validation.js';
export function prismaCatalog(db: DB): () => Promise<Catalog> {
  return async () => {
    try {
      const [row] = await db.$queryRaw<{snapshot:unknown}[]>`SELECT public.catalog_public_snapshot() AS snapshot`;
      const catalog = catalogSchema.parse(row?.snapshot);
      const now = Date.now();
      return {...catalog,generatedAt:new Date(now).toISOString(),activities:catalog.activities.map(item => atTime(item,now))};
    } catch { throw new ApiError(503,'CATALOG_UNAVAILABLE','Activity catalog temporarily unavailable'); }
  };
}
