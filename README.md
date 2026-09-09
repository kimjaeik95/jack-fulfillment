# fulfillment — 풀필먼트 관리 시스템

제품(기준정보) → 입고 → 주문 → 피킹·패킹 → 출고 흐름을 다루는 물류 운영 시스템입니다.

## 구현 범위

전체 시스템은 아래 영역으로 구성되며, **현재는 `system` 영역이 구현되어 있습니다.**

| 영역 | 다루는 것 | 상태 |
| --- | --- | --- |
| **system** | 로그인, 사용자, 조직, 역할·권한, 공통정책, 공통코드, 감사 | ✅ 구현 |
| **master** | 제품/SKU, 브랜드, 채널, 가격, 공급처 | 미구현 |
| **oms** | 주문, 배송, 클레임 | 미구현 |
| **wms** | 입하·검수·적치, 피킹·패킹, 재고·실사 | 미구현 |
| purchase / store | 구매요청·발주 / 보충·매장이동 | 미구현 |

`system`은 다른 모든 영역이 의존하는 **공통 기반**입니다. 사용자·역할·로그인은 OMS·WMS가 공유하므로 어느 한쪽에 속하지 않습니다. 의존 방향은 `oms → system`, `wms → system` 한 방향입니다.

## 저장소 구성

```
jack-fulfillment/
├── frontend/     Vue 3 화면
└── backend/      Spring Boot API 서버
```

프론트엔드와 백엔드를 한 저장소에서 관리합니다. API를 바꾸면 화면 수정이 같은 커밋에 묶여, 어느 변경이 어느 API 때문인지 추적됩니다.

### 기술 스택

| | 프론트엔드 | 백엔드 |
| --- | --- | --- |
| 런타임 | Node / Vite 6 | Java 21 |
| 프레임워크 | Vue 3 (`<script setup>`) | Spring Boot 4.1.1 |
| 상태 · 라우팅 | Pinia, Vue Router | — |
| 데이터 접근 | (현재 Mock / localStorage) | MyBatis (SQL 직접 작성) |
| DB | — | PostgreSQL 18 |
| 스키마 관리 | — | Flyway 마이그레이션 |
| 인증 | 세션 기반 (프론트 가드) | Spring Security 세션 쿠키 |
| 빌드 | npm | Gradle (래퍼 포함) |

주요 라이브러리 버전은 `backend/build.gradle` 참고. MyBatis 스타터는 Boot BOM이 관리하지 않으므로 버전을 명시합니다(`4.1.x` = Boot `4.1.x` 대응).

---

## system — 사용자·권한·공통정책

요구사항으로 주어진 "2. 사용자·권한·공통 정책" 표를 4개 엔터티로 정규화하여 CRUD로 구현했습니다.

| 요구사항 표의 컬럼 | 구현 엔터티 | 화면 |
| --- | --- | --- |
| 역할 | `roles` | 역할 관리 |
| 주요 권한 | `permissions` + `rolePermissions` (M:N) | 권한 관리 / 역할-권한 매핑 |
| 제한/승인 | `policies` | 공통정책 관리 |
| (운영 부가) | `users`, `orgs`, `auditLogs` | 사용자 관리 / 조직 관리 / 변경 이력 |

## 실행

프론트엔드와 백엔드는 각각 별도 프로세스로 띄웁니다.

**백엔드** — PostgreSQL 이 필요합니다.

DB와 계정을 한 번만 만듭니다 (`postgres` 슈퍼유저로 실행).

```sql
CREATE USER fulfillment WITH PASSWORD '비밀번호';
CREATE DATABASE fulfillment OWNER fulfillment ENCODING 'UTF8';
```

```powershell
cd backend
$env:DB_PASSWORD = "비밀번호"
gradlew bootRun        # http://localhost:8080/api

# 확인
curl http://localhost:8080/api/actuator/health
```

기동 시 Flyway 가 테이블 13개와 기준정보를 자동으로 생성합니다.

| 프로파일 | 용도 | 데모 사용자 | SQL 로그 |
| --- | --- | --- | --- |
| `local` (기본) | 개발자 PC | 포함 (12명) | 출력 |
| `dev` | 개발 서버 | 제외 | 미출력 |

접속 정보는 환경변수로 주입합니다 — `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD`.
비밀값을 설정 파일에 적어 커밋하지 않기 위한 것입니다.

### DB 스키마 — Flyway

