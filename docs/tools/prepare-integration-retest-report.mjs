import fs from 'node:fs';
import {dir,root,results,state,snap,assert} from './integration-client.mjs';
import path from 'node:path';
assert(process.env.IT_RUN==='20261006-fix');
const original=JSON.parse(fs.readFileSync(path.join(root,'docs/test-results/20261006/report-data.json'),'utf8'));
const fixes={
 'DEF-001':'출고 상세/하위 작업을 기능별 데이터 범위로 검증. 통합검색 결과와 직접 chain 조회, 연결문서 각각의 권한·센터 범위 적용. 타센터 조회/수정 차단 및 자기센터 정상출고 통과.',
 'DEF-002':'주문 행 FOR UPDATE로 개별/일괄/자동 할당의 같은 주문 동시실행 직렬화. 같은 주문10의 동시 할당10회 반복 결과 모두 총할당10.',
 'DEF-003':'후보 재고를 stock_seq 순서로 잠근 뒤 최신 가용량 조회. rollback-only 예외를 삼키지 않음. 재고10에 주문8×2 동시 요청10회 모두 총할당10·HTTP500없음.',
 'DEF-004':'출고지시 생성과 할당·해제가 같은 주문 잠금 사용. 활성 출고가 있는 주문 전체/라인 취소 및 할당 해제 차단. 출고지시 취소 후 해제 가능. 주문 취소·할당 경합3회 통과.',
 'DEF-005':'피킹 allocSeq 필수. 주문줄·SKU·센터·stockSeq·allocSeq 관계와 할당별 수량 검사. 출고/할당 잠금으로 중복 요청 제어. 동일 빈의 할당6+4도 개별 피킹·되돌림·출고 정상.',
 'DEF-006':'ISSUED 송장이 있으면 박스 다시담기 거부. 부모 출고 잠금으로 송장발급/박스상태 변경 경합 제어. API waybillIssued 표시 및 UI 버튼 비활성. 송장 취소 후 다시담기 성공.',
 'DEF-007':'V39 기존 역할 알림 R 권한 보완, V920 신규 설치 데모역할 권한 보완. 신설 DB 및 V38/V919 기존DB 사본 업그레이드 모두 검증. 알림 읽음·센터범위·해결닫힘·중복방지 통과.',
 'DEF-008':'발급/재발행 시 COURIER 공통코드 대신 tb_courier 사용여부 검증. 신규 등록 택배사 발급 성공, 중지 택배사 거부, 재활성화 후 발급 성공.',
 'DEF-009':'수량체인 전체 보기에서 brokenOnly=N 명시 전송. 실제 브라우저에서 정상 E2E 행 표시·전 단계10·불일치0 확인.'
};
const assoc={};for(const d of original.defs)for(const id of d[1].split(','))assoc[id]=d[0];
for(const [id,r] of Object.entries(results)){r.defect=assoc[id]||'';r.method='API·DB 재검증; 화면 로딩64개 및 전체 보기 실제 UI 확인. 물리 장비·외부연동 제외.';}
assert.equal(Object.keys(results).length,131);
assert.equal(Object.values(results).filter(r=>r.status==='통과').length,130);
assert.equal(results['SYS-007'].status,'해당없음');
const count={통과:130,실패:0,차단:0,해당없음:1};
const meta=[
 ['검증 기준','2026-10-06 수정 작업트리 / 기준커밋 2a89acb + 기존 수정 보존 / DB V39 + 데모 V920'],
 ['최종 결과','131개: 통과130 / 실패0 / 차단0 / 해당없음1. 기존 결함9건 모두 수정 후 재검증 통과.'],
 ['E2E','21단계 통과. 입고97 - 정정5 - 출고10 = 최종보유82 / 할당0 / 2박스 배송완료. 수량체인 전체 보기 UI도 정상.'],
 ['수행 방식','실제 API 호출·DB 상태 대조, Edge 64개 화면 로딩 및 정상 출고 전체 보기 화면 검증. 모든 업무 클릭을 UI로 자동화한 결과는 아님.'],
 ['환경','127.0.0.1:18080 / PostgreSQL 127.0.0.1:55432 / 새 DB fulfillment_fix. 기존 실패 증빙은 20261006 폴더에 별도 보존.'],
 ['기존 DB 업그레이드','수정 전 테스트DB 사본 fulfillment_upgrade에서 V39/V920 적용 및 알림 권한 복구 확인. 실제 업무 DB는 테스트 대상으로 사용하지 않음.'],
 ['빌드','Gradle offline bootJar test 통과 / npm.cmd run build 통과. 최종 빌드 후 피킹·패킹·출고 관련 업무 다시 검증.'],
 ['배치','기본 자동배치 비활성. E2E 자동확정/할당만 별도 18082 서버의 2초 cron으로 실제 수행 후 종료.'],
 ['추가 동시성','가용10·주문8×2 동시 할당10회, 동일 주문10 개별/일괄 동시할당10회. 총할당 정상·HTTP500없음. 주문 취소/할당 경합3회, 같은 피킹 동시요청도 검증.'],
 ['시간/장애 대체','잠금시간 및 인계시각은 격리DB에서 경과시각으로 조정. 출고 롤백/대사 오류는 격리DB에서 오류 주입 후 데이터·제약 원복.'],
 ['장비·외부 범위','바코드 문자열 스캔·라벨 PDF 미리보기 확인. 물리 프린터/스캐너 성능, 실제 택배/외부채널 통신 미검증.'],
 ['시나리오 적용','SYS-007 재고/이력 엑셀 내보내기는 현재 미구현으로 해당없음 유지. 역할은 현재 권한에 맞춰 센터관리자/작업자로 구분 수행.'],
 ['결함관리','결함관리 시트의 실제 결과는 수정 전 재현 현상을 보존. 상태 FIXED, 재검증 PASS 및 비고에 수정내용 기록.'],
 ['증빙','results.json / http-evidence.jsonl / db-evidence.jsonl / fix-regressions.json / multiple-allocations.json / chain-filter-evidence.json / browser-evidence.json / upgrade-check.json'],
 ['사용 주의','재검증 스크립트는 전용 테스트 계정과 DB를 전제로 함. IT_RUN=20261006-fix, IT_DB=fulfillment_fix를 지정하며 운영 DB에 실행하지 않음.'],
 ['작업 상태','애플리케이션 수정은 로컬 작업트리에 반영. 기존 사용자의 변경을 보존. 커밋/배포는 수행하지 않음.'],
];
const migrations=snap('재검증 마이그레이션',"select version,description,success from flyway_schema_history where version in ('38','39','919','920') order by installed_rank");
const report={...original,results,state,counts:count,meta,fixes,retest:true,output:'통합테스트_수정후_재검증_20261006.xlsx',notesTitle:'9개 결함 수정 후 통합테스트 재검증',date:'2026-10-06'};
fs.writeFileSync(dir+'/report-data.json',JSON.stringify(report,null,2));
fs.writeFileSync(dir+'/results.json',JSON.stringify(results,null,2));
console.log(JSON.stringify({cases:131,passed:130,failed:0,notApplicable:1,migrations}));
