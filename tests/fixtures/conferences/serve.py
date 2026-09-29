"""Explicit loopback mock catalog. Never imported by the production API."""
import argparse
import datetime as dt
import html
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

ASSETS = Path(__file__).resolve().parents[3] / 'shared/assets/conferences'
ARTWORK = {'feconf-2026': 'feconf.png', 'kakao-2026': 'kakao-2026.png', 'pycon-2026': 'pycon.png', 'dan-2025': 'dan.png'}

RECORDS = json.loads(Path(__file__).with_name('data.json').read_text())['records']


def identifier(value):
    return str(uuid5(NAMESPACE_URL, 'dearby/mock/conferences/' + value))


def catalog(origin):
    now = dt.datetime.now(dt.timezone.utc)
    iso = lambda value: value.isoformat().replace('+00:00', 'Z')
    result = dict(generatedAt=iso(now), organizations=[], programs=[], activities=[])
    for record in RECORDS:
        key = record['key']
        org, program, activity = [identifier(key + '/' + kind) for kind in ('org', 'program', 'activity')]
        result['organizations'].append(dict(id=org, name=record['organization'], description='공식 정보 기반 목 데이터'))
        result['programs'].append(dict(id=program, organizationId=org, title=record['program'], description=record['summary']))
        schedules = [dict(id=identifier(key + '/schedule/' + str(index)), dateLabel=record['date'], timeZone='Asia/Seoul', **schedule)
                     for index, schedule in enumerate(record['schedules'])]
        result['activities'].append(dict(
            id=activity, programId=program, organizationId=org,
            title='[목 데이터] ' + record['name'], isPreview=True,
            imageUrl=origin + '/artwork/' + key if key in ARTWORK else None,
            summary=record['summary'] + ' 실제 상태: ' + record['status'],
            participationType=record['type'], recruitmentStatus='open', isRecruiting=True,
            recruitmentStartAt=None, recruitmentEndAt=None, dateLabel=record['date'], location=record['location'],
            cost=record.get('cost'), audience=None, qualification='목 데이터의 신청 페이지는 연습용이며 접수되지 않습니다.',
            roles=record['roles'], schedules=schedules, officialUrl=record['url'], applicationUrl=origin + '/apply/' + key,
            sourceCheckedAt=iso(now), validUntil=iso(now + dt.timedelta(hours=1)), freshness='verified',
            sourceNote='목 데이터 생성 시각이며 공식 모집 검증 시각이 아닙니다. 공식 정보 조사: 2026-09-29. 실제 상태: '
                       + record['status'] + ('. 가상 체험 일정입니다.' if key == 'calendar-demo-2026' else '. 기재 일정은 공식 정보이며 전체 세션을 포함하지 않을 수 있습니다.')))
    return result


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/v1/catalog':
            body = json.dumps(catalog(self.server.origin), ensure_ascii=False).encode()
            kind = 'application/json; charset=utf-8'
        elif self.path.startswith('/artwork/'):
            name = ARTWORK.get(self.path.removeprefix('/artwork/'))
            if name is None:
                self.send_error(404)
                return
            body = (ASSETS / name).read_bytes()
            kind = 'image/png'
        elif self.path.startswith('/apply/'):
            record = next((item for item in RECORDS if item['key'] == self.path.removeprefix('/apply/')), None)
            if record is None:
                self.send_error(404)
                return
            body = ('<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width">'
                    '<title>목 데이터 신청 페이지</title><body style="font:18px system-ui;padding:24px;line-height:1.7">'
                    '<h1>신청 페이지 미리보기</h1><h2>' + html.escape(record['name']) + '</h2><p>목 데이터입니다. 실제 신청은 접수되지 않습니다.</p><p>실제 상태: '
                    + html.escape(record['status']) + '</p><p>이 창을 닫으면 앱의 신청 기록 흐름을 확인할 수 있습니다.</p></body></html>').encode()
            kind = 'text/html; charset=utf-8'
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header('Content-Type', kind)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=58764)
    args = parser.parse_args()
    server = ThreadingHTTPServer(('127.0.0.1', args.port), Handler)
    server.origin = f'http://127.0.0.1:{server.server_port}'
    print(f'MOCK ONLY: {server.origin}/v1/catalog ({len(RECORDS)} conferences)', flush=True)
    server.serve_forever()
