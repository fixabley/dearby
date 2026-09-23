import { notFound } from "next/navigation";
import Image from "next/image";
import Link from "next/link";
import { programs, organizations } from "@/features/catalog/data";
import { SaveButton } from "@/features/saved/provider";
export function generateStaticParams() {
  return programs.map((p) => ({ id: p.id }));
}
export default async function ProgramPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const p = programs.find((p) => p.id === id);
  if (!p) notFound();
  const org = organizations.find((o) => o.id === p.orgId)!;
  return (
    <>
      <Link href="/" className="back-link">
        ← 전체 탐색
      </Link>
      <div className="watch-layout">
        <section className="watch-main">
          <Image
            className="detail-cover"
            src={p.cover}
            alt={`${p.title} 프로그램 포스터`}
            width={1200}
            height={675}
            priority
          />
          <div className="detail-heading">
            <span className="eyebrow">
              {p.category} · {p.location}
            </span>
            <h1>{p.title}</h1>
            <p>{p.subtitle}</p>
          </div>
          <div className="channel-row">
            <span className="avatar large" style={{ background: org.color }}>
              {org.initial}
            </span>
            <div>
              <strong>{org.name}</strong>
              <p>
                샘플 조직 · 프로그램{" "}
                {programs.filter((x) => x.orgId === org.id).length}개
              </p>
            </div>
            <SaveButton kind="organizations" id={org.id} />
            <SaveButton kind="programs" id={p.id} />
          </div>
          <div className="description">
            <h2>어떤 경험을 하게 되나요?</h2>
            <p>
              {p.subtitle}. 관심 있는 분야의 사람들과 새로운 경험을 시작하는
              샘플 프로그램입니다. 구체적인 활동과 지원 조건은 회차별 공고에서
              확인하세요.
            </p>
            <p>{org.description}</p>
            <h3>참여 전 확인해 주세요</h3>
            <p>
              지원 조건은 공고별로 다릅니다. 자격을 자동 판정하지 않으며, 제공
              경험과 지원에 필요한 경험은 별개입니다.
            </p>
            <p className="muted">
              이 페이지는 가상 데이터입니다. 실제 지원이나 외부 모집 페이지는
              제공하지 않습니다. 주소를 복사하면 이 프로그램을 다시 열 수
              있습니다.
            </p>
          </div>
        </section>
        <aside className="notice-list" aria-labelledby="notices-title">
          <h2 id="notices-title">
            회차별 모집 공고 <span>{p.notices.length}</span>
          </h2>
          <p className="muted">현재 회차의 근거만 검색에 반영해요.</p>
          {p.notices.map((n) => (
            <article
              className={`notice ${!n.current ? "past" : ""}`}
              key={n.id}
            >
              <div className="notice-top">
                <span>
                  {n.round} · {n.current ? "현재 회차" : "지난 회차"}
                </span>
                <b className={n.open ? "open-text" : ""}>
                  {n.open ? "모집 중" : "모집 종료"}
                </b>
              </div>
              <h3>{n.roles.join(" · ")}</h3>
              <p className="notice-date">
                모집 {n.start.replaceAll("-", ".")} —{" "}
                {n.deadline.replaceAll("-", ".")}
              </p>
              <p>
                <strong>지원 조건</strong>
                <br />
                {n.qualification}
              </p>
              {!n.current && (
                <div className="past-note">
                  지난 기수에서 제공 · 이번 기수 미확인
                </div>
              )}
              <details open={n.current}>
                <summary>제공 경험과 근거</summary>
                {n.activities.map((a, i) => (
                  <div className="activity" key={i}>
                    <b>
                      {a.action}
                      {a.target ? " | " + a.target : ""}
                      {a.method ? " · " + a.method : ""}
                    </b>
                    <p>{a.evidence}</p>
                  </div>
                ))}
              </details>
            </article>
          ))}
        </aside>
      </div>
    </>
  );
}
