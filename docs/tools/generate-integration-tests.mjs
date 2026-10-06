import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateRawSync } from 'node:zlib';
import assert from 'node:assert/strict';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const out = path.join(root, 'docs/통합테스트_시나리오_V37.xlsx');
const java = 'backend/src/main/java/com/fulfillment/';
const sources = {
 AUTH: java+'system/auth/service/AuthService.java',
 MASTER: 'frontend/src/modules/master/routes.js',
 SYSTEM: 'frontend/src/modules/system/routes.js',
 PR: java+'purchase/request/service/RequestService.java',
 PO: java+'purchase/order/service/OrderService.java',
 IN: java+'inbound/plan/service/InboundService.java',
 RECEIPT: java+'inbound/receipt/service/ReceiptService.java',
 CORRECT: java+'inbound/correct/service/CorrectService.java',
 STOCK: java+'inventory/stock/service/StockLedger.java',
 TAKE: java+'inventory/stocktake/service/StocktakeService.java',
 ORDER: java+'order/service/SalesOrderService.java',
 ALLOC: java+'order/service/AllocationService.java',
 BATCH: java+'order/service/AllocationBatch.java',
 CONFIRM: java+'order/service/ConfirmBatch.java',
 UNMAP: java+'order/service/UnmappedOrderService.java',
 UPLOAD: java+'common/upload/targets/OrderUploadTarget.java',
 OUT: java+'outbound/service/OutboundService.java',
 DELIVERY: java+'delivery/track/service/DeliveryService.java',
 COURIER: java+'delivery/courier/service/CourierService.java',
 NOTI: java+'system/notification/service/NotificationService.java',
 SCHEMA: 'docs/erd.html',
};
Object.values(sources).forEach(p=>assert(fs.existsSync(path.join(root,p)),p));
const headers = ['시나리오 ID','업무영역','우선순위','유형','시나리오 / 검증 목적','화면 / 경로','수행 역할','선행조건 / 테스트 데이터','수행 절차','기대 결과 / 합격 기준','DB·연계 확인','근거 코드','실행 결과','실제 결과 / 증빙','결함 ID','담당자','실행일','비고 / 재실행 조건'];
const cases=[];
function group(area, role, src, lines) {
 for(const raw of lines.trim().split('\n')) {
  const p=raw.split('|'); assert.equal(p.length,8,raw);
  const [id,priority,type,title,route,pre,steps,expected]=p;
  cases.push([id,area,priority,type,title,route,role,pre,steps.replaceAll(' → ','\n→ '),expected,'화면 결과와 연결 문서·수량을 비교. 실패 시 업무 데이터가 일부만 반영되지 않았는지 확인.',sources[src],'미실행','','','','','독립 데이터 사용. 시도 전후 문서번호·수량·시각을 증빙에 기록']);
 }
}
group('공통·권한','시스템관리자 / 제한 권한 사용자','AUTH',`
COM-001|P0|정상|로그인과 역할 메뉴 연계|/login → /users|사용 중인 관리자·입고담당 계정 준비|각 계정 로그인 → 사용자와 권한 메뉴 확인 → 업무 화면 진입|계정별 권한에 맞는 접근 결과. 인증 응답에 비밀번호·해시 없음
COM-002|P0|예외|비밀번호 오류·단계별 잠금|/login|전용 테스트 계정; 현재 잠금 정책 임계값 기록|틀린 비밀번호를 정책 횟수만큼 입력 → 시한 잠금·영구 잠금 분기 확인 → 관리자가 해제|정책에 맞게 로그인 차단; 실패 누적과 잠금 상태 저장; 해제 후 정상 로그인
COM-003|P0|권한|세션 종료 후 API 접근 차단|/login|로그인 세션과 보호 API 준비|로그아웃 → 뒤로가기 → 같은 세션으로 보호 API 재호출|서버에서 인증 거부; 업무 데이터 조회·변경 불가
COM-004|P0|권한|센터별 목록·상세 데이터 격리|/stocks; /inbounds; /outbounds|A센터·B센터 문서 각 1건; B센터 제한 계정|B센터 계정으로 조회 → A센터 문서 ID를 상세 API에 직접 입력|권한 밖 데이터 조회·처리 차단; 목록과 상세 범위 일치
COM-005|P0|권한|조회권한으로 승인 API 우회 불가|/inbound-putaway; /stock-adjust-approve|조회·작업 권한만 있고 승인 A 없는 계정|승인 버튼 접근 → 승인 API 직접 호출 → 관리자 승인|미권한 요청은 거부되고 수량 불변; 관리자만 정상 승인
COM-006|P1|정상|사용중지 사용자 로그인 통제|/users → /login|업무 이력이 있는 전용 계정|관리자가 사용중지 → 새 로그인 시도 → 기존 문서 작성자 조회|로그인 거부; 기존 업무 이력과 작성자 정보 유지
COM-007|P1|정상|비밀번호 변경|/password|전용 계정 로그인|현재 암호 오류·신규 확인 불일치 입력 → 정상 변경 → 재로그인|오입력 거부; 변경한 암호로 로그인; 이전 암호로 새 로그인 실패
`);
group('공통·운영','시스템관리자 / 기준정보 담당 / 감사 담당','SYSTEM',`
SYS-001|P1|정상|역할·메뉴·권한 연결|/roles; /permissions; /role-permissions; /menus|전용 역할·사용자·메뉴 준비|역할에 조회 액션 부여 → 사용자 연결 → 재로그인 → 회수 후 재확인|부여한 범위의 메뉴/API만 접근; 회수 후 재로그인하면 접근 차단
SYS-002|P1|정상|회사·조직·데이터 범위 연결|/companies; /orgs; /roles|테스트 회사와 센터 조직|회사·조직 등록 → 사용자 소속·역할 조직범위 연결 → 재로그인|선택한 조직범위에 맞는 업무 데이터만 조회
SYS-003|P1|정상|감사로그의 업무 추적|/audit-logs|등록·승인·취소한 테스트 전표|전표번호·작업자·기간으로 조회 → 상세 확인|주요 업무 행위의 처리자·시각·대상이 추적 가능; 비밀번호 미노출
SYS-004|P1|예외|기준정보 업로드 부분 오류|/uploads|대상 화면에서 받은 템플릿; 정상행·필수누락행 혼합|업로드 → 성공·실패 건수 확인 → 오류 파일 다운로드 → 수정 재업로드|오류 행과 사유 식별 가능; 정상 반영·재처리 결과가 업로드 이력과 일치
SYS-005|P1|정상|공통코드·사유코드 화면 분리|/codes; /reasons|기준정보 담당 계정; REASON_CORRECT|사유코드에서 입고정정 사유 조회·수정 → 정정 화면 선택값 확인|입고정정 사유가 사유코드에서 관리되고 업무 화면에 반영; 공통코드 권한 우회 없음
SYS-006|P1|정상|정책 변경과 업무 판정 연계|/policies|전용 테스트 정책; 기존값 기록|변경 가능한 정책 1개 변경 → 관련 업무 경계값 테스트 → 원복|저장한 정책값에 맞는 허용·경고·거부 결과; 변경 이력 확인
SYS-007|P1|권한|엑셀 내보내기와 조회 범위 일치|/stocks; /stock-history|센터별 재고; 제한 계정; 필터 조건|센터·SKU 필터 조회 → 제공되는 내보내기 실행 → 파일 비교|파일 수량·대상과 필터 범위 일치; 권한 밖 데이터 포함 안 됨
`);
group('기준정보','기준정보 담당','MASTER',`
MST-001|P0|정상|센터·창고·빈에서 입고까지 연결|/plants; /warehouses; /locations|DATA-01 센터·창고·빈 계획|센터·창고·빈 등록 → 입고 적치 대상에서 선택|계층이 일치하는 위치만 선택; 저장한 코드와 적치 위치 일치
MST-002|P1|예외|빈 코드와 바코드 중복 규칙|/locations|동일 창고 2개 빈 및 다른 창고 준비|같은 창고에 동일 빈코드 등록 → 다른 창고에 같은 코드 → 전역 중복 바코드 등록|같은 창고 빈코드는 거부; 다른 창고 동일 코드는 허용; 바코드 중복은 거부
MST-003|P1|정상|제품·SKU 생성과 스캔 연결|/categories; /brands; /products; /skus|분류·브랜드와 제품 1종|분류·브랜드 → 제품 → SKU·바코드 등록 → 입고 스캔|등록 SKU가 업무 선택기와 바코드 스캔에서 같은 품목으로 식별
MST-004|P1|재실행|SKU 색상·사이즈 일괄생성|/sku-bulk|제품 1개; 색상 2개×사이즈 2개|4조합 생성 → 동일 조합 재생성 → SKU 목록 조회|첫 실행 4조합 생성; 재실행 시 기존 조합이 중복 생성되지 않음
MST-005|P0|정상|채널 SKU 매핑과 주문 수신|/channels; /channel-skus; /orders|DATA-02 채널·외부 SKU·내부 SKU|채널 등록 → SKU 매핑 → 해당 외부 SKU 주문 등록|주문 줄이 매핑된 내부 SKU로 연결되어 후속 확정·할당 가능
MST-006|P1|정상|공급처 정보와 발주·입고 연결|/partners; /purchase-orders; /inbounds|공급처·주소·허용오차 등록|공급처 등록 → 발주 선택 → 구매입고 연결|공급처 정보와 문서 연결 일치; 허용오차 정책이 검수 판정에 사용
MST-007|P1|정상|로케이션 라벨과 스캔 일치|/label-print; /stocktake|서로 다른 창고의 동명 빈; 각 바코드|라벨 출력 → 표시 바코드를 스캔 → 실사·적치 위치 확인|라벨이 지칭하는 정확한 창고·빈 식별; 다른 위치로 잘못 연결되지 않음
`);
group('구매','재고 담당 → 구매 담당','PO',`
PUR-001|P0|정상|요청·부분승인·발주 연결|/purchase-requests; /purchase-request-approve; /purchase-orders|요청120·승인100; DATA-01|구매요청120 → 구매담당 승인100 → 승인 건으로 발주 작성|요청120 보존; 승인100과 발주 근거 연결; 승인 범위 내 발주
PUR-002|P1|예외|미승인 요청 발주 방지|/purchase-orders|승인 전 구매요청|발주에서 미승인 요청 선택 시도 → 직접 API 요청|미승인 요청으로 유효 발주가 생성되지 않음
PUR-003|P0|정상|발주확정과 수정 통제|/purchase-orders; /purchase-progress|DRAFT 발주100|발주 확정 → 수량 변경 시도 → 진행현황 조회|확정 후 임의 수량 변경 차단; 발주100·미입고100 확인
PUR-004|P1|정상|발주 금액 스냅샷|/purchase-orders; /partners|발주 단가1000·수량10|발주 확정 → 기준 단가 변경 → 기존 발주 조회|기존 발주 단가1000·금액10000 유지
PUR-005|P0|예외|입고예정 가능한 발주만 선택|/inbounds|DRAFT·확정·취소·잔량0 발주 각각|발주 선택기 조회 → 불가 발주 번호 직접 불러오기|확정된 입고 가능 잔량 건만 선택 가능; 불가 발주는 서버에서도 거부
PUR-006|P1|정상|발주 미납 잔량 마감|/purchase-progress|부분입고 완료 발주; 남은8; 마감 권한·사유|잔량 마감 실행 → 발주 진행현황 → 신규 입고예정 조회|잔량 마감 이력·사유 보존; 마감분을 추가 예정으로 중복 생성하지 못함
`);
group('입고','입고 작업자 → 센터 관리자','RECEIPT',`
INB-001|P0|정상|구매입고 예정과 발주 잔량 예약|/inbounds|확정 발주100; 예정 없음|발주 불러오기 → 예정60 등록 → 다음 예정 가능수량 확인|동일 발주의 예정 중복을 제외한40만 추가 예정 가능
INB-002|P0|예외|중복 입고예정 초과 차단|/inbounds|발주100·기존 예정60|새 예정41 등록 → 40으로 수정 등록|41은 거부; 40은 허용; 총 예정100 이내
INB-003|P0|정상|직납입고 전 과정|/inbounds; /inbound-arrive; /inbound-inspect; /inbound-putaway|발주 없는 DIRECT 입고10; 별도 SKU|직납입고 등록 → 입하 → 검수10 → 적치10 → 관리자 완료|발주 없이 완료 가능; 재고10 증가; 구매발주 수량에 잘못 반영되지 않음
INB-004|P1|예외|구매입고 발주 필수|/inbounds|PURCHASE 타입·발주 미선택|구매입고 저장 → 직접 API 입력 검증|발주 없는 구매입고 거부; 전표·라인 일부 생성 없음
INB-005|P1|정상|입하 박스·파렛트 수 저장|/inbound-arrive|예정100; 박스10·파렛트2|입하 등록에 차량·기사·박스10·파렛트2 입력 → 재조회|두 포장단위 수 저장; SKU 예정수량100 보존; 재고 미증가
INB-006|P1|경계|입하 포장수량 NULL·0·음수|/inbound-arrive|서로 다른 예정 3건|박스·파렛트 수를 비움·0·-1로 각각 입력|비움과0은 허용; 음수 거부; 오류 건 입하 데이터 부분 저장 없음
INB-007|P0|정상|분할 검수와 합격·거부 누적|/inbound-inspect|예정·입하100|검수60 합격 → 추가37 합격·3 거부 사유 파손 → 이력 조회|검수2회 보존; 합격97·거부3; 예정100 유지; 아직 보유재고 미증가
INB-008|P0|예외|검수 거부 사유 필수|/inbound-inspect|입하 완료·거부수량3|거부 사유 없이 검수 저장 → 사유 입력 후 저장|사유 누락은 거부; 정상 재입력 1회만 반영
INB-009|P1|경계|초과입고 정책과 승인|/inbound-inspect; /inbound-approve|공급처 허용오차·초과승인 정책값 기록|허용 경계와 초과량으로 검수 → 필요 시 관리자 초과 승인 → 적치|현재 정책에 맞는 경고·보류·승인 적용; 승인 전 후속 처리 통제
INB-010|P0|정상|분할 적치와 완료 시점 재고 반영|/inbound-putaway; /stocks|합격97; 빈A·B|A에60·B에37 적치 → 재고 확인 → 관리자 완료|완료 전 미증가; 완료 후 A60·B37 총97 증가; 재고이력과 동일
INB-011|P0|예외|잘못된 SKU·빈 스캔|/inbound-putaway|적치대상 SKU-A; 다른 SKU·다른 센터 빈|다른 SKU 스캔 → 다른 센터 빈 스캔 → 올바른 값으로 적치|오스캔·범위 밖 위치 거부; 잘못된 적치·재고 생성 없음
INB-012|P0|예외|합격수량 초과 적치|/inbound-putaway|합격10·기적치7|4개 추가 적치 → 3개로 수정|초과4는 거부; 3만 저장; 총 적치10
INB-013|P0|재실행|입고완료 중복 요청|/inbound-putaway|완료 가능한 입고10|완료 → 같은 완료 요청 재전송 → 재고·원장 조회|재고는10만 증가; 중복 원장·중복 승인 반영 없음
INB-014|P1|예외|입하 후 예정 취소 차단|/inbounds|입하 완료 전표|취소 시도 → 상태·검수대상 조회|취소 거부; 입하 이력과 후속 검수대상 보존
INB-015|P0|정상|입고정정과 발주·재고 연동|/inbound-corrects; /inbound-correct-approve|발주100·합격97·완료97; 다른 작업 없음|원 적치자리에서 덜 받음5 정정 요청 → 관리자 승인|재고92·입고 누계92·발주 잔량8; 원장-5와 정정 근거 연결
INB-016|P0|예외|정정 승인 실패 원자성|/inbound-correct-approve|완료입고; 차감 가능한 수량보다 큰 정정|승인 전 재고·입고·발주 수량 기록 → 초과 차감 승인 시도|불가 차감 거부; 세 문서 수량 및 원장이 모두 기존 상태 유지
`);
group('재고','재고 담당 → 센터 관리자','STOCK',`
INV-001|P0|정상|판매불가 수량과 가용재고|/stock-unsellable; /stocks|보유20·할당0·판매불가0|5개 판매불가 전환 → 현황 조회 → 2개 해제|보유20 유지; 판매불가5→3; 가용15→17; 사유·이력 확인
INV-002|P0|정상|빈간 이동 수량 보존|/stock-move; /stocks; /stock-history|A빈20·B빈0; 할당 없는 재고|A→B 7개 이동 → 양쪽 현황·이력 확인|A13·B7·합계20; 출발/도착 이동이력과 수량 일치
INV-003|P0|예외|이동·차감 시 가용수량 보호|/stock-move; /stock-unsellable|보유10·할당8|허용 가용량을 넘는 이동·판매불가 전환 요청|할당분 침범·음수 가용 발생 거부; 기존 할당 보존
INV-004|P0|정상|재고조정 요청·승인 분리|/stock-adjust; /stock-adjust-approve|보유20; -3 조정요청|요청자 승인 시도 → 다른 승인자 승인 → 원장 조회|본인 승인 차단; 다른 승인 후 보유17; 조정 원장1회
INV-005|P1|정상|조정 승인 전 재고 변동|/stock-adjust-approve|보유20에서 -3 요청; 승인 전 다른 정상거래+5|재고25 확인 → -3 조정 승인|변동량 기준으로22 반영; 요청시점과 달라진 재고 경고 확인
INV-006|P1|정상|조정 반려와 수량 불변|/stock-adjust-approve|미승인 조정요청|반려 사유 입력 → 반려 → 재고·원장 확인|반려 상태·사유 저장; 재고와 수량 원장 변화 없음
INV-007|P0|정상|실사 대상·스캔·차이 반영|/stocktake; /stocks|보유20 재고; 실사 권한 분리|계획 생성 → 대상 담기 → 시작 → 실물18 입력 → 마감·승인 절차 수행|차이-2가 승인 시 한 번 반영; 최종18; 실사·원장 근거 연결
INV-008|P1|예외|실사 대상0 시작 차단|/stocktake|재고 없는 전용 창고|계획 → 대상 생성 → 시작 시도|대상0 안내; 빈 실사를 정상 진행중으로 만들지 못함
INV-009|P1|정상|실사 대상 비우기와 재생성|/stocktake|시작 전 대상이 있는 계획|대상 비우기 → 조건 변경 → 다시 대상 담기|기존 대상 제거; 새 조건의 대상만 남음; 재고수량 불변
INV-010|P1|정상|실사 라벨 스캔 식별|/stocktake; /label-print|실사대상 SKU·빈 라벨|SKU ID·바코드와 빈 라벨로 스캔 → 대상 줄 비교|지원 식별값이 같은 실제 대상을 지목; 다른 품목·빈 실적으로 저장 안 됨
INV-011|P0|정합성|재고 원장 대사|/stock-recon; /stock-history|입고·이동·조정·출고 수행 데이터|대사 실행 → 차이 건 조회 → 관련 원장 합산|정상 흐름 차이0; 차이 발생 시 재고·원장·근거문서 식별 가능
`);
group('주문·할당','주문 담당 / 자동 배치','ORDER',`
ORD-001|P0|정상|주문 일괄등록과 그룹 처리|/orders; /uploads|현재 주문 템플릿; 동일 주문번호 2줄|두 SKU 주문 업로드 → 주문 상세·이력 조회|주문1건에 라인2개 연결; 주문 합계·수량이 입력과 일치
ORD-002|P0|예외|주문 중복 수신|/orders; /uploads|이미 등록된 채널·외부 주문번호|동일 입력 재업로드 → 주문·라인 건수 확인|중복 주문·중복 라인이 생기지 않음; 중복 사유를 확인 가능
ORD-003|P0|정상|미매핑 주문 오류대기·재처리|/orders/errors; /channel-skus|미매핑 외부 SKU 주문|주문 등록 → 오류대기 확인 → SKU 매핑 → 재처리|미매핑 동안 확정·할당 안 됨; 재처리 후 내부 SKU 연결·후속 처리 가능
ORD-004|P0|정상|주문 자동확정 배치|/orders|정상 RECEIVED 주문·미매핑 주문·취소 주문; autoConfirm 활성|배치 실행주기 대기 → 각 주문 상태·처리자 확인|정상 대상만 확정; 미매핑·취소는 제외; system 처리 이력 확인
ORD-005|P0|정상|재고할당과 보유수량 보존|/orders/allocations; /stocks|보유20·판매불가0·할당0; 확정주문10|주문10 할당 → 주문·재고·할당내역 확인|보유20 유지; 할당10·가용10; 주문·재고 할당 합계 일치
ORD-006|P0|경계|재고 부족 부분할당|/orders/allocations; /orders/shortages|가용6; 주문10|할당 실행 → 결품 화면·할당 내역 확인|할당6·미충족4로 구분; 보유 불변; 과할당 없음
ORD-007|P0|정상|추가입고 후 결품 재할당|/orders/shortages; /orders/allocations|ORD-006의 결품4; 정상 입고로4 추가|추가 입고완료 → 재처리 또는 allocRetry 배치 대기|기존6 유지하고4 추가 할당; 총10; 결품 해소
ORD-008|P0|동시성|한 재고에 동시 주문 할당|/orders/allocations|가용10·주문8 두 건; 자동배치 간섭 통제|두 세션에서 동시에 할당 → 양쪽 결과·재고 확인|전체 할당 합계10 이하; 부족분 명시; 음수 가용 없음
ORD-009|P0|재실행|수동·자동할당 중복 실행|/orders/allocations|확정 주문10·가용20|수동 할당과 배치 실행 겹치기 → 재실행|해당 주문 필요량 초과 할당 없음; 재고·주문·할당행 합계 일치
ORD-010|P0|정상|할당 후 주문취소와 할당 해제|/orders|할당10·출고지시 없는 주문|주문 취소 → 주문·할당·가용재고 조회|취소수량 반영; 해당 할당 해제; 보유 불변·가용 회복
ORD-011|P0|예외|출고 진행 주문 취소 통제|/orders; /outbounds|작업 진행 중 출고지시가 연결된 주문|주문 취소 시도 → 출고·재고·할당 비교|진행 중 출고를 무시한 취소·할당 해제가 차단됨
ORD-012|P1|정상|주문 배송지 변경 연계|/orders; /outbounds|배송지 변경 가능한 단계의 주문|배송지 수정 → 상세 조회 → 출고지시 생성 후 수취정보 확인|허용 단계에서 변경 저장; 후속 출고 수취정보와 일치
ORD-013|P1|예외|배치 일부 주문 실패 격리|/orders/allocations|정상주문·처리불가주문 혼합|일괄 할당 → 성공·실패 건수 확인|실패 건 때문에 성공 건이 롤백되지 않음; 건별 수량·사유 확인
`);
group('출고·피킹','출고 관리자 / 피킹 작업자','OUT',`
OUT-001|P0|정상|할당 주문의 출고지시 생성|/outbound-targets; /outbounds|주문10 전량할당; 생성 지시 없음|대상 선택 → 지시 생성 → 라인·주문·SKU 연결 확인|지시10 생성; 원 주문라인·SKU·센터 일치; 보유재고 불변
OUT-002|P0|재실행|출고지시 중복·초과 생성 방지|/outbound-targets|할당10·기지시10|동일 주문으로 지시 생성 재시도|추가 가용 지시수량 없으면 거부; 총 유효 지시10 유지
OUT-003|P1|정상|피킹 담당자 배정|/outbounds; /outbound-picking|작업 전 지시·실제 사용자|담당자 배정 → 작업목록 조회 → 첫 피킹|배정자·담당자·시각 저장; 배정만으로 피킹 실적 생성 안 됨; 첫 실적에 진행 반영
OUT-004|P0|정상|서로 다른 빈의 분할 피킹|/outbound-picking|지시10; A빈6·B빈4 할당|A6 스캔 피킹 → B4 스캔 피킹|피킹 실적2건; 라인 picked10·remain0; stock_seq·alloc_seq 연결; 보유 불변
OUT-005|P0|예외|잘못된 빈·SKU·타지시 라인|/outbound-picking|정상 지시와 다른 지시·SKU·빈|오스캔 → 타지시 line_seq로 API 요청|각 잘못된 요청 거부; 정상 라인의 누적수량·실적 변화 없음
OUT-006|P0|경계|피킹0·초과수량 차단|/outbound-picking|지시10·피킹7|수량0 → 추가4 → 추가3 요청|0·초과4 거부; 추가3 정상; 누적10
OUT-007|P0|정상|음수 실적으로 피킹 되돌리기|/outbound-picking|A빈에서5 피킹|A빈 -2 되돌리기 → 실적 이력·라인 확인|기존+5 보존·-2 추가; 순피킹3; remain 증가; 보유재고 불변
OUT-008|P0|예외|빈별 피킹 초과 되돌리기 방지|/outbound-picking|A빈 순피킹3·B빈2|A빈 -4 요청|A빈 실적보다 큰 되돌림 거부; 두 빈 실적과 라인 누계 불변
OUT-009|P0|정상|피킹 결품과 지시수량 보존|/outbound-shortages; /outbound-picking|지시10·피킹7|결품3·사유 입력 → 라인·결품목록 확인|지시10 보존; 피킹7·결품3·remain0; 결품사유 저장
OUT-010|P0|예외|피킹 결품 필수사유·총량 검증|/outbound-shortages|지시10·피킹7|사유 없이3 결품 → 사유와4 결품 요청|사유 누락 및 피킹+결품>지시 요청 거부; 기존 실적 불변
OUT-011|P0|정상|출고검수 스캔 누적|/outbound-inspect|피킹10 완료·검수0|화면 초기0 확인 → 6개 검수 → 4개 검수 → 재조회|검수 누계10; 피킹수량을 자동 복사하지 않고 실제 입력·스캔량 반영
OUT-012|P0|예외|검수수량 상한|/outbound-inspect|피킹7·검수0|검수8 요청 → 정상7 검수|피킹 초과 검수 거부; 정상7 저장
OUT-013|P0|예외|패킹 이후 피킹수량 변경 방지|/outbound-picking; /outbound-packing|피킹·검수 후 패킹 단계 지시|추가 피킹·되돌리기 요청|패킹된 내용과 어긋나는 실적 변경 거부
OUT-014|P1|정상|작업 전 지시취소|/outbounds; /outbound-targets|생성 직후·피킹 없는 지시|사유 입력 후 지시 취소 → 주문·출고대상·재고 확인|지시취소 이력 보존; 보유재고 불변; 주문취소와 구별; 재지시 가능수량 재계산
OUT-015|P0|예외|피킹 시작 후 지시취소 차단|/outbounds|피킹 실적이 있는 지시|지시 취소 요청|취소 거부; 기존 피킹·할당과 지시 상태 보존
`);
group('패킹·송장·출고확정','패킹 작업자 → 출고 승인자','OUT',`
PAC-001|P0|정상|여러 박스 분할 포장|/outbound-packing|피킹·검수10; 박스2개|1번 박스6·2번 박스4 담기 → 포장 완료|박스별6·4; 라인 총10; 두 박스 완료 시 패킹완료; 보유재고 불변
PAC-002|P0|예외|검수량 초과 포장 차단|/outbound-packing|검수10·기포장7|다른 박스에4 추가 → 3으로 수정|초과4 거부; 총포장10까지만 허용
PAC-003|P1|예외|빈 박스 포장완료 방지|/outbound-packing|내용물이 없는 열린 박스|포장 완료 시도 → 내용 추가 후 완료|빈 박스 완료 거부; 유효 내용이 있는 박스만 완료
PAC-004|P1|정상|박스 다시 담기와 송장 유무|/outbound-packing; /outbound-waybills|완료 박스 두 개; 하나만 유효송장 있음|각 박스 다시 담기 시도|송장 없는 박스는 허용; 유효송장 있는 박스 재개봉 거부
PAC-005|P0|정상|송장 발급과 박스 연결|/outbound-waybills|포장완료 박스; 사용 가능한 택배사|실제 테스트용 송장번호 입력 → 발급 → 조회|박스당 유효송장1개; 택배사·번호·발급자 저장
PAC-006|P0|예외|미완료 박스·중복 송장 방지|/outbound-waybills|열린 박스; 기존 택배사+송장번호|열린 박스 발급 → 기존 번호로 다른 박스 발급|두 요청 거부; 유효송장 중복 없음
PAC-007|P1|정상|송장 취소·재발행 추적|/outbound-waybills|출고 전 발급 송장|사유와 새번호로 재발행 → 이전·새 송장 조회|원 송장 취소·신규 발급; reissued_from 연결; 유효송장1개
PAC-008|P0|예외|송장 누락 출고확정 차단|/outbound-ship|포장완료 박스2개 중1개 송장 없음|출고확정 요청 → 재고·할당 확인|확정 거부; 보유·할당·원장 변화 없음
PAC-009|P0|정상|출고확정 시 재고·할당·주문 연결|/outbound-ship; /stocks; /stock-history|보유20·할당10; 피킹·포장·송장 완료10|승인자가 출고확정 → 빈별 재고·할당·주문 확인|보유10; 해당 할당10 해제; 출고이력-10; 지시 출고완료; 주문 상태 조건 충족 시 동기화
PAC-010|P0|재실행|출고확정 중복 차감 방지|/outbound-ship|PAC-009 완료 지시|출고확정 재요청 또는 두 세션 동시 요청|총 차감10 한 번; 중복 원장·음수 할당 없음
PAC-011|P0|예외|출고확정 실패 전체 롤백|/outbound-ship|검증 환경에서 차감 불가 상태의 지시; 승인 직전 수량 기록|출고확정 요청 → 모든 라인·재고·할당·원장 조회|한 라인 실패 시 지시 전체 수량 변경 롤백; 일부 출고완료 없음
PAC-012|P1|정상|택배 인계 스캔과 부분 실패|/outbound-ship|출고완료 송장·미출고 송장·없는 번호|세 번호를 함께 인계 스캔 → 결과 재조회|유효 출고 건만 인계; 나머지 실패사유 표시; 재고 추가 차감 없음
PAC-013|P1|재실행|인계 중복 스캔|/outbound-ship|이미 인계한 박스|동일 송장 재스캔|중복 인계로 재고·배송 실적 중복 반영 없음; 처리 결과 식별 가능
PAC-014|P0|정합성|출고 수량체인 대사|/outbound-chain|주문·할당·피킹·패킹·출고 정상10|문서번호로 체인 조회 → 각 단계 수량 대조|정상 건 불일치0; 분할·결품은 단계별 잔량과 일치; 근거 추적 가능
`);
group('배송','배송 담당','DELIVERY',`
DLV-001|P1|정상|택배사 기준정보와 송장 사용|/delivery-couriers; /outbound-waybills|전용 택배사 코드·계약·조회URL|택배사 등록 → 송장 발급 선택 → 배송조회 링크 확인|택배사 정보 연결; 조회URL의 송장번호 치환 정확
DLV-002|P0|정상|인계 후 배송상태·사건 이력|/delivery-track|출고·인계 완료한 테스트 송장|운송중 → 배송출발 → 배송완료; 발생시각 입력|상태와 사건 이력 보존; 완료시각 저장; 창고재고 추가 차감 없음
DLV-003|P0|예외|인계 전 배송중 변경 차단|/delivery-track|출고확정했지만 미인계인 송장|운송중으로 변경 요청|인계 누락 안내와 함께 거부; 잘못된 운송중 상태·사건 없음
DLV-004|P1|예외|미래 사건·실패사유 누락 차단|/delivery-track|인계 완료 송장|미래 시각 입력 → 실패 상태를 사유 없이 입력|두 요청 거부; 유효한 과거·현재 사건과 필수사유만 저장
DLV-005|P0|정상|배송실패 후 재배송 연결|/delivery-fail; /delivery-track|실패·사유가 기록된 송장|새 송장번호로 재배송 → 원·새 송장 상세 확인|원 송장 취소; 새 송장 redelivery_of 연결·READY 사건; 창고재고 재차감 없음
DLV-006|P1|예외|정상 송장 재배송·번호 재사용 차단|/delivery-fail|정상 운송중 송장; 과거 취소번호|정상 송장 재배송 → 실패송장에 취소된 번호 재사용|허용 상태가 아니거나 사용한 번호면 거부; 원 송장 불필요 취소 없음
DLV-007|P0|정합성|운송중 수량과 배송완료 제외|/delivery-transit; /delivery-track|미완료 배송 박스6·4; 동일 SKU|운송중10 확인 → 6개 박스 배송완료 → 재조회|미완료 수량4; 완료분 제외; 창고재고와 중복 합산되지 않음
DLV-008|P1|정상|배송지연 필터|/delivery-fail; /delivery-transit|지연 기준일 전·후 출고송장; 완료·미완료 혼합|설정한 지연일 기준 필터 → 전표·시각 확인|기준 경계에 맞는 미완료 건 표시; 완료건과 구분; 조건을 증빙에 기록
`);
group('통합운영','센터 관리자 / 구매 담당 / 제한 사용자','NOTI',`
OPS-001|P1|권한|통합검색과 원문 이동 범위|/search|구매·입고·주문·출고 문서; 센터 제한 계정|문서번호 검색 → 결과 상세 이동 → 권한 밖 번호 검색|허용 원문으로 이동; 범위 밖 정보가 검색으로 유출되지 않음
OPS-002|P0|권한|승인 작업함과 업무 승인 연계|/approval-box|구매요청·재고조정·입고정정 승인대기|승인권한 사용자 조회 → 원문 이동·승인 → 작업함 재조회|본인이 처리 가능한 대상 표시; 승인 후 대기목록 갱신; 미권한 승인 거부
OPS-003|P1|정상|알림 읽음과 업무해결 구분|/notifications|미매핑 또는 결품 미해결 알림; 알림 훑기 실행 권한|알림 읽음 → 배지·열린목록 확인 → 원인 업무 해결 → 지금 훑기 또는 알림 배치 실행|읽음은 배지만 갱신; 미해결 알림 유지; 업무 해결 후 훑기에서 닫힘
OPS-004|P1|권한|역할·센터별 알림 수신|/notifications|A센터·B센터의 서로 다른 역할 사용자|동일 시간 각 계정 알림함 조회|수신 역할·센터에 맞는 알림만 조회; 타센터 업무 알림 노출 없음
OPS-005|P0|정상|적치완료 후 입고완료 대기 알림|/notifications; /inbound-putaway|전량 적치했지만 미완료인 입고; 알림 훑기 실행 권한|지금 훑기 또는 알림 배치 실행 → 센터관리자 알림 확인 → 입고완료 → 다시 훑기 → 알림 재조회|입고완료 대기 알림으로 원문 추적; 완료 후 훑기에서 닫힘; 재고는 완료 때 1회 증가
OPS-006|P1|재실행|같은 미해결 업무 알림 중복 방지|/notifications|동일 결품·미매핑 업무가 남아 있음|동일 원인 재처리 또는 감지 반복 → 열린알림 조회|같은 종류·참조문서에 열린알림 중복 생성 없음; 해결 이력 보존
`);

