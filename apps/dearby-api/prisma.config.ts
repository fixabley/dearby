import { defineConfig } from 'prisma/config';
// Generation needs no database credentials. DDL belongs to supabase/migrations only.
export default defineConfig({schema:'prisma/schema.prisma'});
