import fs from 'node:fs';
import {dir,results,state,snap} from './integration-client.mjs';
const defs=[
 ['DEF-001','COM-004,OPS-001','치명','타센터 출고 상세·통합검색 데이터 노출','dc2.mgr(DC002, OWN_ORG)로 이천 OUT-20261006-0001의 /outbounds/1 및 /search?q=OUT-20261006-0001 조회','타센터 상세 거부·검색 제외','HTTP200으로 이천 출고 상세 및 주문·송장·배송 연결 정보 반환','cross-center-search.json'],
 ['DEF-002','ORD-009','치명','동일 주문 동시 할당 시 주문수량 초과','보유20/주문10; /orders/{seq}/allocate와 /orders/allocate-many 동시 요청 후 재시도','총 할당10 유지','총 할당20. 두 번 독립 재현','defect-http-evidence.json / db-evidence.jsonl'],
 ['DEF-003','ORD-008','높음','재고 경쟁 할당에서 HTTP500 발생','가용10에 수량8 주문2건을 서로 다른 세션에서 동시에 할당','경합을 제어하고 잔여수량에 맞게 부분할당 또는 명확한 업무오류 반환','한 요청 HTTP500 INTERNAL_ERROR. 두 번 독립 재현','defect-http-evidence.json'],
 ['DEF-004','ORD-011','높음','피킹 진행 중 주문 취소와 할당 해제 허용','10개 할당·출고지시·피킹 완료 후 /orders/{seq}/cancel','진행 중 출고의 취소 차단 또는 일관된 역처리','HTTP200 CANCELED. 출고 피킹 상태와 주문 취소 상태가 어긋남','defect-http-evidence.json / defect-db.json'],
 ['DEF-005','OUT-005','치명','피킹 시 할당과 무관한 재고 ID 허용','정상 lineSeq/allocSeq에 다른 SKU 재고 stockSeq=1로 피킹1 요청','SKU·위치·할당 관계 검증 후 거부','HTTP200으로 피킹실적 저장. 타지시 lineSeq는 거부되나 다른 재고는 허용','defect-http-evidence.json / defect-db.json'],
 ['DEF-006','PAC-004','높음','유효 송장 박스 다시담기 허용','ISSUED 송장이 있는 CLOSED 박스에 /outbounds/boxes/1/reopen','송장 취소 선행 또는 다시담기 거부','HTTP200 OPEN. E2E 계속 진행을 위해 해당 박스를 다시 닫음','defect-http-evidence.json'],
 ['DEF-007','OPS-003,OPS-004,OPS-005','높음','신규 설치 데모 업무역할의 알림 읽기 권한 누락','신규 DB V1–V38 + V900–V919; 센터관리자 알림 읽음 또는 재고담당 알림목록 조회','수신 대상 역할이 알림 조회·읽음 처리 가능','SYS_NOTIFICATION/R 누락으로 HTTP403. 감지·중복방지·해결 닫힘은 별도 통과','defect-http-evidence.json / db-evidence.jsonl'],
 ['DEF-008','DLV-001','높음','신규 등록 택배사로 송장 발급 불가','택배사 관리에서 신규 코드 등록 후 해당 코드로 박스 송장 발급','등록한 사용중 택배사로 송장 발급 가능','택배사 등록 성공 후 공통코드 검증에서 HTTP400. 허용값은 기존 CJ/HANJIN 등만','defect-http-evidence.json'],
 ['DEF-009','PAC-014,E2E-021','높음','수량체인 전체 보기에서 정상 문서 조회 불가','수량체인에서 OUT-20261006-0001 입력·전체 보기 선택·조회','정상 출고10의 지시/피킹/검수/포장/출고 수량 표시','UI는 brokenOnly를 누락하여 서버 기본 Y 적용: 조회0. brokenOnly=N 직접 API는 정상1행. 수량은 맞지만 화면 조회 실패','chain-filter-evidence.json / e2e-chain.png'],
];
const assoc={};for(const d of defs)for(const id of d[1].split(','))assoc[id]=d[0];
for(const [id,r] of Object.entries(results)){r.defect=assoc[id]||'';r.method='API·DB 자동검증; 브라우저는 별도 화면 로딩 점검. 실제 장비/외부 서비스 미사용.';if(r.status==='실패'){const d=defs.find(d=>d[0]===r.defect);r.note=d[6]+'\n재현: '+d[4]+'\n증빙: '+d[7];}}
const captured={
 orders:snap('실패 주문·출고 상태',"select o.order_seq,o.order_no,o.order_status,l.line_seq,l.order_qty,x.outbound_seq,x.outbound_status from tb_order o join tb_order_line l using(order_seq) left join tb_outbound_line ol on ol.order_line_seq=l.line_seq left join tb_outbound x on x.outbound_seq=ol.outbound_seq where o.order_seq in (13,14,15,35,36,37,38)"),
 migrations:snap('실행 마이그레이션',"select version,description,success from flyway_schema_history order by installed_rank"),
};
fs.writeFileSync(dir+'/defect-db.json',JSON.stringify(captured,null,2));
const counts=Object.values(results).reduce((a,r)=>(a[r.status]=(a[r.status]||0)+1,a),{});
const meta=[
 ['검증 기준','2026-10-06 작업트리 / 기준 커밋 2a89acb + 기존 미커밋 수정 / DB V38 및 데모 V900–V919'],
 ['결과',`131개: 통과 ${counts['통과']} / 실패 ${counts['실패']} / 해당없음 ${counts['해당없음']}; 실제 결함 9건. 배포 적합 판정 아님.`],
 ['E2E','업무처리20단계 통과, 최종대사 화면 단계 실패(DEF-009). API·DB 수량은 0 + 입고97 - 정정5 - 출고10 = 최종82; 할당0; 박스2·송장2 배송완료.'],
 ['수행 방식','업무 API 실제 호출 + PostgreSQL 조회 대조. 별도 Edge 브라우저 로그인과 64개 화면 로딩 점검. 각 시나리오의 모든 버튼·인쇄 동작을 UI로 수행한 결과는 아님.'],
 ['서버','격리 backend 127.0.0.1:18080 / frontend 127.0.0.1:15173 / PostgreSQL 127.0.0.1:55432 / fulfillment_it'],
 ['데이터 보호','기존 8080 앱 및 기존 업무 DB 대신 새 PostgreSQL 클러스터 사용. 애플리케이션 소스 수정·커밋 없음.'],
 ['빌드 검증','Gradle offline bootJar test 성공. 기존 컨텍스트 테스트 통과. 본 통합검증은 docs/tools/integration-*.mjs로 별도 실행.'],
 ['배치','기본 autoConfirm/autoAlloc/allocRetry=false; 알림·대사·휴면 스케줄 비활성. E2E 자동확정·할당만 별도 인스턴스에서 2초 cron으로 실제 수행 후 종료.'],
 ['시간 대체','로그인 잠금 대기는 격리DB locked_until 경과로, 배송 지연은 handed_over_at을 8일 전으로 바꿔 검증. 실제 장시간 대기는 미수행.'],
 ['오류 주입','PAC-011 롤백 확인은 격리DB CHECK를 일시 제거한 비정상재고로 수행 후 데이터·제약 복원. INV-011은 보유+1 오류를 주입하여 원장차이 검출 후 즉시 원복.'],
 ['장비·외부 연동','스캔은 바코드 문자열 API 입력. 물리 프린터·스캐너 품질, 실제 택배사/외부채널 통신 미검증. 배송은 시스템 내 수동 상태입력.'],
 ['시나리오 보정','입고계획 생성·택배 인계는 센터관리자 권한 사용. CS_VIEWER는 읽기전용 정책으로 변경 불가하여 배송 상태는 센터관리자 사용. SYS-007 내보내기는 현 시스템 미구현으로 해당없음.'],
 ['실패 처리','테스트 준비 오류는 원인을 수정 후 재검증. 최종 실패는 재현된 제품/초기 권한구성 문제만 포함. 실패13건은 중복 원인을 합쳐 결함9건으로 연결.'],
 ['대사 해석','STALE은 실사 이력 없음/90일 미실사 경고. clean=true와 병존할 수 있으며 원장 불일치로 계산하지 않음.'],
 ['증빙 시각','JSON 기록시각은 UTC(Z); 실행일·문서 일자는 한국시간 2026-10-06.'],
 ['로그','http-evidence.jsonl: 케이스·계정·요청·응답 / db-evidence.jsonl: 조회 SQL·결과 / browser-evidence.json: 화면별 실제 텍스트·JS 오류'],
 ['한계','단일 PC 환경·소수 동시 요청. 부하/성능, 실제 다중 서버 경합, 모든 가능한 입력 조합은 범위 밖.'],
 ['후속 조치','치명: 센터범위·중복할당·피킹 참조 검증 우선 수정. 다른 실패도 수정 후 해당 케이스와 E2E 재검증 필요.'],
];
const report={results,defs,meta,counts,state,output:'통합테스트_실행결과_20261006.xlsx',runSheet:'실행기록',notesTitle:'API·DB 통합테스트 실행 결과',date:'2026-10-06'};
fs.writeFileSync(dir+'/report-data.json',JSON.stringify(report,null,2));
fs.writeFileSync(dir+'/results.json',JSON.stringify(results,null,2));
console.log(JSON.stringify({counts,defects:defs.length}));