스키마는 `backend/src/main/resources/db/migration/` 의 SQL 파일이 유일한 근거입니다.
`ddl-auto` 같은 자동 생성은 쓰지 않습니다.

```
db/migration/  V1__create_system_schema.sql     테이블 13개
               V2__insert_common_codes.sql      코드그룹 9 · 코드 48
               V3__insert_system_master.sql     조직 · 역할 · 권한 · 정책 · admin 계정
db/demo/       V900__insert_demo_users.sql      데모 사용자 12명 (local 만)
```

**이미 적용된 파일은 수정하지 않습니다.** 체크섬이 바뀌면 기동이 거부됩니다(`validate-on-migrate: true`). 변경이 필요하면 새 버전 파일을 추가하세요. 자세한 규칙은 [db/migration/README.md](backend/src/main/resources/db/migration/README.md) 에 있습니다.

### 데이터 접근 — MyBatis

- DAO 인터페이스는 각 기능의 `dao` 패키지에 두면 `@MapperScan("com.fulfillment.**.dao")` 가 자동으로 잡습니다.
- 매퍼 XML은 `resources/mapper/{영역}/` 에 둡니다 (`classpath*:mapper/**/*.xml`).
- `map-underscore-to-camel-case: true` 이므로 DB의 `user_name` 이 Java의 `userName` 으로 매핑됩니다.
- DDL에서는 **식별자를 따옴표로 감싸지 마세요.** PostgreSQL은 따옴표 없는 식별자를 소문자로 정규화하는데, 따옴표를 쓰면 대소문자가 그대로 고정되어 매퍼 SQL과 어긋나기 쉽습니다.

**프론트엔드**

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173
npm run build    # frontend/dist 로 정적 빌드
npm run preview  # 빌드 결과 확인
```

백엔드 없이 동작합니다. 데이터는 브라우저 `localStorage`에 저장되며, 헤더의 **초기화** 버튼으로 시드 상태로 되돌립니다.

> 5173 포트가 이미 사용 중이면 Vite가 자동으로 다음 포트(5174 등)를 잡습니다. 터미널에 출력되는 `Local:` URL을 확인하세요.

데이터 구조를 바꿀 때는 [client.js](frontend/src/api/client.js)의 `SCHEMA_VERSION`을 올리세요. 브라우저에 남아 있던 이전 버전 데이터는 자동으로 폐기되고 시드로 다시 시작합니다. 올리지 않으면 필드가 없는 옛 데이터가 그대로 재사용되어 오작동합니다.

## 로그인

첫 진입 시 로그인 화면(`#/login`)으로 이동합니다. 데모 계정 공통 비밀번호는 **`wms1234!`** 이며, 로그인 화면 오른쪽의 데모 계정 목록을 누르면 자동 입력됩니다.

인증 판정은 [client.js](frontend/src/api/client.js)의 `login()`에서 처리합니다.

| 상황 | 처리 |
| --- | --- |
| 없는 계정 / 비밀번호 불일치 | 동일 메시지로 응답해 계정 존재 여부를 노출하지 않음 (비밀번호 오류 시에만 남은 횟수 안내) |
| 비밀번호 5회 연속 오류 | 계정 상태를 `잠김`으로 변경, 이후 올바른 비밀번호도 거부 |
| 잠김 / 휴면 / 퇴사 / 사용중지 | 각각의 사유와 관리자 조치 안내를 표시 |
| 역할·권한 없는 계정 | 로그인은 허용하되 경고를 표시 |
| 로그인 성공 | 실패 횟수 초기화, 최근 접속 시각 갱신, 감사로그 기록 |

- 미인증 상태로 관리 화면에 접근하면 로그인 화면으로 보내고, 로그인 후 원래 목적지로 이동합니다 (`?redirect=`).
- 잠긴 계정은 **사용자 관리** 화면에서 `잠금해제`(실패 횟수 초기화 동반) 또는 `비번초기화`로 복구합니다.
- 로그인·로그아웃·로그인 실패·비밀번호 초기화는 모두 **변경 이력**에 기록됩니다.
- 비밀번호는 목록 조회 응답과 감사로그 스냅샷에서 제거됩니다(`redact`). 운영에서는 평문 저장이 아니라 단방향 해시를 서버에서만 검증해야 합니다.

## 화면

