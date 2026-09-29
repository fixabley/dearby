# Explicit local UI test fixture only. Never production catalog data.
import http.server,json,datetime
from datetime import timezone,timedelta
class Handler(http.server.BaseHTTPRequestHandler):
 def do_GET(self):
  now=datetime.datetime.now(timezone.utc)
  iso=lambda d:d.isoformat().replace('+00:00','Z')
  org='11111111-1111-4111-8111-111111111111';program='22222222-2222-4222-8222-222222222222';activity='33333333-3333-4333-8333-333333333333'
  item={'id':activity,'programId':program,'organizationId':org,'title':'검증 전용 활동 · 실제 모집 아님','summary':'캘린더와 신청 흐름을 확인하는 테스트 자료입니다.','participationType':'registration','recruitmentStatus':'open','isRecruiting':True,'recruitmentStartAt':iso(now-timedelta(days=1)),'recruitmentEndAt':iso(now+timedelta(days=1)),'dateLabel':'2026년 10월 24일 14–16시','location':None,'cost':None,'audience':None,'qualification':None,'roles':[],'schedules':[{'id':'44444444-4444-4444-8444-444444444444','title':'검증용 시간','startAt':'2026-10-24T14:00:00+09:00','endAt':'2026-10-24T16:00:00+09:00','dateLabel':'10월 24일','timeZone':'Asia/Seoul'}],'officialUrl':'http://127.0.0.1:58763/source','applicationUrl':'http://127.0.0.1:58763/apply','sourceCheckedAt':iso(now),'validUntil':iso(now+timedelta(hours=1)),'freshness':'verified','sourceNote':'로컬 UI 검증 전용 fixture'}
  if self.path=='/v1/catalog':
   body=json.dumps({'generatedAt':iso(now),'organizations':[{'id':org,'name':'검증용 조직','description':''}],'programs':[{'id':program,'organizationId':org,'title':'검증용 프로그램','description':''}],'activities':[item]}).encode();kind='application/json'
  else:body='<html><body><h1>Local application flow fixture</h1><p>No actual application is submitted.</p></body></html>'.encode();kind='text/html'
  self.send_response(200);self.send_header('Content-Type',kind);self.end_headers();self.wfile.write(body)
 def log_message(self,*args):pass
http.server.ThreadingHTTPServer(('127.0.0.1',58763),Handler).serve_forever()
