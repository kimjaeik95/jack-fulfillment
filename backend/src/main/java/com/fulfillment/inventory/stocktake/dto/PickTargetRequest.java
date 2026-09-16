package com.fulfillment.inventory.stocktake.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 고른 재고를 실사 대상으로 담는다 (지정실사).
 *
 * 재고 순번으로 받는다. 로케이션 + SKU + 소유주 세 개를 따로 받으면
 * 화면이 그 조합을 다시 맞춰 보내야 하고, 그 사이에 재고가 옮겨지면
 * 없는 자리를 가리키게 된다. 재고 한 줄을 그대로 가리키는 편이 어긋날
 * 여지가 없다.
 */
public record PickTargetRequest(

		@NotEmpty(message = "담을 재고를 하나 이상 고르세요.")
		List<Long> stockSeqs
) {

	public PickTargetRequest {
		// 같은 재고를 두 번 보내도 한 줄이다. 걸러 두면 '건너뛴 건수' 가
		// 실제로 중복된 만큼만 나와 사람이 읽기 쉽다.
		stockSeqs = stockSeqs == null ? List.of() : stockSeqs.stream().distinct().toList();
	}
}
