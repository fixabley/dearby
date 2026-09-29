import { proxy } from "@/lib/proxy";
export const runtime = "nodejs";
export const dynamic = "force-dynamic";
async function handle(
  request: Request,
  { params }: { params: Promise<{ path: string[] }> },
) {
  return proxy(request, (await params).path);
}
export { handle as GET, handle as PUT, handle as DELETE, handle as POST };
