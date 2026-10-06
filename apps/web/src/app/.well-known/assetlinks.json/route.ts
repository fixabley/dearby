import { assetLinks, associationResponse } from "@/lib/app-links";
// Read the environment per request so new values apply without a rebuild.
export const dynamic = "force-dynamic";
export function GET() {
  return associationResponse(assetLinks());
}
