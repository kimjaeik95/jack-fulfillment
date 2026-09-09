# Flyway 마이그레이션

이 폴더의 SQL 파일이 DB 스키마의 유일한 근거입니다. 애플리케이션 기동 시 Flyway가
적용 안 된 파일을 버전 순서대로 실행하고, 실행 이력을 `flyway_schema_history` 테이블에 남깁니다.

**대상 DB는 PostgreSQL 하나입니다.** 호환용으로 문법을 낮출 필요가 없으므로
PostgreSQL 고유 기능을 그대로 씁니다.

| 사용하는 것 | 예 |
| --- | --- |
| IDENTITY 컬럼 | `bigint GENERATED ALWAYS AS IDENTITY` |
| 테이블·컬럼 주석 | `COMMENT ON COLUMN tb_user.status IS '...'` |
| VALUES 리스트 조인 | `FROM (VALUES (...), (...)) AS v(col1, col2)` |
| 배열 전개 | `CROSS JOIN LATERAL unnest(string_to_array(v.actions, NULL))` |
| 부분 인덱스 | `CREATE INDEX ... WHERE use_yn = 'Y'` |
| 긴 텍스트 | `text` |

## 폴더 구성

```
db/migration/   모든 환경에 적용 — 스키마 + 기준정보 + admin 계정
db/demo/        local 프로파일만 적용 — 데모 사용자 12명
```

`db/demo` 를 분리한 이유는 Flyway가 기본적으로 모든 환경에 마이그레이션을 적용하기 때문입니다.
공통 비밀번호를 쓰는 데모 계정이 운영에 배포되면 그대로 보안 구멍이 됩니다.

## 파일 이름 규칙

```
V{버전}__{설명}.sql        예) V1__create_system_schema.sql
```

- `V` 는 대문자, 버전과 설명 사이는 **밑줄 두 개**(`__`)
- 버전은 정수 또는 점 구분(`V1`, `V1_1`, `V2`)
- 설명은 소문자 + 밑줄

## 반드시 지킬 것

**이미 적용된 파일은 절대 수정하지 마세요.** Flyway는 각 파일의 체크섬을 저장하고,
바뀌면 기동을 거부합니다(`validate-on-migrate: true`). 이건 안전장치입니다 —
개발자 A의 DB에는 적용됐고 개발자 B의 DB에는 안 적용된 파일을 수정하면
두 DB의 스키마가 영구히 달라지기 때문입니다.

변경이 필요하면 **새 버전 파일을 추가**하세요.

```
V1__create_system_schema.sql       ← 수정 금지
V4__add_user_last_password_try.sql ← 이렇게 추가
```

## 로컬 DB 초기화

로컬 PostgreSQL 을 처음 상태로 되돌리려면 스키마를 비우고 다시 실행합니다.

```powershell
# psql 은 PATH 에 없으므로 전체 경로로 실행
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U fulfillment -d fulfillment `
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

cd backend
gradlew bootRun
```

`DROP SCHEMA public CASCADE` 는 `flyway_schema_history` 까지 지우므로
Flyway가 V1부터 다시 적용합니다.
