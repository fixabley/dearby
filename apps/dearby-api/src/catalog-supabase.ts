import { atTime, catalogSchema, type Catalog } from './catalog.js';
import { ApiError } from './validation.js';

export function supabaseCatalog(env: NodeJS.ProcessEnv, fetcher: typeof fetch = fetch): (() => Promise<Catalog>) | undefined {
  const backend = env.CATALOG_BACKEND ?? 'sqlite';
  if (backend === 'sqlite') return undefined;
  if (backend !== 'supabase') throw new Error('CATALOG_BACKEND must be sqlite or supabase');
  const base = env.SUPABASE_URL;
  const key = env.SUPABASE_ANON_KEY;
  if (!base || !key) throw new Error('Supabase catalog requires SUPABASE_URL and SUPABASE_ANON_KEY');
  if (!URL.canParse(base)) throw new Error('Invalid Supabase URL');
  const url = new URL(base);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost','127.0.0.1','[::1]','host.docker.internal'].includes(url.hostname))) {
    throw new Error('Supabase must use HTTPS or local Docker host/loopback');
  }
  if (url.username || url.password || url.search || url.hash) throw new Error('Invalid Supabase URL');
  return async () => {
    try {
      const response = await fetcher(new URL('/rest/v1/rpc/catalog_public_snapshot',url), {
        method:'POST', headers:{apikey:key,Authorization:`Bearer ${key}`,'Content-Type':'application/json'},
        body:'{}',signal:AbortSignal.timeout(8000),redirect:'error',
      });
      if (!response.ok) throw new Error('Catalog upstream unavailable');
      const catalog = catalogSchema.parse(await response.json());
      const now = Date.now();
      return {...catalog,generatedAt:new Date(now).toISOString(),activities:catalog.activities.map(item => atTime(item,now))};
    } catch {
      // Neither local SQLite history nor an empty response may impersonate a successful remote read.
      throw new ApiError(503,'CATALOG_UNAVAILABLE','Activity catalog temporarily unavailable');
    }
  };
}
