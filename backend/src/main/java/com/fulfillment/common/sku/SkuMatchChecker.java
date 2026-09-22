package com.fulfillment.common.sku;

import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.domain.Code;
import com.fulfillment.domain.Sku;
import com.fulfillment.system.code.dao.CodeDao;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 채널이 보낸 상품명과 고른 SKU 가 서로 맞는지 눈으로 볼 수 있게 한다.
 *
 * <b>막지 않는다.</b> 경고만 만든다.
 *
 * 채널 표시명은 자유 텍스트다. 같은 상품을 채널마다 다르게 적고, 띄어쓰기도
 * 약어도 제각각이다. 기계가 '틀렸다' 고 단정할 근거가 없다 — 단정해서 막으면
 * 멀쩡한 매핑이 자꾸 걸리고, 그러면 사람들은 경고를 읽지 않고 넘기는 습관이
 * 든다. 그 습관이 생기면 진짜 틀린 건도 같이 지나간다.
 *
 * 그래서 색상 · 사이즈 · 상품명 셋만 본다. 이 셋은 어긋나면 거의 확실히
 * 실수이고, 맞아도 맞다고 보장하지는 않는다. 판단은 사람이 한다.
 *
 * 왜 색상 · 사이즈가 쓸 만한가: 둘 다 공통코드라 'BK' 의 이름이 '블랙' 임을
 * 서버가 안다. 채널은 대개 옵션명에 그 말을 그대로 적는다 ("블랙 / M").
 * 코드값('BK')으로 적는 채널도 있어서 코드와 이름을 둘 다 찾아본다.
 */
@Component
public class SkuMatchChecker {

	private final CodeDao codeDao;

	public SkuMatchChecker(CodeDao codeDao) {
		this.codeDao = codeDao;
	}

	/**
	 * 채널이 준 표시값과 SKU 를 견줘 이상한 점을 모은다.
	 *
	 * 비어 있으면 눈에 띄는 어긋남이 없다는 뜻이지, 맞다는 뜻이 아니다.
	 *
	 * @param extProductName 채널 표시 상품명 (없을 수 있다)
	 * @param extOptionName  채널 표시 옵션명 (없을 수 있다)
	 */
	public List<String> mismatches(String extProductName, String extOptionName, Sku sku) {
		List<String> notes = new ArrayList<>();
		if (sku == null) {
			return notes;
		}

		// 채널이 보낸 말을 한 덩어리로 놓고 그 안에서 찾는다. 채널마다 색을
		// 상품명에 넣기도 하고 옵션명에 넣기도 해서 나눠 보면 놓친다.
		String haystack = normalize(extProductName + " " + extOptionName);

		if (haystack.isBlank()) {
			// 표시값이 아예 없으면 견줄 것이 없다. 그것 자체가 경고다 —
			// 사람도 무엇을 주문한 것인지 알 방법이 없다.
			notes.add("채널이 보낸 상품명이 없어 무엇을 주문한 것인지 대조할 수 없습니다.");
			return notes;
		}

		checkCode(notes, haystack, CodeGroups.COLOR, sku.getColorCode(), "색상");
		checkCode(notes, haystack, CodeGroups.SIZE, sku.getSizeCode(), "사이즈");

		// 상품명은 한 글자도 안 겹치면 짚는다. 부분만 겹치는 경우는 넘긴다 —
		// '베이직 반팔티' 와 '베이직 반팔 티셔츠' 를 틀렸다고 할 수 없다.
		if (sku.getProductName() != null && !sku.getProductName().isBlank()
				&& !sharesAnyWord(haystack, sku.getProductName())) {
			notes.add("상품명이 한 글자도 겹치지 않습니다. 채널 \"%s\" ↔ SKU \"%s\""
					.formatted(display(extProductName, extOptionName), sku.getProductName()));
		}

		return notes;
	}

	/** 경고를 화면에 띄울 한 줄로. 없으면 null. */
	public String warning(String extProductName, String extOptionName, Sku sku) {
		List<String> notes = mismatches(extProductName, extOptionName, sku);
		if (notes.isEmpty()) {
			return null;
		}
		return "채널이 보낸 내용과 어긋나는 점이 있습니다 — " + String.join(" ", notes)
				+ " 맞다면 그대로 두세요.";
	}

