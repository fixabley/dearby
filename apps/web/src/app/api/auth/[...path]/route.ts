import { authRoute } from "@/lib/auth-routes";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
async function handle(request: Request, { params }: { params: Promise<{ path: string[] }> }) {
  return authRoute(request, (await params).path);
}
export { handle as GET, handle as POST };