| 메뉴 | 기능 | 요구 권한 |
| --- | --- | --- |
| 로그인 | 인증, 실패 횟수 누적·계정 잠금, 데모 계정 선택 | (공개) |
| 권한 현황 | 요약 카드, 정합성 점검, 역할별 권한·정책 현황, 접속 계정의 유효 권한 | `SYS_ROLE/R` |
| 사용자 관리 | 등록·수정·삭제, 역할 배정, 승인한도, 잠금 해제, 배정 결과 미리보기 | `SYS_USER` |
| 조직 관리 | 본사/물류센터/매장 등록·수정·삭제, 상위 조직 | `SYS_COMPANY` |
| 역할 관리 | 역할 정의, 적용범위·데이터범위, 제한사항, 연결 정보 | `SYS_ROLE` |
| 권한 관리 | 기능 권한 정의, 모듈 분류, 허용 액션 | `SYS_ROLE` |
| 역할-권한 매핑 | 권한 × 액션 체크박스 매트릭스, 행·열 일괄 토글, 다른 역할에서 복사 | `SYS_ROLE/U` |
| 공통정책 관리 | 제한·승인 규칙 등록, 유형별 필수값 검증, 정책 커버리지 안내 | `SYS_POLICY` |
| 공통코드 | 코드그룹·코드 조회, 실제 사용 건수 | `SYS_CODE/R` |
| 변경 이력 | 등록/수정/삭제 감사로그, 스냅샷 상세, CSV 다운로드 | `AUD_HISTORY/R`, 다운로드는 `AUD_DOWNLOAD/X` |

## 권한·정책이 화면에 적용되는 방식

로그인한 계정의 역할·정책이 이 콘솔 자신에게도 그대로 적용됩니다. 다른 역할로 확인하려면 로그아웃 후 다른 데모 계정으로 로그인하세요.

판정 순서 (`frontend/src/stores/session.js`의 `check()`):

1. 역할-권한 매핑에 해당 액션이 있는가 → 없으면 거부
2. `READONLY` 정책이 걸린 역할인가 (조회 외 차단) → 거부
3. 대상 권한에 `BLOCK` 강도의 `DENY` 정책이 있는가 → 거부
4. `WARN` / `APPROVAL` 정책이 있으면 → 허용하되 사유를 경고로 표시

확인해 볼 수 있는 예 (모두 비밀번호 `wms1234!`):

- `admin` (시스템 관리자) → 전 화면 관리 가능, 단 업무 수량 직접 수정은 정책 P001로 미부여
- `cs01` (CS/조회 사용자) → 모든 등록·수정·삭제 버튼 비활성, 헤더에 `조회 전용` 배지
- `audit01` (감사/분석 사용자) → 이름·이메일·연락처 마스킹, CSV 다운로드는 허용되며 감사 안내 표시
- `st1.stf1` (매장 직원) → 관리 화면 진입은 되지만 변경 버튼에 권한 없음 사유가 표시됨
- `st2.stf1` (판교점 직원) → 잠긴 계정이라 로그인 차단 (관리자가 잠금 해제해야 함)

## 업무 규칙 (저장 시 서버 측 검증)

`frontend/src/api/client.js`에서 검증하며, 위반 시 모달을 닫지 않고 사유를 그대로 노출합니다.

**사용자**
- 역할의 적용범위와 소속 조직유형 불일치 차단 (예: 시스템 관리자를 매장 소속에 배정 불가)
- 직무분리(SOD): `입고 작업자`+`센터 관리자`, `매장 직원`+`매장 관리자` 동시 배정 차단
- `CS/조회 사용자`는 다른 역할과 혼합 배정 불가
- 미사용 역할 배정 불가, 이메일 중복 불가, `admin` 삭제 불가

**역할**
- 정상 상태 사용자가 사용 중인 역할은 미사용 처리 불가
- 배정 사용자 또는 연결된 정책이 있으면 삭제 불가
- 삭제 시 역할-권한 매핑 연쇄 삭제

**권한**
- 역할에 매핑되어 있거나 정책의 대상인 권한은 삭제 불가
- 허용 액션 축소 시 기존 매핑과의 충돌을 사전 경고

**정책**
- 동일 역할·대상·유형의 사용중 정책 중복 불가
- 유형별 필수값: `REQUIRED`→대상 필드, `CONDITION`→조건식, `LIMIT`→한도금액 또는 한도수량
- 역할이 대상 권한을 보유하지 않으면 "정책이 평가되지 않음" 경고

**조직**
- 소속 사용자 또는 하위 조직이 있으면 삭제 불가, 자기 자신을 상위로 지정 불가