	/* ------------------------------------------------------------------ */

	/**
	 * 코드 하나를 견준다.
	 *
	 * 채널이 이 코드계열의 말을 하나도 안 썼으면 아무 말도 하지 않는다.
	 * 옵션을 안 보내는 채널이 흔한데, 그때마다 경고를 내면 경고가 흔해져서
	 * 아무도 안 읽는다. 다른 값을 <b>썼을 때만</b> 짚는다.
	 */
	private void checkCode(List<String> notes, String haystack, String groupId,
			String skuCode, String label) {
		if (skuCode == null || skuCode.isBlank()) {
			return;
		}
		Map<String, String> labels = labelsOf(groupId);

		String mine = labels.get(skuCode);
		if (mine != null && contains(haystack, mine)) {
			return;
		}
		if (contains(haystack, skuCode)) {
			return;
		}

		// 내 값은 안 보이는데 같은 계열의 다른 값이 보이면 그것을 알려 준다.
		// "사이즈가 다릅니다" 보다 "L 이라는데 S 를 골랐습니다" 가 훨씬 낫다.
		List<String> seen = new ArrayList<>();
		for (Map.Entry<String, String> e : labels.entrySet()) {
			if (e.getKey().equals(skuCode)) {
				continue;
			}
			if (contains(haystack, e.getValue()) || contains(haystack, e.getKey())) {
				seen.add(e.getValue());
			}
		}
		if (!seen.isEmpty()) {
			notes.add("%s%s 다릅니다. 채널은 %s 인데 고른 SKU 는 %s 입니다."
					.formatted(label, subjectParticle(label), String.join(" · ", seen),
							mine == null ? skuCode : mine));
		}
	}

	/**
	 * '이' 냐 '가' 냐 — 앞말에 받침이 있으면 '이' 다.
	 *
	 * 문구를 조립하면 피할 수 없는 일이다. '색상가 다릅니다' 가 화면에 뜨면
	 * 읽는 사람은 그 경고를 덜 믿게 된다.
	 *
	 * 한글 음절은 (코드 - 가) % 28 이 0 이면 받침이 없다. 한글이 아닌 말로
	 * 끝나면 '가' 로 둔다 — 영문 약어가 라벨에 오는 경우다.
	 */
	private String subjectParticle(String word) {
		if (word == null || word.isBlank()) {
			return "가";
		}
		char last = word.charAt(word.length() - 1);
		if (last < 0xAC00 || last > 0xD7A3) {
			return "가";
		}
		return (last - 0xAC00) % 28 == 0 ? "가" : "이";
	}

	/** 코드값 → 이름. 순서를 지켜 읽기 좋게 둔다. */
	private Map<String, String> labelsOf(String groupId) {
		Map<String, String> map = new LinkedHashMap<>();
		for (Code c : codeDao.selectCodeLabels(groupId)) {
			map.put(c.getCodeId(), c.getCodeName());
		}
		return map;
	}

	/**
	 * 공백을 지우고 소문자로 맞춘다.
	 *
	 * 채널 표시명은 "블랙 / M", "블랙/M", "블랙 M" 이 다 같은 뜻이다.
	 * 공백을 지워야 'S' 같은 한 글자도 자리를 안 타고 찾힌다.
	 */
	private String normalize(String s) {
		return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
	}

	private boolean contains(String haystack, String needle) {
		String n = normalize(needle);
		return !n.isBlank() && haystack.contains(n);
	}

	/**
	 * 낱말이 하나라도 겹치나.
	 *
	 * 한 글자는 세지 않는다. 우연히 겹칠 확률이 너무 높아 '겹쳤다' 는 신호가
	 * 되지 못한다.
	 */
	private boolean sharesAnyWord(String haystack, String productName) {
		for (String word : productName.split("\\s+")) {
			if (word.length() >= 2 && haystack.contains(normalize(word))) {
				return true;
			}
		}
		return false;
	}

	private String display(String productName, String optionName) {
		String a = productName == null ? "" : productName.trim();
		String b = optionName == null ? "" : optionName.trim();
		if (a.isBlank()) {
			return b;
		}
		return b.isBlank() ? a : a + " / " + b;
	}
}
