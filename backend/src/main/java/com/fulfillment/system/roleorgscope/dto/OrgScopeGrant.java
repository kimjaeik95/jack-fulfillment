package com.fulfillment.system.roleorgscope.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 역할에 열어 줄 조직 한 곳.
 * 조직범위 화면의 한 행에 대응한다.
 */
public record OrgScopeGrant(

		@NotBlank(message = "조직코드는 필수입니다.")
		String orgId,

		/**
		 * 하위 조직까지 열지.
		 *
		 * 기본은 'Y' 다. 센터를 지정했는데 그 아래 창고가 안 열리면
		 * 대개는 의도가 아니고, 창고가 새로 생길 때마다 권한을 손보게
		 * 된다. 하위를 빼야 하는 쪽이 드문 경우라 그쪽을 명시하게 한다.
		 */
		@Pattern(regexp = "[YN]", message = "하위 포함은 Y 또는 N 입니다.")
		String includeChildYn
) {

	public OrgScopeGrant {
		orgId = Texts.trimToNull(orgId);
		includeChildYn = Texts.trimToNull(includeChildYn);
	}

	/** 안 보냈으면 하위까지 포함 */
	public String includeChildOrDefault() {
		return includeChildYn == null ? "Y" : includeChildYn;
	}
}
