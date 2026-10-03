import { dataProvider } from "@refinedev/supabase";
import type { SupabaseClient } from "@supabase/supabase-js";
export function catalogDataProvider(client: SupabaseClient) {
  const provider = dataProvider(client);
  const originalUpdate = provider.update;
  provider.update = async (parameters) => {
    if (
      parameters.resource === "catalog_activities" &&
      parameters.meta?.expectedUpdatedAt
    ) {
      const { data, error } = await client
        .from(parameters.resource)
        .update(parameters.variables as Record<string, unknown>)
        .eq("id", parameters.id)
        .eq("updated_at", parameters.meta.expectedUpdatedAt)
        .select()
        .maybeSingle();
      if (error) throw error;
      if (!data)
        throw new Error(
          "다른 관리자가 활동을 수정했거나 권한이 변경됐어요. 입력 내용을 복사한 뒤 새로고침해 주세요.",
        );
      return { data };
    }
    const result = await originalUpdate(parameters);
    if (!result.data)
      throw new Error(
        "변경을 저장하지 못했어요. 데이터와 관리자 권한을 다시 확인해 주세요.",
      );
    return result;
  };
  return provider;
}