// A single numerical flow, isolated from the independent exception cases.
const e2e=[];
const flow=`
사전준비|기준정보 담당|DATA-01·02 등록; 전용 SKU 재고0 확인|초기 보유0·할당0·판매불가0; 자동배치 설정 기록|MASTER|/skus; /stocks
구매요청|재고 담당|SKU-A 120개 구매요청|요청120 기록; 재고0|PR|/purchase-requests
부분승인|구매 담당|요청120 중100 승인|요청120 보존·승인100|PR|/purchase-request-approve
발주확정|구매 담당|승인100으로 발주 작성·확정|발주100·잔량100; 재고0|PO|/purchase-orders
구매입고 예정|입고 담당|발주를 불러와100 예정 등록|발주라인·입고라인 연결; 재고0|IN|/inbounds
입하|입고 담당|입하100; 박스10·파렛트2 기록|예정100 유지·포장단위 저장; 재고0|IN|/inbound-arrive
분할 검수|입고 담당|합격60 기록 후 합격37·거부3(파손) 기록|합격97·거부3; 검수 이력2회; 재고0|RECEIPT|/inbound-inspect
분할 적치|입고 담당|빈A60·빈B37 적치|적치97; 보유재고0|RECEIPT|/inbound-putaway
입고완료|센터 관리자|입고완료 승인|A60·B37·총보유97; 입고원장+97|RECEIPT|/inbound-putaway
입고정정|입고 담당 → 센터 관리자|A빈 덜 받음5 요청·승인|A55·B37·총보유92; 기입고92·발주잔량8; 원장-5|CORRECT|/inbound-corrects; /inbound-correct-approve
주문등록·확정|주문 담당 / 배치|매핑된 외부SKU-A 10개 주문 등록; 자동확정 대기|주문10 확정; 미매핑 없음; 보유92|ORDER|/orders
할당|주문 담당 / 배치|10개 할당; 실제 선택된 빈·할당번호 기록|보유92·할당10·가용82; 주문라인 할당10|ALLOC|/orders/allocations
출고지시|출고 관리자|해당 주문 지시10 생성·담당 배정|지시10; 주문라인·SKU·센터 연결; 보유92|OUT|/outbound-targets; /outbounds
피킹|피킹 작업자|할당된 실제 빈에서 합계10 스캔|라인 picked10·shortage0·remain0; 실적합10; 보유92|OUT|/outbound-picking
출고검수|출고 작업자|스캔·계수로 합계10 검수|inspected10; 보유92|OUT|/outbound-inspect
패킹|패킹 작업자|박스1에6·박스2에4; 모두 포장 완료|포장합10·박스2; 지시 PACKED; 보유92|OUT|/outbound-packing
송장|패킹 작업자|박스마다 서로 다른 테스트 송장 발급|유효송장2개·각 박스1개; 보유92|OUT|/outbound-waybills
출고확정|출고 승인자|출고확정 1회|보유82·해당할당0·가용82; 출고원장-10; 지시 SHIPPED|OUT|/outbound-ship
택배인계|출고 작업자|송장2개 인계 스캔|인계자·시각 기록; 보유82 유지|OUT|/outbound-ship
배송완료|배송 담당|두 송장 운송중 → 배송출발 → 배송완료|두 송장 완료·완료시각; 미완료 운송수량0; 보유82 유지|DELIVERY|/delivery-track; /delivery-transit
최종대사|감사 / 재고 담당|재고대사·수량체인·문서·원장 조회|0+97-5-10=82; 할당0; 관련 전표·감사 추적; 정상 대사차이0|STOCK|/stock-recon; /outbound-chain; /audit-logs
`;
flow.trim().split('\n').forEach((line,i)=>{
 const [title,role,steps,expected,src,route]=line.split('|');
 e2e.push([`E2E-${String(i+1).padStart(3,'0')}`,'종단간 흐름','P0','정상',title,route,role,i===0?'DATA-01·02 준비. 다른 테스트와 SKU·문서 분리.':`E2E-${String(i).padStart(3,'0')} 통과; 같은 문서·SKU를 이어서 사용`,steps,expected,'수량대사 시트와 비교; 문서번호·실제 stock_seq·alloc_seq 기록',sources[src],'미실행','','','','','중간 실패 시 이후 단계 차단으로 기록. 초기수량으로 재시작하거나 마지막 정상 단계부터 재개']);
});
// More precise source mapping for mixed areas.
for(const c of cases){
 if(c[0].startsWith('INB-') && Number(c[0].slice(4))<=6) c[11]=sources.IN;
 if(['INB-015','INB-016'].includes(c[0]))c[11]=sources.CORRECT;
 if(['INV-007','INV-008','INV-009','INV-010'].includes(c[0]))c[11]=sources.TAKE;
 if(['ORD-001','ORD-002'].includes(c[0]))c[11]=sources.UPLOAD;
 if(c[0]==='ORD-003')c[11]=sources.UNMAP;
 if(c[0]==='ORD-004')c[11]=sources.CONFIRM;
 if(['ORD-005','ORD-006','ORD-007','ORD-008','ORD-009'].includes(c[0]))c[11]=sources.ALLOC;
 if(c[0]==='ORD-013')c[11]=sources.BATCH;
 if(c[0]==='DLV-001')c[11]=sources.COURIER;
 if(['PUR-001','PUR-002'].includes(c[0]))c[11]=sources.PR;
 if(['INV-004','INV-005','INV-006'].includes(c[0]))c[11]=java+'inventory/adjust/service/AdjustService.java';
 if(c[0]==='SYS-004')c[11]=java+'common/upload/UploadService.java';
 if(c[0]==='OPS-001')c[11]=java+'system/search/service/SearchService.java';
 if(c[0]==='OPS-002')c[11]=java+'system/approval/service/ApprovalBoxService.java';
 if(['OPS-003','OPS-005','OPS-006'].includes(c[0]))c[11]=java+'system/notification/service/NotifyBatch.java';
 if(['COM-004','COM-005'].includes(c[0]))c[11]='backend/src/main/java/com/fulfillment/common/security/PermissionChecker.java';
 const tables={ '공통·권한':'tb_user / tb_role_permission / tb_role_org_scope / tb_audit_log', '공통·운영':'tb_menu / tb_permission_action / tb_upload_history / tb_upload_error / tb_audit_log', '기준정보':'tb_plant / tb_warehouse / tb_location / tb_product / tb_sku / tb_channel_sku / tb_partner', '구매':'tb_purchase_request_line / tb_purchase_order_line', '입고':'tb_inbound_line / tb_inbound_inspect / tb_inbound_putaway / tb_inbound_correct_line / tb_stock_history', '재고':'tb_stock / tb_stock_history / tb_stock_adjust_line / tb_stocktake_line', '주문·할당':'tb_order / tb_order_line / tb_stock_alloc / tb_stock', '출고·피킹':'tb_outbound / tb_outbound_line / tb_outbound_pick / tb_stock_alloc', '패킹·송장·출고확정':'tb_pack_box / tb_pack_box_line / tb_waybill / tb_stock / tb_stock_history', '배송':'tb_waybill / tb_delivery_event / tb_pack_box_line', '통합운영':'tb_notification / tb_notification_read / 원문 업무 테이블'};
 c[10]=tables[c[1]]+'\n해당 행의 문서번호로 조회; 성공 시 기대 수량·상태, 거부 시 업무수량 불변 확인.';
}
const all=[...e2e,...cases];
assert.equal(new Set(all.map(r=>r[0])).size,all.length);
all.forEach(r=>{assert.equal(r.length,18); assert.equal(r[12],'미실행'); assert(fs.existsSync(path.join(root,r[11])),r[11]);});

