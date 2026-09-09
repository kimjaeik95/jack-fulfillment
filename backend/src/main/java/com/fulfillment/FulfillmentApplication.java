package com.fulfillment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 풀필먼트 관리 시스템 API.
 *
 * 업무 모듈은 com.fulfillment.<module> 아래에 둔다.
 *   system  — 사용자·조직·역할·권한·정책·공통코드·감사 (공통 기반)
 *   master  — 제품/SKU·브랜드·채널·가격·공급처
 *   oms     — 주문·배송
 *   wms     — 입고·출고·재고
 */
@SpringBootApplication
// DAO 인터페이스는 각 기능의 dao 패키지에 둔다. 매퍼 XML 은 resources/mapper 아래.
@MapperScan("com.fulfillment.**.dao")
public class FulfillmentApplication {

	public static void main(String[] args) {
		SpringApplication.run(FulfillmentApplication.class, args);
	}

}
