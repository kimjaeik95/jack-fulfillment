package com.fulfillment.system.menu.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Menu;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 메뉴 등록 · 수정 요청.
 *
 * 머리글인지 실제 화면인지는 groupYn 이 말한다.
 *
 * 전에는 parentId 로 갈랐다 — 비우면 머리글. 머리글이 최상위에만 있을 때는
 * 맞았는데, 기준정보 아래를 플랜트 · 제품 · 채널 · 거래처로 한 번 더 나누면서
 * (V19) 부모가 있는 머리글이 생겼다.
 *
 * 그래서 '라우트가 없으면 머리글' 로 갈음해 봤더니 더 나빴다 — 화면을 고르는
 * 것을 깜빡한 메뉴가 조용히 머리글이 되어 저장되고, 하위가 없으니 사이드바에
 * 나오지도 않는다. 만든 사람은 어디로 갔는지 모른다.
 *
 * 무엇을 만들려는지는 만드는 사람이 말해야 한다.
 */
public record MenuSaveRequest(

		@NotBlank(message = "메뉴코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$",
				message = "영문 대문자로 시작하는 2~30자여야 합니다. (숫자 _ 허용) 예) SYS_USERS")
		String menuId,

		@NotBlank(message = "메뉴명은 필수입니다.")
		@Size(max = 100, message = "메뉴명은 100자 이하여야 합니다.")
		String menuName,

		/** 상위 메뉴코드. 비우면 최상위다. 최상위는 늘 머리글이다. */
		String parentId,

		/**
		 * 머리글인가 — 'Y' 면 누를 화면 없이 아래를 묶기만 한다.
		 *
		 * 안 보내면 예전처럼 parentId 로 판단한다. 업로드 양식처럼 이 칸이
		 * 없는 입력이 아직 있어서, 없다고 거절하지 않는다.
		 */
		String groupYn,

		/** 프론트 라우트 이름. 머리글이면 비운다. */
		@Size(max = 50) String routeName,

		@Size(max = 10, message = "아이콘은 10자 이하여야 합니다.")
		String icon,

		/** 노출에 필요한 권한코드. 비우면 로그인만 하면 보인다. */
		String permId,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public MenuSaveRequest {
		menuId = Texts.trimToNull(menuId);
		menuName = Texts.trimToNull(menuName);
		parentId = Texts.trimToNull(parentId);
		groupYn = Texts.trimToNull(groupYn);
		routeName = Texts.trimToNull(routeName);
		icon = Texts.trimToNull(icon);
		permId = Texts.trimToNull(permId);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/**
	 * 머리글로 만들려는 것인가.
	 *
	 * groupYn 을 보내면 그 말을 따른다. 안 보내면 예전 방식(최상위면 머리글)으로
	 * 읽는다 — 업로드 양식에 아직 이 칸이 없다. '라우트가 비었으니 머리글'
	 * 로는 읽지 않는다. 그렇게 하면 화면 고르는 것을 깜빡한 것과 구별이 안 된다.
	 */
	public boolean isGroup() {
		return groupYn == null ? parentId == null : "Y".equalsIgnoreCase(groupYn);
	}

	/**
	 * @param parentSeq 검증을 마친 상위 메뉴 순번. 그룹이면 null
	 * @param permSeq   검증을 마친 권한 순번. 없으면 null
	 */
	public Menu toNewMenu(Long parentSeq, Long permSeq, String actorId) {
		return editable(parentSeq, permSeq)
				.menuId(menuId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 * 조회한 기존 객체를 고치지 않고 새로 만든다 — 감사로그가 변경 전후를 비교한다.
	 * 메뉴코드는 바꾸지 않는다.
	 */
	public Menu toUpdatedMenu(Long menuSeq, Long parentSeq, Long permSeq, String actorId) {
		return editable(parentSeq, permSeq)
				.menuSeq(menuSeq)
				.updatedBy(actorId)
				.build();
	}

	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 Menu 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private Menu.MenuBuilder editable(Long parentSeq, Long permSeq) {
		return Menu.builder()
				.menuName(menuName)
				.parentSeq(parentSeq)
				// 그룹 머리글은 이동할 화면이 없다. 남겨 두면 DB 제약에 걸린다.
				.routeName(parentSeq == null ? null : routeName)
				.icon(icon)
				.permSeq(permSeq)
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