const sheets=[];
const add=(name,headers,rows,widths,options={})=>sheets.push({name,headers,rows,widths,...options});
add('사용안내',['항목','내용'],[
 ['문서','jack-fulfillment 통합테스트 시나리오 / 작성일 2026-10-06'],
 ['대상 기준','현재 작업트리 코드·마이그레이션 V1–V37. 기준 커밋 2a89acb; 로컬 ERD 업데이트 포함. 실제 배포 DB 버전은 수행 전에 기록.'],
 ['현재 상태',`${all.length}개 검증 항목 작성. 애플리케이션 통합테스트를 실행한 결과가 아니며 모든 결과는 미실행.`],
 ['진행 순서','테스트데이터 준비 → E2E흐름 순서대로 실행 → 업무별시나리오 독립 실행 → 수량대사 → 결함관리·재검증 → 실행요약 확인'],
 ['결과 입력','E2E흐름·업무별시나리오 M열 드롭다운: 미실행/통과/실패/차단/해당없음. N열 실제 결과·증빙, O열 결함ID, P열 담당자, Q열 실행일을 기록.'],
 ['판정 기준','화면·API·연결 문서·수량이 기대 결과에 모두 맞으면 통과. 실패는 결함 연결. 선행 실패/환경 미준비는 차단. 해당없음은 제외 사유와 검토자 기록.'],
 ['우선순위','P0: 수량·권한·핵심 업무 연결과 중복방지. P1: 운영·예외·편의 흐름. 중요도는 이 문서의 테스트 기준이며 개발 우선순위와 구분.'],
 ['집계 정의','진행률=(통과+실패)/(전체-해당없음). 통과율=통과/(통과+실패). 차단·미실행은 실행 완료에 포함하지 않음. 결과 수정 시 Excel 재계산.'],
 ['테스트 환경','격리된 테스트 DB와 테스트 계정 사용. 실행 환경 URL·브랜치·DB 마이그레이션 버전·배치 활성값·정책값을 아래 항목에 기록. 이 문서 생성 중 DB 변경 없음.'],
 ['자동배치','application-local.yml은 autoConfirm/autoAlloc/allocRetry 활성. 서비스 기본값 false와 다름. 수동 수량검증 시 전용 환경에서 배치 간섭 통제. 배치 시나리오는 활성값·주기·실행시각 기록.'],
 ['수량 전제','E2E는 신규 전용 SKU 보유0, 다른 주문·실사·이동 없음. 독립 예외 테스트는 별도 SKU 또는 새 문서를 사용하여 E2E의 82개 결과를 오염시키지 않음.'],
 ['인증·권한','기존 데모 계정은 환경에 존재하는지 확인. 없으면 테스트용 역할계정 생성. 관리자만으로 전 시나리오를 수행하지 말 것. 비밀번호는 문서에 저장하지 않음.'],
 ['범위','공통·권한, 기준정보, 구매·입고·정정, 재고·실사, 주문·자동확정·할당, 출고·피킹·검수·패킹·송장·인계, 배송, 검색·승인함·알림.'],
 ['제외','외부 채널/택배사 실연동, 반품 전용 흐름, B2B 판매오더, 웨이브·합포, 고급 분석 등 개발취소 영역은 현재 통합 시나리오에서 제외. 송장은 테스트용 번호 수동 입력.'],
 ['근거 우선순위','서비스 코드·실제 라우트·V37 스키마 우선. DEMO.md/PROGRAMS.md는 과거 시점 내용이 있으므로 현재 상태 판정에 그대로 사용하지 않음.'],
 ['권장 완료 조건','P0 실패·차단0, 미실행0(승인된 해당없음 제외), 정상 수량대사 차이0, 주요 결함 재검증 완료.'],
 ['실행 환경 URL','[수행자가 입력]'],['실행 커밋 / DB 버전','[수행자가 입력]'],['배치 설정 / 정책값','[수행자가 입력]'],['수행 기간 / 책임자','[수행자가 입력]'],
], [25,125]);
const F=(f,v=0,percent=false)=>({f,v,percent});
const count=(status,area)=>['E2E흐름','업무별시나리오'].map((s,i)=>{
 const end=(i===0?e2e:cases).length+1;
 return area?`COUNTIFS('${s}'!$B$2:$B$${end},A${area},'${s}'!$M$2:$M$${end},"${status}")`:`COUNTIF('${s}'!$M$2:$M$${end},"${status}")`;
}).join('+');
const areas=[...new Set(all.map(r=>r[1]))];
const summary=areas.map((a,i)=>{const row=i+2,n=all.filter(r=>r[1]===a).length; return [a,n,F(count('미실행',row),n),F(count('통과',row)),F(count('실패',row)),F(count('차단',row)),F(count('해당없음',row)),F(`IFERROR((D${row}+E${row})/(B${row}-G${row}),0)`,0,true),F(`IFERROR(D${row}/(D${row}+E${row}),0)`,0,true)];});
const totalrow=summary.length+2;
summary.push(['전체',all.length,F(count('미실행'),all.length),F(count('통과')),F(count('실패')),F(count('차단')),F(count('해당없음')),F(`IFERROR((D${totalrow}+E${totalrow})/(B${totalrow}-G${totalrow}),0)`,0,true),F(`IFERROR(D${totalrow}/(D${totalrow}+E${totalrow}),0)`,0,true)]);
add('실행요약',['영역','전체','미실행','통과','실패','차단','해당없음','진행률','통과율'],summary,[25,12,12,12,12,12,12,15,15]);
const widths=[15,20,10,12,36,34,25,50,65,70,48,55,14,50,18,18,18,45];
add('E2E흐름',headers,e2e,widths,{scenario:true});
add('업무별시나리오',headers,cases,widths,{scenario:true});
add('테스트데이터',['데이터 ID','구분','준비 값 / 조건','준비 절차 / 주의점','실제 ID·코드·환경 기록'],[
 ['DATA-01','E2E 기준정보','센터A·양품창고·빈A/B; 공급처; SKU-A; 초기재고0','기존 코드 사용 시 재고0·활성여부 확인. 신규 코드는 IT260... 등 실행별 접두사 사용.',''],
 ['DATA-02','채널·SKU','활성 채널1개; 외부SKU-A→내부SKU-A 매핑','시나리오 주문번호·송장번호는 실행마다 고유값 사용. 외부 실서비스로 전송하지 않음.',''],
 ['DATA-03','E2E 수량','요청120→승인100→발주100→합격97/거부3→완료97→정정-5→주문·출고10→재고82','단계별 전표번호 저장. E2E 중 수량에 영향을 주는 다른 거래 실행 금지.',''],
 ['DATA-04','역할계정','시스템관리자·기준정보·재고·구매·입고·센터관리자·주문·피킹·패킹·배송·감사','역할별 필요한 C/R/U/D/A 및 센터 범위 확인. 승인자는 요청자와 별도 계정.',''],
 ['DATA-05','데이터 격리','센터A·센터B 각각 재고와 입고·출고 전표1개','각 센터 전용 계정으로 목록·상세·수정 범위 테스트.',''],
 ['DATA-06','주문 오류','정상 매핑1개·미매핑1개·같은 외부 주문번호 중복 파일','docs/samples와 현재 화면 다운로드 템플릿을 사용. 입력 열 이름은 현재 템플릿 기준.',''],
 ['DATA-07','동시성','SKU-C 가용10; 주문8 두 건; 브라우저 세션2개','자동배치 간섭을 통제하고 양쪽 요청시각·응답·최종 수량 수집.',''],
 ['DATA-08','예외 전표','지시10/피킹7, 검수10/포장7, 송장 미발급 박스 등 각기 별도 건','다른 케이스가 필요로 하는 상태를 덮어쓰지 않도록 문서를 분리.',''],
 ['DATA-09','배송','박스6·4; 테스트 송장2개; 지연 경계일 전후 데이터','실제 택배사 접수·실배송은 수행하지 않음. 테스트 시스템 안에서 수동 상태 변경.',''],
 ['DATA-10','배치·정책','autoConfirm/autoAlloc/allocRetry 활성값·cron·limit; 잠금·승인·오차 정책','테스트 환경의 실제 값을 먼저 기록. 배치가 다음 주기로 넘어가면 실패로 단정하지 말고 대상 선정과 로그 확인.',''],
 ['DATA-11','증빙','실행일·사용자·URL·전표번호·요청응답·화면·조회 SQL 결과','암호·세션토큰 등 인증정보는 증빙에서 제거. 원본 데이터에는 직접 수량 수정 대신 정상 업무 절차 사용.',''],
], [16,22,70,85,48]);
add('수량대사',['점검 ID','범위 / 시점','검증식·확인 내용','E2E 기대값','근거 테이블','실제 결과 / 증빙','판정'],[
 ['REC-01','입고완료 전','검수·적치만으로 보유재고가 늘지 않는지 확인','0','tb_inbound_line / tb_inbound_putaway / tb_stock','','미실행'],
 ['REC-02','입고완료','빈별 증가량 합 = 완료 적치량','A60+B37=97','tb_stock / tb_stock_history','','미실행'],
 ['REC-03','입고정정','원장 -5 / 기입고 -5 / 발주잔량 +5','재고92 / 기입고92 / 발주잔량8','tb_inbound_correct_line / tb_stock_history / tb_purchase_order_line','','미실행'],
 ['REC-04','할당','가용 = 보유 - 할당 - 판매불가','92-10-0=82','tb_stock / tb_stock_alloc / tb_order_line','','미실행'],
 ['REC-05','피킹','라인별 SUM(실적 picked_qty) = 라인 picked_qty','10','tb_outbound_pick / tb_outbound_line','','미실행'],
 ['REC-06','피킹 잔량','remain_qty = instructed_qty - picked_qty - shortage_qty','10-10-0=0','tb_outbound_line','','미실행'],
 ['REC-07','검수·패킹','0≤검수≤피킹; 라인별 포장합≤검수; 완료 흐름은 모두 같음','피킹10=검수10=포장10','tb_outbound_line / tb_pack_box_line','','미실행'],
 ['REC-08','송장','각 출고 박스에 ISSUED 송장 1개; 재발행 원 송장은 CANCELED','박스2 / 유효송장2','tb_pack_box / tb_waybill','','미실행'],
 ['REC-09','출고확정','빈별 순피킹량만 차감; 사용한 할당 해제; 원장과 일치','보유82 / 해당 주문 할당0 / 출고-10','tb_stock / tb_stock_alloc / tb_stock_history','','미실행'],
 ['REC-10','배송완료','미완료 운송수량에서 완료 박스 수량 제외; 창고재고 불변','운송중0 / 보유82','tb_waybill / tb_delivery_event / tb_pack_box_line','','미실행'],
 ['REC-11','전체 흐름','초기 + 입고 + 정정 - 출고 = 최종 보유','0+97-5-10=82','tb_stock_history / tb_stock','','미실행'],
 ['REC-12','중복·실패 요청','동일 승인·확정 재요청 및 실패 후 수량·원장 추가 반영 여부','업무 효과 1회 / 실패 효과0','해당 업무문서 / tb_stock / tb_stock_history','','미실행'],
], [15,24,85,42,65,55,15]);
add('결함관리',['결함 ID','시나리오 ID','심각도','제목','재현 절차 / 데이터','기대 결과','실제 결과','증빙 경로','담당자','상태','발견일','수정 버전','재검증 결과','비고'],Array.from({length:20},()=>Array(14).fill('')),[18,20,14,40,65,55,55,45,18,16,18,22,20,40],{defects:true});
const routeRows=[];
for(const module of fs.readdirSync(path.join(root,'frontend/src/modules'))){
 const rel=`frontend/src/modules/${module}/routes.js`,p=path.join(root,rel);
 if(!fs.existsSync(p))continue;
 const txt=fs.readFileSync(p,'utf8');
 for(const m of txt.matchAll(/path:\s*'([^']+)'[\s\S]*?meta:\s*\{([^}]+)\}/g)){
  const route=m[1],title=m[2].match(/title:\s*'([^']+)'/)?.[1]||'',perm=m[2].match(/perm:\s*'([^']+)'/)?.[1]||'별도 서버 판정 / 공통 접근';
  const linked=all.filter(c=>c[5].split(/;\s*| → /).includes(route)).map(c=>c[0]);
  routeRows.push([module,title,route,perm,linked.join(', ')||'공통 접근·역할 검증으로 확인; 별도 세부 기능 시나리오 없음',rel]);
 }
}
add('화면추적',['모듈','현재 화면명','라우트','진입 권한','연결 시나리오 ID','근거 파일'],routeRows,[20,32,35,25,90,65]);
add('근거자료',['분류','경로','사용 목적'],[
 ...Object.entries(sources).map(([k,p])=>[k,p,'현재 코드의 업무 흐름·입력 검증·관계 확인']),
 ['DB 기준','backend/src/main/resources/db/migration/V1__system.sql ~ V37__box_status_label.sql','현재 스키마 및 추가 변경점'],
 ['배치 설정','backend/src/main/resources/application-local.yml','로컬 자동확정·할당·재처리 활성 여부 확인'],
 ['보조 자료','docs/DEMO.md','계정·초기 구매입고 흐름 참고. 과거 시연 수치를 이번 실행 결과로 사용하지 않음.'],
 ['생성기','docs/tools/generate-integration-tests.mjs','엑셀 재생성용. 재실행하면 결과 입력 전 원본으로 덮어쓰므로 수행본은 다른 이름으로 저장.'],
], [20,110,85]);

