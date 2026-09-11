package com.fulfillment.common.upload;

import com.fulfillment.common.csv.CsvReader;
import com.fulfillment.common.security.LoginUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 행을 자기 트랜잭션에서 반영한다 (COM-PG-010).
 *
 * 부분성공(CMN-004)을 성립시키는 자리다. 전체를 한 트랜잭션에 묶으면 900번째
 * 행 하나가 틀렸을 때 앞의 899건이 함께 되돌려진다. 그러면 사용자는 파일
 * 전체를 완벽하게 만들어야만 한 건도 넣을 수 없다.
 *
 * 별도 빈으로 둔 이유는 스프링의 트랜잭션이 프록시로 동작하기 때문이다.
 * {@link UploadService} 안에서 자기 메서드를 부르면 프록시를 거치지 않아
 * REQUIRES_NEW 가 적용되지 않는다.
 */
@Component
public class UploadRowRunner {

	/**
	 * @return 등록이었으면 true, 수정이었으면 false
	 * @throws com.fulfillment.common.exception.BusinessException 행이 규칙에 맞지 않을 때
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean run(UploadTarget target, LoginUser actor, CsvReader.Row row) {
		return target.apply(actor, row);
	}
}
