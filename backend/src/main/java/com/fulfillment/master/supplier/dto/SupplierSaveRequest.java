package com.fulfillment.master.supplier.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Supplier;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 공급처 등록 · 수정 요청.
 *
 * 사업자등록번호는 중복을 막지 않는다 (MST-010 은 '중복 경고' 라고 했다).
 * 같은 사업자가 사업부별로 코드를 따로 쓰는 경우가 실제로 있어서, 막으면
 * 정당한 등록이 거부된다. 서비스가 경고만 한다.
 */
public record SupplierSaveRequest(

		@NotBlank(message = "공급처코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,29}$",
				message = "공급처코드는 영문 대문자·숫자·하이픈 2~30자여야 합니다. 예) SUP-001")
		String supplierId,

		@NotBlank(message = "공급처명은 필수입니다.")
		@Size(max = 100, message = "공급처명은 100자 이하여야 합니다.")
		String supplierName,

		/** 하이픈 포함 형식을 그대로 받는다. 세금계산서에 적히는 모양이다. */
		@Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$",
				message = "사업자등록번호는 000-00-00000 형식이어야 합니다.")
		String bizRegNo,

		@Size(max = 50, message = "대표자명은 50자 이하여야 합니다.")
		String ceoName,

		@Size(max = 50, message = "담당자명은 50자 이하여야 합니다.")
		String managerName,

		@Pattern(regexp = "^[0-9-+() ]{7,30}$", message = "연락처 형식이 올바르지 않습니다.")
		String phone,

		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
		String email,

		@Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
		String zipCode,

		@Size(max = 200, message = "주소는 200자 이하여야 합니다.")
		String address,

		@NotBlank(message = "거래상태는 필수입니다.")
		String status,

		String payTerm,

		/**
		 * 초과입고 허용 오차율 % (INB-005).
		 *
		 * 공급처마다 다르다. 0 이면 예정수량을 넘는 입고는 전부 승인 대상이다.
		 */
		@PositiveOrZero(message = "초과입고 허용률은 0 이상이어야 합니다.")
		@DecimalMin(value = "0", message = "초과입고 허용률은 0 이상이어야 합니다.")
		@DecimalMax(value = "100", message = "초과입고 허용률은 100 이하여야 합니다.")
		BigDecimal overReceiptRate,

		@Size(max = 300, message = "비고는 300자 이하여야 합니다.")
		String remark,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public SupplierSaveRequest {
		supplierId = Texts.trimToNull(supplierId);
		supplierName = Texts.trimToNull(supplierName);
		bizRegNo = Texts.trimToNull(bizRegNo);
		ceoName = Texts.trimToNull(ceoName);
		managerName = Texts.trimToNull(managerName);
		phone = Texts.trimToNull(phone);
		email = Texts.trimToNull(email);
		zipCode = Texts.trimToNull(zipCode);
		address = Texts.trimToNull(address);
		status = Texts.trimToNull(status);
		payTerm = Texts.trimToNull(payTerm);
		remark = Texts.trimToNull(remark);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public Supplier toNewSupplier(String actorId) {
		return editable()
				.supplierId(supplierId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 공급처코드는 바꾸지 않는다 — 구매오더와 입고가 이 코드로 부른다.
	 */
	public Supplier toUpdatedSupplier(Long supplierSeq, String actorId) {
		return editable()
				.supplierSeq(supplierSeq)
				.updatedBy(actorId)
				.build();
	}

	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 */
	private Supplier.SupplierBuilder editable() {
		return Supplier.builder()
				.supplierName(supplierName)
				.bizRegNo(bizRegNo)
				.ceoName(ceoName)
				.managerName(managerName)
				.phone(phone)
				.email(email)
				.zipCode(zipCode)
				.address(address)
				.status(status)
				.payTerm(payTerm)
				.overReceiptRate(overReceiptRate == null ? BigDecimal.ZERO : overReceiptRate)
				.remark(remark)
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
