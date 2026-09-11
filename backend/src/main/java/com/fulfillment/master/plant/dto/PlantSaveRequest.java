package com.fulfillment.master.plant.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Plant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 플랜트 등록 · 수정 요청.
 *
 * 형식 검증은 @Valid 가, 소속 조직과 유형 코드값 확인은 서비스가 맡는다.
 * 형식만으로는 판단할 수 없는 것들이기 때문이다.
 */
public record PlantSaveRequest(

		@NotBlank(message = "플랜트코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z]{2}\\d{3}$",
				message = "영문 대문자 2자 + 숫자 3자 형식이어야 합니다. 예) PL001")
		String plantId,

		@NotBlank(message = "플랜트명은 필수입니다.")
		@Size(max = 100, message = "플랜트명은 100자 이하여야 합니다.")
		String plantName,

		@NotBlank(message = "플랜트유형은 필수입니다.")
		String plantType,

		/** 운영 조직코드. 조직개편 시 이 값만 바꾼다. */
		@NotBlank(message = "운영 조직은 필수입니다.")
		String orgId,

		@Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
		String zipCode,

		@Size(max = 300) String address,

		@Size(max = 50) String managerName,

		@Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$",
				message = "연락처는 02-1234-5678 형식으로 입력하세요.")
		String phone,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public PlantSaveRequest {
		plantId = Texts.trimToNull(plantId);
		plantName = Texts.trimToNull(plantName);
		plantType = Texts.trimToNull(plantType);
		orgId = Texts.trimToNull(orgId);
		zipCode = Texts.trimToNull(zipCode);
		address = Texts.trimToNull(address);
		managerName = Texts.trimToNull(managerName);
		phone = Texts.trimToNull(phone);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/** @param orgSeq 검증을 마친 운영 조직의 순번 */
	public Plant toNewPlant(Long orgSeq, String actorId) {
		Plant plant = new Plant();
		plant.setPlantId(plantId);
		plant.setCreatedBy(actorId);
		applyEditableFields(plant, orgSeq);
		return plant;
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 플랜트코드는 바꾸지 않는다 — 창고와 재고가 코드로 플랜트를 부른다.
	 */
	public Plant toUpdatedPlant(Long plantSeq, Long orgSeq, String actorId) {
		Plant plant = new Plant();
		plant.setPlantSeq(plantSeq);
		plant.setUpdatedBy(actorId);
		applyEditableFields(plant, orgSeq);
		return plant;
	}

	private void applyEditableFields(Plant plant, Long orgSeq) {
		plant.setOrgSeq(orgSeq);
		plant.setPlantName(plantName);
		plant.setPlantType(plantType);
		plant.setZipCode(zipCode);
		plant.setAddress(address);
		plant.setManagerName(managerName);
		plant.setPhone(phone);
		plant.setSortOrder(sortOrder == null ? 0 : sortOrder);
		plant.setUseYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