**역할-권한 매핑**
- 권한에 정의되지 않은 액션은 선택 불가
- 조회(R)는 다른 액션의 전제 조건으로 자동 부여되며, 해제 시 함께 해제

## 초기 데이터 (요구사항 표 기준)

- 역할 10건 — 시스템 관리자 / 본사 기준정보 담당 / 구매 담당 / 센터 관리자 / 입고 작업자 / 피킹·패킹 작업자 / 매장 관리자 / 매장 직원 / CS·조회 사용자 / 감사·분석 사용자
- 권한 40건 (9개 모듈: 시스템설정·기준정보·구매·입고·출고·재고·매장·조회·감사)
- 공통정책 10건 — 표의 "제한/승인" 10개 항목을 각각 유형·강도·조건식·안내 메시지로 등록
- 사용자 13명, 조직 6개 (본사 1 / 물류센터 2 / 매장 3)

## 실제 API 연동

Mock 계층은 `frontend/src/api/client.js` 한 곳에 격리되어 있습니다.
`list / get / create / update / remove / saveRolePermissions / login / logout / resetPassword` 함수의 시그니처를 유지한 채 본문을 `fetch`로 교체하면 화면 코드는 수정할 필요가 없습니다.

```js
export async function list(entity, params) {
  const res = await fetch(`/api/${entity}?${new URLSearchParams(params)}`)
  if (!res.ok) throw new ApiError(await res.text())
  return res.json()   // { rows, total, page, size }
}
```

검증 로직(`validate`, `checkDeletable`)은 참조용으로 남겨두고, 실제 운영에서는 반드시 서버에서 동일 규칙을 적용해야 합니다.

## 구조

업무 영역별로 `frontend/src/modules/` 아래에 모듈을 두고, 여러 모듈이 함께 쓰는 것만 상위 공용 폴더에 둡니다.

```
frontend/src/
├── modules/                    업무 모듈 (각 모듈이 자기 화면·라우트·메뉴를 소유)
│   ├── auth/
│   │   ├── views/LoginView.vue
│   │   └── routes.js
│   └── system/                 사용자·조직·역할·권한·정책·공통코드·감사
│       ├── views/              화면 9개
│       └── routes.js           routes + menuGroups
│   ( 앞으로 추가될 자리 → master/ , oms/ , wms/ , purchase/ , store/ )
│
├── components/                 공용 UI — DataTable, ModalDialog, ConfirmDialog,
│                               FormField, CodeBadge, ActionTags, ToastHost
├── composables/
│   └── useCrud.js              등록/수정 모달·검증·삭제 확인·권한 게이팅 공통 로직
├── stores/
│   ├── index.js                공유 Pinia 인스턴스 (라우터 가드에서 사용)
│   ├── session.js              로그인/로그아웃, 유효 권한 계산, 정책 판정, 마스킹
│   ├── admin.js                엔터티 로딩·캐싱·CRUD 액션
│   └── toast.js                알림
├── api/
│   ├── codes.js                공통코드 정의 (코드그룹·라벨·색상)
│   ├── seed.js                 초기 데이터 (요구사항 표를 정규화)
│   └── client.js               Mock API + 업무 규칙 검증 + 감사로그  ← 실제 API 교체 지점
├── router/index.js             모듈의 routes·menuGroups 를 합치고 인증 가드 적용
├── assets/styles.css           디자인 토큰 (라이트/다크)
├── App.vue                     레이아웃 (헤더·사이드바) / 로그인은 전체 화면
└── main.js
```

### 새 업무 모듈 추가하는 방법

1. `src/modules/oms/views/` 에 화면을 만듭니다.
2. `src/modules/oms/routes.js` 에 `routes` 와 `menuGroups` 를 내보냅니다.
3. `src/router/index.js` 에 두 줄을 추가합니다.

```js
import * as oms from '@/modules/oms/routes.js'
const modules = [auth, system, oms]
```

라우트의 `meta.perm` 에 권한코드를 선언하면 사이드바 노출과 버튼 활성 상태가 자동으로 그 권한을 따릅니다.

## 검증 결과

- `npm run build` 통과
- 업무 규칙 46건 검증 통과 — 필수값·형식·중복·참조 무결성·직무분리·연쇄삭제·감사로그
- 인증 규칙 40건 검증 통과 — 자격 오류 응답 통일·실패 누적 잠금·상태별 차단·비밀번호 비노출
- 전 화면 렌더링 및 로그인 → 권한 게이팅 → 마스킹 → 잠금 해제 흐름을 브라우저에서 확인
