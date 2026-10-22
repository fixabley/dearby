import React, { useEffect } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, NavLink, Route, Routes } from "react-router";
import { QueryClient, QueryClientProvider, useMutation } from "@tanstack/react-query";
import { App as AntApp, Alert, Button, ConfigProvider } from "antd";
import koKR from "antd/locale/ko_KR";
import { ApartmentOutlined, AuditOutlined, CalendarOutlined, KeyOutlined, LogoutOutlined, ReadOutlined, IdcardOutlined, TagsOutlined, WarningOutlined } from "@ant-design/icons";
import { logout, passkey, recheckRoles, useSession, useSingleFlight } from "./api";
import { Activities } from "./activities";
import { Organizations } from "./organizations";
import { Programs } from "./programs";
import { Histories } from "./histories";
import { Roles } from "./roles";
import { Jobs } from "./jobs";
import { Tags } from "./tags";
import { Reports } from "./reports";
import "./style.css";

function Brand() {
  return (
    <div className="brand">
      dearby<span>탐색 관리</span>
    </div>
  );
}

function Login() {
  const sign = useMutation({ mutationFn: passkey });
  const signOnce = useSingleFlight();
  return (
    <main className="login">
      <Brand />
      <h1>
        좋은 활동을
        <br />
        발견하는 시작점.
      </h1>
      <p>활동과 공식 정보를 관리하는 운영 공간입니다.</p>
      {sign.error && <Alert className="form-error" type="error" showIcon message={sign.error.message} />}
      <Button
        type="primary"
        block
        size="large"
        icon={<KeyOutlined aria-hidden="true" />}
        loading={sign.isPending && sign.variables === "authentication"}
        disabled={sign.isPending}
        onClick={() => signOnce(() => sign.mutateAsync("authentication"))}
      >
        패스키로 로그인
      </Button>
      <Button
        block
        size="large"
        loading={sign.isPending && sign.variables === "registration"}
        disabled={sign.isPending}
        onClick={() => signOnce(() => sign.mutateAsync("registration"))}
      >
        새 패스키로 시작
      </Button>
      <small>
        Dearby 앱과 같은 패스키 계정을 써요. 새 패스키로 시작하면 계정이 생기고, 운영자가 관리자 권한을 줘야 사용할 수
        있어요.
      </small>
    </main>
  );
}

function Forbidden() {
  const { message } = AntApp.useApp();
  const recheck = useMutation({
    mutationFn: recheckRoles,
    onSuccess: (admin) => admin || message.info("아직 관리자 권한이 없어요. 관리자에게 확인해 주세요."),
  });
  return (
    <main className="login">
      <Brand />
      <h1>관리자 권한이 없어요</h1>
      <p>로그인은 됐지만 이 계정에는 관리자 권한이 없어요. 관리자에게 권한을 요청해 주세요.</p>
      <p className="field-note">권한을 받은 뒤 ‘다시 확인’을 누르면 권한을 다시 읽어요.</p>
      {recheck.error && <Alert className="form-error" type="error" showIcon message={recheck.error.message} />}
      <Button type="primary" block size="large" loading={recheck.isPending} onClick={() => recheck.mutate()}>
        다시 확인
      </Button>
      <Button block size="large" onClick={() => logout()}>
        로그아웃
      </Button>
    </main>
  );
}

function Shell() {
  return (
    <div className="shell">
      <aside>
        <NavLink to="/activities" className="brand">
          dearby<span>탐색 관리</span>
        </NavLink>
        <nav aria-label="관리 메뉴">
          <NavLink to="/activities">
            <CalendarOutlined aria-hidden="true" />
            활동
          </NavLink>
          <NavLink to="/programs">
            <ReadOutlined aria-hidden="true" />
            프로그램
          </NavLink>
          <NavLink to="/organizations">
            <ApartmentOutlined aria-hidden="true" />
            조직
          </NavLink>
          <NavLink to="/histories">
            <AuditOutlined aria-hidden="true" />
            이력 검토
          </NavLink>
          <NavLink to="/roles">
            <TagsOutlined aria-hidden="true" />
            역할
          </NavLink>
          <NavLink to="/jobs">
            <IdcardOutlined aria-hidden="true" />
            직무
          </NavLink>
          <NavLink to="/tags">
            <TagsOutlined aria-hidden="true" />
            태그
          </NavLink>
          <NavLink to="/reports">
            <WarningOutlined aria-hidden="true" />
            신고
          </NavLink>
        </nav>
        <div className="sidebar-bottom">
          <Button icon={<LogoutOutlined aria-hidden="true" />} onClick={() => logout()}>
            로그아웃
          </Button>
        </div>
      </aside>
      <main className="workspace">
        <div className="topline">
          <span>발견을 위한 운영 공간</span>
          <span>공식 출처 기준으로 관리해요</span>
        </div>
        <Routes>
          <Route path="/activities" element={<Activities />} />
          <Route path="/programs" element={<Programs />} />
          <Route path="/organizations" element={<Organizations />} />
          <Route path="/histories" element={<Histories />} />
          <Route path="/roles" element={<Roles />} />
          <Route path="/jobs" element={<Jobs />} />
          <Route path="/tags" element={<Tags />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="*" element={<Navigate to="/activities" replace />} />
        </Routes>
      </main>
    </div>
  );
}

function Root() {
  const { tokens, forbidden } = useSession();
  // Logout and an expired refresh both end here; drop the previous account's cached data.
  useEffect(() => {
    if (!tokens) queryClient.clear();
  }, [tokens]);
  if (!tokens) return <Login />;
  if (forbidden) return <Forbidden />;
  return <Shell />;
}

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={koKR}
      theme={{
        token: {
          colorPrimary: "#007777",
          borderRadius: 8,
          fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
          fontSize: 14,
          colorBgLayout: "#f5f7f7",
          colorText: "#153236",
        },
      }}
    >
      <AntApp>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <Root />
          </BrowserRouter>
        </QueryClientProvider>
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
