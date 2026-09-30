package com.fulfillment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스프링 컨텍스트가 뜨는지만 본다.
 *
 * 확인하는 것은 <b>빈 배선</b>이다 — 서비스 · 매퍼 · 보안 설정이 서로
 * 맞물리는가. 생성자 인자가 안 맞거나 빈이 빠지면 여기서 걸린다.
 *
 * <b>DB 는 안 본다.</b> test 프로파일이 Flyway 를 끄고 커넥션을 못 얻어도
 * 뜨게 해 둔다 — 이유는 application-test.yml 에 적었다. 전에는 프로파일을
 * 안 주어 기본(local)로 떴고, DB_PASSWORD 가 없으면 빌드가 실패했다.
 * 코드가 멀쩡한데 빌드가 빨간 것은 나쁘다.
 *
 * 질의까지 보려면 Testcontainers 로 진짜 PostgreSQL 을 띄워야 한다.
 * 스키마가 PostgreSQL 전용이라(GENERATED 컬럼 · 부분 유니크 인덱스)
 * H2 로 바꿔치면 테스트가 통과하는 스키마와 운영 스키마가 달라진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class FulfillmentApplicationTests {

	@Test
	void contextLoads() {
	}

}
