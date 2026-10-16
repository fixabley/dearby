import type { ReactNode } from "react";

/**
 * 명함 본문(마크다운 부분집합) 렌더러. 계약 "템플릿": 제목(#~###), 굵게, 기울임, 목록, 번호 목록, 링크(https·mailto·tel), 줄바꿈.
 * 원시 HTML은 글자로 보이고(React가 이스케이프), 허용하지 않는 링크는 글자로 남긴다. 외부 라이브러리를 쓰지 않는다.
 */
export function Markdown({ source }: { source: string }) {
  return <>{blocks(source).map((block, index) => render(block, index))}</>;
}

type Block =
  | { kind: "heading"; level: 1 | 2 | 3; text: string }
  | { kind: "list"; ordered: boolean; items: string[] }
  | { kind: "paragraph"; lines: string[] };

function blocks(source: string): Block[] {
  const result: Block[] = [];
  for (const line of source.replace(/\r\n?/g, "\n").split("\n")) {
    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    const bullet = /^\s*[-*+]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(line);
    const last = result[result.length - 1];
    if (heading) {
      result.push({ kind: "heading", level: heading[1].length as 1 | 2 | 3, text: heading[2] });
    } else if (bullet || numbered) {
      const ordered = !!numbered;
      const item = (bullet ?? numbered)![1];
      if (last?.kind === "list" && last.ordered === ordered) last.items.push(item);
      else result.push({ kind: "list", ordered, items: [item] });
    } else if (!line.trim()) {
      result.push({ kind: "paragraph", lines: [] });
    } else if (last?.kind === "paragraph") {
      last.lines.push(line);
    } else {
      result.push({ kind: "paragraph", lines: [line] });
    }
  }
  return result.filter((block) => block.kind !== "paragraph" || block.lines.length);
}

function render(block: Block, key: number): ReactNode {
  switch (block.kind) {
    case "heading": {
      const Tag = `h${block.level}` as const;
      return <Tag key={key}>{inline(block.text)}</Tag>;
    }
    case "list": {
      const Tag = block.ordered ? "ol" : "ul";
      return (
        <Tag key={key}>
          {block.items.map((item, index) => (
            <li key={index}>{inline(item)}</li>
          ))}
        </Tag>
      );
    }
    case "paragraph":
      return (
        <p key={key}>
          {block.lines.map((line, index) => (
            <span key={index}>
              {index > 0 && <br />}
              {inline(line)}
            </span>
          ))}
        </p>
      );
  }
}

const INLINE = /(\*\*(.+?)\*\*)|(\*(.+?)\*)|(_(.+?)_)|(\[([^\]]+)\]\(([^)\s]+)\))/;

/** 굵게·기울임·링크만 해석한다. 허용 scheme(https·mailto·tel)이 아닌 링크는 글자로 둔다. */
function inline(text: string): ReactNode[] {
  const parts: ReactNode[] = [];
  let rest = text;
  let key = 0;
  while (rest) {
    const match = INLINE.exec(rest);
    if (!match) {
      parts.push(rest);
      break;
    }
    if (match.index > 0) parts.push(rest.slice(0, match.index));
    if (match[2] !== undefined) parts.push(<strong key={key++}>{inline(match[2])}</strong>);
    else if (match[4] !== undefined) parts.push(<em key={key++}>{inline(match[4])}</em>);
    else if (match[6] !== undefined) parts.push(<em key={key++}>{inline(match[6])}</em>);
    else if (match[8] !== undefined && match[9] !== undefined) {
      const href = match[9];
      if (/^(https:\/\/|mailto:|tel:)/i.test(href))
        parts.push(
          <a key={key++} href={href} target={href.startsWith("https") ? "_blank" : undefined} rel="noopener noreferrer">
            {match[8]}
          </a>,
        );
      else parts.push(match[0]);
    }
    rest = rest.slice(match.index + match[0].length);
  }
  return parts;
}
