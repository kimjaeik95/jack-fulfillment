package com.fulfillment.master.warehouse.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Warehouse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 창고 등록 · 수정 요청.
 *
 * 창고코드는 플랜트코드와 짝으로만 의미가 있다. 그래서 등록 · 수정 모두
 * plantId 를 함께 받는다.
 *
 * 코드 형식을 조직 · 플랜트보다 느슨하게 둔다(영문 대문자 2~20자). 창고는
 * GD · RT · DF 처럼 짧게 쓰는 것이 현장 관행이고, 여기에 5자 고정을
 * 강요하면 의미 없는 자리를 채우게 된다.
 */
public record WarehouseSaveRequest(

		/** 소속 플랜트코드 */
		@NotBlank(message = "소속 플랜트는 필수입니다.")
		String plantId,

		@NotBlank(message = "창고코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9]{2,20}$",
				message = "창고코드는 영문 대문자와 숫자 2~20자여야 합니다. 예) GD")
		String warehouseId,

		@NotBlank(message = "창고명은 필수입니다.")
		@Size(max = 100, message = "창고명은 100자 이하여야 합니다.")
		String warehouseName,

		@NotBlank(message = "창고유형은 필수입니다.")
		String warehouseType,

		@Size(max = 200, message = "위치는 200자 이하여야 합니다.")
		String positionDesc,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public WarehouseSaveRequest {
		plantId = Texts.trimToNull(plantId);
		warehouseId = Texts.trimToNull(warehouseId);
		warehouseName = Texts.trimToNull(warehouseName);
		warehouseType = Texts.trimToNull(warehouseType);
		positionDesc = Texts.trimToNull(positionDesc);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/** @param plantSeq 검증을 마친 소속 플랜트의 순번 */
	public Warehouse toNewWarehouse(Long plantSeq, String actorId) {
		Warehouse warehouse = new Warehouse();
		warehouse.setWarehouseId(warehouseId);
		warehouse.setCreatedBy(actorId);
		applyEditableFields(warehouse, plantSeq);
		return warehouse;
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 창고코드와 소속 플랜트는 바꾸지 않는다 — 로케이션과 재고가 그 조합으로
	 * 창고를 부르고, 플랜트를 옮기면 로케이션코드 체계가 어긋난다.
	 */
	public Warehouse toUpdatedWarehouse(Long warehouseSeq, Long plantSeq, String actorId) {
		Warehouse warehouse = new Warehouse();
		warehouse.setWarehouseSeq(warehouseSeq);
		warehouse.setUpdatedBy(actorId);
		applyEditableFields(warehouse, plantSeq);
		return warehouse;
	}

	private void applyEditableFields(Warehouse warehouse, Long plantSeq) {
		warehouse.setPlantSeq(plantSeq);
		warehouse.setWarehouseName(warehouseName);
		warehouse.setWarehouseType(warehouseType);
		warehouse.setPositionDesc(positionDesc);
		warehouse.setSortOrder(sortOrder == null ? 0 : sortOrder);
		warehouse.setUseYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