// Portable OOXML writer: standard ZIP, inline strings, cached formulas and validations.
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
const col=n=>{let s='';for(n++;n;n=Math.floor((n-1)/26))s=String.fromCharCode(65+(n-1)%26)+s;return s;};
const files=new Map();
function sheetXML(s){
 const rows=[s.headers,...s.rows],end=rows.length,last=col(s.headers.length-1);
 let xml=`<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:${last}${end}"/><sheetViews><sheetView workbookViewId="0"><pane xSplit="${s.scenario?5:0}" ySplit="1" topLeftCell="${s.scenario?'F':'A'}2" activePane="${s.scenario?'bottomRight':'bottomLeft'}" state="frozen"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="30"/><cols>${s.widths.map((w,i)=>`<col min="${i+1}" max="${i+1}" width="${w}" customWidth="1"/>`).join('')}</cols><sheetData>`;
 rows.forEach((row,ri)=>{
  const h=ri===0?32:s.scenario?100:s.name==='사용안내'?58:s.name==='실행요약'?30:70;
  xml+=`<row r="${ri+1}" ht="${h}" customHeight="1">`;
  row.forEach((v,ci)=>{
   let style=ri===0?1:ri%2?2:3;
   if(ri>0&&s.scenario&&ci>=12)style=5;
   if(v&&typeof v==='object'&&'f'in v)xml+=`<c r="${col(ci)}${ri+1}" s="${v.percent?4:style}"><f>${esc(v.f)}</f><v>${v.v}</v></c>`;
   else if(typeof v==='number')xml+=`<c r="${col(ci)}${ri+1}" s="${style}"><v>${v}</v></c>`;
   else xml+=`<c r="${col(ci)}${ri+1}" s="${style}" t="inlineStr"><is><t xml:space="preserve">${esc(v)}</t></is></c>`;
  });xml+='</row>';
 });
 xml+=`</sheetData><autoFilter ref="A1:${last}${end}"/>`;
 if(s.scenario){
  xml+=`<conditionalFormatting sqref="M2:M${end}">${['통과','실패','차단'].map((v,i)=>`<cfRule type="cellIs" dxfId="${i}" priority="${i+1}" operator="equal"><formula>"${v}"</formula></cfRule>`).join('')}</conditionalFormatting>`;
 }
 const dvs=[];
 if(s.scenario)dvs.push([`M2:M${end}`,'미실행,통과,실패,차단,해당없음']);
 if(s.name==='수량대사')dvs.push([`G2:G${end}`,'미실행,통과,실패,차단,해당없음']);
 if(s.defects){dvs.push([`C2:C${end}`,'치명,높음,보통,낮음'],[`J2:J${end}`,'신규,분석중,수정중,재검증,완료,보류'],[`M2:M${end}`,'미실행,통과,실패,차단']);}
 if(dvs.length)xml+=`<dataValidations count="${dvs.length}">${dvs.map(([r,list])=>`<dataValidation type="list" allowBlank="1" showErrorMessage="1" errorTitle="목록 선택" error="지정된 값 중 하나를 선택하세요." sqref="${r}"><formula1>"${list}"</formula1></dataValidation>`).join('')}</dataValidations>`;
 xml+='<pageMargins left="0.25" right="0.25" top="0.5" bottom="0.5" header="0.2" footer="0.2"/><pageSetup paperSize="8" orientation="landscape" fitToWidth="1" fitToHeight="0"/></worksheet>';
 return xml;
}
files.set('[Content_Types].xml',`<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.map((s,i)=>`<Override PartName="/xl/worksheets/sheet${i+1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>`).join('')}</Types>`);
files.set('_rels/.rels','<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>');
files.set('xl/workbook.xml',`<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><bookViews><workbookView activeTab="0"/></bookViews><sheets>${sheets.map((s,i)=>`<sheet name="${esc(s.name)}" sheetId="${i+1}" r:id="rId${i+1}"/>`).join('')}</sheets><definedNames>${sheets.map((s,i)=>`<definedName name="_xlnm.Print_Titles" localSheetId="${i}">'${s.name}'!$1:$1</definedName>`).join('')}</definedNames><calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>`);
files.set('xl/_rels/workbook.xml.rels',`<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">${sheets.map((s,i)=>`<Relationship Id="rId${i+1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i+1}.xml"/>`).join('')}<Relationship Id="styles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`);
files.set('xl/styles.xml',`<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="맑은 고딕"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="맑은 고딕"/></font></fonts><fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF17365D"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF0F5FA"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFF8DC"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="6"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="10" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="0" fillId="4" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles><dxfs count="3">${['FFC6EFCE','FFFFC7CE','FFFFEB9C'].map(c=>`<dxf><fill><patternFill patternType="solid"><fgColor rgb="${c}"/><bgColor indexed="64"/></patternFill></fill></dxf>`).join('')}</dxfs></styleSheet>`);
sheets.forEach((s,i)=>files.set(`xl/worksheets/sheet${i+1}.xml`,sheetXML(s)));
const crcTable=Array.from({length:256},(_,n)=>{for(let k=0;k<8;k++)n=n&1?0xedb88320^(n>>>1):n>>>1;return n>>>0;});
function crc32(b){let c=0xffffffff;for(const x of b)c=crcTable[(c^x)&255]^(c>>>8);return (c^0xffffffff)>>>0;}
let offset=0;const local=[],central=[];
for(const [name,xml]of files){
 const n=Buffer.from(name),data=Buffer.from(xml),compressed=deflateRawSync(data),crc=crc32(data);
 const h=Buffer.alloc(30);h.writeUInt32LE(0x04034b50);h.writeUInt16LE(20,4);h.writeUInt16LE(0x800,6);h.writeUInt16LE(8,8);h.writeUInt32LE(crc,14);h.writeUInt32LE(compressed.length,18);h.writeUInt32LE(data.length,22);h.writeUInt16LE(n.length,26);
 const c=Buffer.alloc(46);c.writeUInt32LE(0x02014b50);c.writeUInt16LE(20,4);c.writeUInt16LE(20,6);c.writeUInt16LE(0x800,8);c.writeUInt16LE(8,10);c.writeUInt32LE(crc,16);c.writeUInt32LE(compressed.length,20);c.writeUInt32LE(data.length,24);c.writeUInt16LE(n.length,28);c.writeUInt32LE(offset,42);
 local.push(h,n,compressed);central.push(c,n);offset+=h.length+n.length+compressed.length;
}
const cd=Buffer.concat(central),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(files.size,8);end.writeUInt16LE(files.size,10);end.writeUInt32LE(cd.length,12);end.writeUInt32LE(offset,16);
fs.writeFileSync(out,Buffer.concat([...local,cd,end]));
console.log(JSON.stringify({output:out,sheets:sheets.length,e2e:e2e.length,business:cases.length,total:all.length,routeCount:routeRows.length,bytes:fs.statSync(out).size}));
