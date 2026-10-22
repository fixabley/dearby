import { useEffect, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { App, Button, Popconfirm, Space, Upload } from "antd";
import { api, apiBlob } from "./api";

const logoTypes = ["image/png", "image/jpeg", "image/webp"];
const logoMaxBytes = 1024 * 1024;
const logoRule = "로고는 PNG·JPEG·WebP 1MB 이하여야 합니다";

/**
 * Logo preview (contract admin-v1.md "프로그램 로고"·"조직 로고"): the admin GET (`path`, e.g. `programs/3/logo`) is
 * bearer-gated, so the image comes down as a blob through `apiBlob` and is shown from an object URL. Keyed by `logoUrl`
 * (carries `?v=`), so it refetches exactly when the logo changes. Loading, failure and null show the first-letter avatar
 * like the discovery card. `dimmed` marks an inherited logo.
 */
export function LogoImage({ path, logoUrl, name, size = 44, dimmed }: { path: string; logoUrl: string | null; name: string; size?: number; dimmed?: boolean }) {
  const blob = useQuery({
    queryKey: ["logo", path, logoUrl],
    queryFn: () => apiBlob(path),
    enabled: !!logoUrl,
    staleTime: Infinity,
    retry: false,
  });
  const [src, setSrc] = useState<string>();
  useEffect(() => {
    if (!blob.data) {
      setSrc(undefined);
      return;
    }
    const url = URL.createObjectURL(blob.data);
    setSrc(url);
    return () => URL.revokeObjectURL(url);
  }, [blob.data]);
  const style = { width: size, height: size, borderRadius: Math.round(size * 14 / 44), flex: "none", opacity: dimmed ? 0.45 : 1 } as const;
  if (src && logoUrl) return <img src={src} alt={`${name} 로고`} style={{ ...style, objectFit: "cover" }} />;
  return (
    <div
      aria-hidden
      style={{ ...style, display: "grid", placeItems: "center", background: "#d8f3ee", color: "#0f766e", fontWeight: 600, fontSize: size * 0.4 }}
    >
      {name.trim().charAt(0)}
    </div>
  );
}

/**
 * Upload·delete for one logo (`path` is the admin logo route). Changes apply at once, independent of any form's 저장,
 * so the current `logoUrl` lives here; `stale` query keys are refreshed after each change. `fallback` shows instead of
 * the avatar when there is no own logo (an inherited one).
 */
export function LogoField({
  path,
  name,
  initialLogoUrl,
  stale,
  size = 44,
  fallback,
}: {
  path: string;
  name: string;
  initialLogoUrl: string | null;
  stale: string[][];
  size?: number;
  fallback?: ReactNode;
}) {
  const { message } = App.useApp();
  const client = useQueryClient();
  const [logoUrl, setLogoUrl] = useState(initialLogoUrl);
  const refresh = () => stale.forEach((queryKey) => client.invalidateQueries({ queryKey }));
  const upload = useMutation({
    mutationFn: (file: File) => {
      const body = new FormData();
      body.append("file", file);
      return api<{ logoUrl: string }>(path, "PUT", body);
    },
    onSuccess: ({ logoUrl }) => {
      setLogoUrl(logoUrl);
      refresh();
      message.success("로고를 올렸어요.");
    },
    onError: (error) => message.error(error.message),
  });
  const remove = useMutation({
    mutationFn: () => api<void>(path, "DELETE"),
    onSuccess: () => {
      setLogoUrl(null);
      refresh();
      message.success("로고를 지웠어요.");
    },
    onError: (error) => message.error(error.message),
  });
  const small = size < 40;
  return (
    <Space align="center">
      {!logoUrl && fallback ? fallback : <LogoImage path={path} logoUrl={logoUrl} name={name} size={size} />}
      <Upload
        accept={logoTypes.join(",")}
        showUploadList={false}
        disabled={upload.isPending}
        beforeUpload={(file) => {
          if (logoTypes.includes(file.type) && file.size <= logoMaxBytes) return true;
          message.error(logoRule);
          return Upload.LIST_IGNORE;
        }}
        customRequest={({ file }) => upload.mutate(file as File)}
      >
        <Button size={small ? "small" : "middle"} loading={upload.isPending} aria-label={`${name} 로고 업로드`}>
          업로드
        </Button>
      </Upload>
      <Popconfirm title="로고를 지울까요?" okText="지우기" okButtonProps={{ danger: true }} cancelText="취소" onConfirm={() => remove.mutate()}>
        <Button size={small ? "small" : "middle"} danger disabled={!logoUrl} loading={remove.isPending} aria-label={`${name} 로고 지우기`}>
          지우기
        </Button>
      </Popconfirm>
    </Space>
  );
}
