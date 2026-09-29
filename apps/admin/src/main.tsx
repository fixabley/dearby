import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, NavLink, Navigate } from "react-router";
import {
  Authenticated,
  Refine,
  useLogin,
  useLogout,
  useGetIdentity,
} from "@refinedev/core";
import { catalogDataProvider } from "./provider";
import {
  App as AntApp,
  Alert,
  Button,
  ConfigProvider,
  Form,
  Input,
  Spin,
} from "antd";
import koKR from "antd/locale/ko_KR";
import {
  CalendarOutlined,
  ApartmentOutlined,
  ReadOutlined,
  HistoryOutlined,
  LogoutOutlined,
} from "@ant-design/icons";
import { supabase, configurationError, authProvider } from "./supabase";
import { ActivityList, ActivityEditor } from "./activities";
import { Directory, AuditLog } from "./directories";
import { CollectionJobs } from "./collection";
import "./style.css";

function Login() {
  const login = useLogin();
  return (
    <main className="login">
      <div className="brand">
        dearby<span>탐색 관리</span>
      </div>
      <h1>
        좋은 활동을
        <br />
        발견하는 시작점.
      </h1>
      <p>활동과 공식 정보를 관리하는 운영 공간입니다.</p>
      <Form layout="vertical" onFinish={(values) => login.mutate(values)}>
        <Form.Item
          label="이메일"
          name="email"
          rules={[
            {
              required: true,
              type: "email",
              message: "이메일을 입력해 주세요.",
            },
          ]}
        >
          <Input autoComplete="username" />
        </Form.Item>
        <Form.Item
          label="비밀번호"
          name="password"
          rules={[{ required: true, message: "비밀번호를 입력해 주세요." }]}
        >
          <Input.Password autoComplete="current-password" />
        </Form.Item>
        {login.error && (
          <Alert type="error" showIcon message={login.error.message} />
        )}
        <Button
          type="primary"
          htmlType="submit"
          block
          size="large"
          loading={login.isPending}
        >
          관리자로 로그인
        </Button>
      </Form>
      <small>승인된 관리자 계정만 접근할 수 있습니다.</small>
    </main>
  );
}
function Shell() {
  const logout = useLogout();
  const { data: identity } = useGetIdentity<{ name?: string }>();
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
          <NavLink to="/collection">
            <HistoryOutlined aria-hidden="true" />
            활동 수집
          </NavLink>
          <NavLink to="/audit">
            <HistoryOutlined aria-hidden="true" />
            변경 기록
          </NavLink>
        </nav>
        <div className="sidebar-bottom">
          <span>{identity?.name}</span>
          <Button
            icon={<LogoutOutlined aria-hidden="true" />}
            onClick={() => logout.mutate()}
            loading={logout.isPending}
          >
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
          <Route path="/activities" element={<ActivityList />} />
          <Route path="/activities/new" element={<ActivityEditor />} />
          <Route path="/activities/:id" element={<ActivityEditor />} />
          <Route path="/programs" element={<Directory kind="programs" />} />
          <Route
            path="/organizations"
            element={<Directory kind="organizations" />}
          />
          <Route path="/collection" element={<CollectionJobs />} />
          <Route path="/audit" element={<AuditLog />} />
          <Route path="*" element={<Navigate to="/activities" replace />} />
        </Routes>
      </main>
    </div>
  );
}
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={koKR}
      theme={{
        token: {
          colorPrimary: "#007777",
          borderRadius: 8,
          fontFamily:
            '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
          fontSize: 14,
          colorBgLayout: "#f5f7f7",
          colorText: "#153236",
        },
      }}
    >
      <AntApp>
        {configurationError ? (
          <div className="login">
            <h1>Dearby 탐색 관리</h1>
            <Alert type="warning" message={configurationError} />
          </div>
        ) : (
          <BrowserRouter>
            <Refine
              dataProvider={catalogDataProvider(supabase!)}
              authProvider={authProvider}
              options={{
                disableTelemetry: true,
                mutationMode: "pessimistic",
                reactQuery: {
                  clientConfig: {
                    defaultOptions: {
                      queries: { retry: false, refetchOnWindowFocus: true },
                    },
                  },
                },
              }}
              resources={[
                { name: "catalog_activities" },
                { name: "catalog_programs" },
                { name: "catalog_organizations" },
                { name: "catalog_audit_log" },
              ]}
            >
              <Authenticated
                key="admin"
                fallback={<Login />}
                loading={
                  <div className="login">
                    <Spin tip="권한 확인 중" />
                  </div>
                }
              >
                <Shell />
              </Authenticated>
            </Refine>
          </BrowserRouter>
        )}
      </AntApp>
    </ConfigProvider>
  </React.StrictMode>,
);
