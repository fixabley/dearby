import Link from "next/link";
import { Icon } from "@/shared/ui/icon";

/** 활동 목록의 카드 한 장. 화면이 조직 이름·참여 방식 문구를 계산해 넘기고, 누르면 활동 상세로 간다. */
export function ActivityCard({
  id,
  title,
  summary,
  organizationName,
  participationLabel,
  dateLabel,
  location,
}: {
  id: string;
  title: string;
  summary: string;
  organizationName: string;
  participationLabel: string;
  dateLabel: string;
  location: string | null;
}) {
  return (
    <Link className="activity-card" href={`/activities/${id}`}>
      <span className="activity-art" aria-hidden="true">
        <Icon name="calendar" size={34} />
      </span>
      <div className="activity-copy">
        <p className="eyebrow">
          {organizationName} <span className="dot">·</span> {participationLabel}
        </p>
        <h2>{title}</h2>
        <p className="summary">{summary}</p>
        <div className="activity-meta">
          <span>
            <Icon name="calendar" size={17} />
            {dateLabel || "일정 미확인"}
          </span>
          <span>
            <Icon name="pin" size={17} />
            {location || "장소 미확인"}
          </span>
        </div>
      </div>
      <span className="card-chevron" aria-hidden="true">
        ›
      </span>
    </Link>
  );
}
