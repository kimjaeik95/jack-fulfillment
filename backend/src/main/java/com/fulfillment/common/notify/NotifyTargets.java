package com.fulfillment.common.notify;

import com.fulfillment.domain.Role;
import com.fulfillment.system.role.dao.RoleDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 알림을 받을 역할을 찾는다 (COM-PG-015).
 *
 * 알림은 사람이 아니라 <b>역할</b>에 걸린다. 사람으로 박으면 휴가 가고
 * 퇴사하면 그 알림이 허공에 뜬다. 그런데 코드는 'PURCHASER' 같은 아이디로
 * 말하고 표는 순번으로 잡으므로 여기서 바꿔 준다.
 *
 * 한 번 찾으면 기억해 둔다. 역할 순번은 바뀌지 않고, 알림은 결품 · 배송실패
 * 처럼 자주 일어나는 자리에서 불려서 매번 역할 표를 읽을 이유가 없다.
 * 역할이 새로 생기는 일은 드물고, 생겨도 그때는 이미 뜨는 중이 아니다.
 */
@Component
public class NotifyTargets {

	private static final Logger log = LoggerFactory.getLogger(NotifyTargets.class);

	private final RoleDao roleDao;
	private final Map<String, Long> cache = new ConcurrentHashMap<>();

	public NotifyTargets(RoleDao roleDao) {
		this.roleDao = roleDao;
	}

	/**
	 * 역할 아이디 → 순번.
	 *
	 * 없는 역할이면 null 을 돌려준다. 그러면 알림은 role_seq 없이 들어가
	 * <b>모두에게</b> 보인다 — 아무에게도 안 보이는 것보다 낫다. 받을 사람을
	 * 못 찾았다고 알림을 버리면, 정작 그게 중요한 알림일 때 아무도 모른다.
	 */
	public Long roleSeqOf(String roleId) {
		if (roleId == null || roleId.isBlank()) {
			return null;
		}
		Long cached = cache.get(roleId);
		if (cached != null) {
			return cached;
		}
		Role role = roleDao.selectByRoleId(roleId);
		if (role == null) {
			log.warn("알림 수신 역할을 찾을 수 없습니다: {} — 모두에게 보이는 알림으로 남깁니다.", roleId);
			return null;
		}
		cache.put(roleId, role.getRoleSeq());
		return role.getRoleSeq();
	}

	/**
	 * 알림을 못 남겼다.
	 *
	 * 업무는 그대로 간다. 결품을 적었는데 알림을 못 남겼다고 결품 등록이
	 * 되돌려지면 본말이 뒤집힌다 — 알림함이 업무를 인질로 잡는 셈이다.
	 */
	public void logFailure(String kind, String refNo, Exception e) {
		log.warn("알림을 남기지 못했습니다. kind={} ref={} — {}", kind, refNo, e.toString());
	}
}
