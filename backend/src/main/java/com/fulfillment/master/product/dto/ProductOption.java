package com.fulfillment.master.product.dto;

import jakarta.validation.constraints.*;

public record ProductOption(
        @NotBlank @Pattern(regexp = "COLOR|SIZE") String optionType,
        @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9-]{0,19}", message = "옵션코드는 영문 대문자·숫자·하이픈 1~20자입니다.") String optionCode,
        @NotBlank @Size(max = 100) String optionName,
        @PositiveOrZero int sortOrder) {
    public ProductOption {
        /*
         * 코드는 대문자로 맞춘다.
         *
         * SKU 코드가 이 값으로 조립되므로(PRD-24001-BK-M) 'bk' 와 'BK' 가
         * 섞이면 같은 검정이 두 코드가 된다. @Pattern 이 대문자만 받으니
         * 소문자로 치면 거절당하는데, 그건 사람에게 '다시 치세요' 라고 할
         * 일이지 막을 일이 아니다 — 받아서 맞춰 준다.
         *
         * 이름은 건드리지 않는다. '블랙' 과 'Black' 은 둘 다 사람이 읽는
         * 말이고, 코드가 같으면 섞이지 않는다.
         */
        optionCode = optionCode == null ? null : optionCode.trim().toUpperCase();
        optionName = optionName == null ? null : optionName.trim();
    }
}
