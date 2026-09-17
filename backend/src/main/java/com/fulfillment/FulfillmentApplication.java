package com.fulfillment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 풀필먼트 관리 시스템 API.
 *
 * 업무 모듈은 com.fulfillment.<module> 아래에 둔다.
 *   system    — 사용자·조직·역할·권한·정책·공통코드·감사 (공통 기반)
 *   master    — 제품/SKU·브랜드·채널·가격·공급처
 *   inventory — 재고 조회·이동·조정·실사·대사
 *   oms       — 주문·배송
 *   wms       — 입고·출고
 */
@SpringBootApplication
// DAO 인터페이스는 각 기능의 dao 패키지에 둔다. 매퍼 XML 은 resources/mapper 아래.
@MapperScan("com.fulfillment.**.dao")
// 배치(@Scheduled)를 켠다. 배치 자체는 각자 설정값으로 꺼져 있어서, 이
// 어노테이션만으로는 아무것도 돌지 않는다 — 켜는 것은 프로파일의 일이다.
// 지금은 재고 정합성 점검(INV-BT-001) 하나뿐이다.
@EnableScheduling
// app.security.* 를 레코드로 받는다 (SecurityProperties). 로그인 잠금 기준처럼
// 사고 한 번에 바뀌는 값을 코드에 상수로 박아 두지 않기 위해서다.
@ConfigurationPropertiesScan
public class FulfillmentApplication {

	public static void main(String[] args) {
		SpringApplication.run(FulfillmentApplication.class, args);
	}

}
