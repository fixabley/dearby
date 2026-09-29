import { createClient } from "@supabase/supabase-js";
import type { AuthProvider } from "@refinedev/core";
const url = import.meta.env.VITE_SUPABASE_URL;
const key = import.meta.env.VITE_SUPABASE_ANON_KEY;
function isSecret(value: string) {
  if (value.startsWith("sb_secret_")) return true;
  try {
    return JSON.parse(atob(value.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"))).role === "service_role";
  } catch {
    return false;
  }
}
export const configurationError =
  !url || !key
    ? "Supabase 연결 설정이 필요합니다. apps/admin/.env.local 안내를 확인해 주세요."
    : isSecret(key)
      ? "브라우저에는 관리자 비밀 키를 사용할 수 없습니다. 공개 키로 설정해 주세요."
      : null;
export const supabase = configurationError ? null : createClient(url, key);
export const authProvider: AuthProvider = {
  login: async ({ email, password }) => {
    const { data, error } = await supabase!.auth.signInWithPassword({
      email,
      password,
    });
    if (error)
      return {
        success: false,
        error: {
          name: "로그인 실패",
          message: "이메일과 비밀번호 또는 연결 상태를 확인해 주세요.",
        },
      };
    const permission = await supabase!.rpc("is_catalog_admin");
    if (permission.error || !permission.data || !data.user) {
      await supabase!.auth.signOut();
      return {
        success: false,
        error: {
          name: "접근 불가",
          message: "탐색 관리자 권한이 있는 계정만 사용할 수 있습니다.",
        },
      };
    }
    return { success: true };
  },
  logout: async () => {
    const { error } = await supabase!.auth.signOut();
    return error ? { success: false, error } : { success: true };
  },
  check: async () => {
    const { data, error } = await supabase!.auth.getUser();
    if (error || !data.user) return { authenticated: false };
    const permission = await supabase!.rpc("is_catalog_admin");
    return { authenticated: !permission.error && permission.data === true };
  },
  getIdentity: async () => {
    const { data } = await supabase!.auth.getUser();
    return { id: data.user?.id, name: data.user?.email };
  },
  onError: async (error) => ({
    error,
    logout: error.statusCode === 401 || error.statusCode === 403,
  }),
};
