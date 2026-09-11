package com.fulfillment.system.menu.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.domain.Menu;
import com.fulfillment.domain.Permission;
import com.fulfillment.system.menu.dao.MenuDao;
import com.fulfillment.system.menu.dto.MenuResponse;
import com.fulfillment.system.menu.dto.MenuSaveRequest;
import com.fulfillment.system.permission.dao.PermissionDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메뉴 관리 (COM-PG-005).
 *
 * 지금까지 사이드바는 프론트 코드가 들고 있었다. 그러면 메뉴 순서 하나를
 * 바꾸는 데도 배포가 필요하다. 메뉴를 데이터로 옮겨 화면에서 관리한다.
 *
 * 라우트(어떤 컴포넌트를 그릴지)는 여전히 코드가 소유한다. 메뉴는 "그 라우트를
 * 어디에 어떤 이름으로 걸지"만 정한다. 그래서 없는 라우트를 가리키는 메뉴가
 * 생길 수 있고, 그건 화면이 경고로 알려준다.
 *
 * 화면에서 버튼을 막는 것과 별개로 모든 진입점에서 서버가 다시 권한을 판정한다.
 */
@Service
public class MenuService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "SYS_MENU";
	private static final String TABLE = "tb_menu";

	/**
	 * 숨기거나 지우면 스스로를 되돌릴 수 없게 되는 메뉴.
	 *
	 * 메뉴 관리 화면을 숨기면 메뉴를 고칠 방법이 사라지고, 역할-권한 매핑을
	 * 숨기면 권한을 되돌릴 방법이 사라진다. 둘 다 DB 를 직접 고쳐야 복구된다.
	 */
	private static final List<String> PROTECTED_MENU_IDS = List.of("SYS_MENUS", "SYS_ROLEPERMS");

	private static final List<Field<Menu>> AUDIT_FIELDS = List.of(
			new Field<>("menu_name", Menu::getMenuName),
			new Field<>("parent_id", Menu::getParentId),
			new Field<>("route_name", Menu::getRouteName),
			new Field<>("icon", Menu::getIcon),
			new Field<>("perm_id", Menu::getPermId),
			new Field<>("sort_order", Menu::getSortOrder),
			new Field<>("use_yn", Menu::getUseYn));

	private final MenuDao menuDao;
	private final PermissionDao permissionDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public MenuService(MenuDao menuDao, PermissionDao permissionDao,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.menuDao = menuDao;
		this.permissionDao = permissionDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	/** 관리 화면용 — 미사용까지 평면으로 */
	@Transactional(readOnly = true)
	public List<MenuResponse> search(LoginUser actor, String keyword, String useYn) {
		permissionChecker.require(actor, PERM, "R");
		return menuDao.selectAll(keyword, useYn).stream().map(MenuResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public MenuResponse get(LoginUser actor, String menuId) {
		permissionChecker.require(actor, PERM, "R");
		return MenuResponse.of(mustFind(menuId));
	}

	/**
	 * 사이드바용 — 로그인 사용자에게 보이는 메뉴를 트리로.
	 *
	 * 메뉴 관리 권한(SYS_MENU)을 요구하지 않는다. 자기 메뉴를 못 보면
	 * 아무 화면에도 들어갈 수 없기 때문이다. 대신 어떤 메뉴가 보이는지는
	 * 서버가 사용자의 역할로 판정한다.
	 */
	@Transactional(readOnly = true)
	public List<MenuResponse> myMenus(LoginUser actor) {
		if (actor == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		return toTree(menuDao.selectVisible(actor.getUserSeq()));
	}

	/** 평면 목록을 그룹 → 항목 2단 트리로 묶는다 */
	private List<MenuResponse> toTree(List<Menu> flat) {
		Map<Long, List<MenuResponse>> childrenOf = new LinkedHashMap<>();
		for (Menu m : flat) {
			if (!m.isGroup()) {
				childrenOf.computeIfAbsent(m.getParentSeq(), k -> new ArrayList<>())
						.add(MenuResponse.of(m));
			}
		}
		List<MenuResponse> tree = new ArrayList<>();
		for (Menu m : flat) {
			if (m.isGroup()) {
				tree.add(MenuResponse.of(m, childrenOf.getOrDefault(m.getMenuSeq(), List.of())));
			}
		}
		return tree;
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public MenuResponse create(LoginUser actor, MenuSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (menuDao.countByMenuId(request.menuId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 메뉴코드입니다. (%s)".formatted(request.menuId()));
		}

		Menu parent = resolveParent(request, null);
		validateRoute(request, null);
		Long permSeq = resolvePermSeq(request.permId());

		menuDao.insert(request.toNewMenu(seqOf(parent), permSeq, actorId(actor)));

		Menu saved = mustFind(request.menuId());
		auditRecorder.recordCreate(actor, TABLE, saved.getMenuId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "메뉴 등록"));
		return MenuResponse.of(saved);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String menuId, MenuSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Menu before = mustFind(menuId);

		Menu parent = resolveParent(request, before);
		validateRoute(request, menuId);
		Long permSeq = resolvePermSeq(request.permId());
		validateNotHiding(before, request);
		String warning = warnOnDisable(before, request);

		menuDao.update(request.toUpdatedMenu(before.getMenuSeq(), seqOf(parent), permSeq,
				actorId(actor)));

		Menu after = mustFind(menuId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, menuId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "메뉴 수정"));
		return new Result(MenuResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String menuId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Menu before = mustFind(menuId);

		if (PROTECTED_MENU_IDS.contains(menuId)) {
			throw new BusinessException(ErrorCode.PROTECTED,
					("'%s' 메뉴는 삭제할 수 없습니다. 이 메뉴가 사라지면 메뉴와 권한을 "
							+ "되돌릴 방법이 없어집니다.").formatted(before.getMenuName()));
		}
		int children = menuDao.countChildren(before.getMenuSeq());
		if (children > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					"하위 메뉴 %d개가 있어 삭제할 수 없습니다. 하위 메뉴를 먼저 옮기거나 삭제하세요."
							.formatted(children));
		}

		menuDao.delete(before.getMenuSeq());
		auditRecorder.recordDelete(actor, TABLE, menuId, before, AUDIT_FIELDS,
				defaultReason(reason, "메뉴 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 상위 메뉴 확인. 메뉴는 2단까지만 둔다.
	 *
	 * 3단 이상을 허용하면 사이드바가 접혔을 때 표현할 방법이 없고, 화면마다
	 * 펼침 상태를 따로 기억해야 한다. 얻는 것에 비해 비용이 크다.
	 */
	private Menu resolveParent(MenuSaveRequest request, Menu self) {
		if (request.isGroup()) {
			if (request.routeName() != null) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						"그룹 머리글은 이동할 화면을 가질 수 없습니다. 상위 메뉴를 지정하거나 라우트를 비우세요.");
			}
			return null;
		}

		Menu parent = menuDao.selectByMenuId(request.parentId());
		if (parent == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"상위 메뉴를 찾을 수 없습니다. (%s)".formatted(request.parentId()));
		}
		if (!parent.isGroup()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("'%s'은(는) 이미 하위 메뉴라 상위로 지정할 수 없습니다. 메뉴는 2단까지만 둡니다.")
							.formatted(parent.getMenuName()));
		}
		if (self != null) {
			if (parent.getMenuSeq().equals(self.getMenuSeq())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						"자기 자신을 상위 메뉴로 지정할 수 없습니다.");
			}
			// 하위를 거느린 그룹을 다른 그룹 밑으로 넣으면 3단이 된다
			int children = menuDao.countChildren(self.getMenuSeq());
			if (children > 0) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("'%s'에는 하위 메뉴 %d개가 있어 다른 그룹의 하위로 옮길 수 없습니다. "
								+ "하위 메뉴를 먼저 옮기세요.")
								.formatted(self.getMenuName(), children));
			}
		}
		return parent;
	}

	/**
	 * 라우트 확인.
	 *
	 * 하위 메뉴는 갈 곳이 있어야 하고, 두 메뉴가 같은 라우트를 가리키면
	 * 사이드바에서 어느 쪽이 활성인지 알 수 없다.
	 */
	private void validateRoute(MenuSaveRequest request, String exceptMenuId) {
		if (request.isGroup()) {
			return;
		}
		if (request.routeName() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"하위 메뉴는 이동할 화면(라우트)이 필요합니다.");
		}
		if (menuDao.countByRouteName(request.routeName(), exceptMenuId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("이미 다른 메뉴가 '%s' 화면을 가리키고 있습니다. 한 화면은 한 메뉴에만 연결됩니다.")
							.formatted(request.routeName()));
		}
	}

	private Long resolvePermSeq(String permId) {
		if (permId == null) {
			return null;
		}
		Permission perm = permissionDao.selectByPermId(permId);
		if (perm == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"권한을 찾을 수 없습니다. (%s)".formatted(permId));
		}
		return perm.getPermSeq();
	}

	/** 스스로를 되돌릴 수 없게 만드는 변경을 막는다 */
	private void validateNotHiding(Menu before, MenuSaveRequest request) {
		if (!PROTECTED_MENU_IDS.contains(before.getMenuId())) {
			return;
		}
		if ("N".equals(request.useYnOrDefault())) {
			throw new BusinessException(ErrorCode.PROTECTED,
					("'%s' 메뉴는 숨길 수 없습니다. 이 메뉴가 사라지면 메뉴와 권한을 되돌릴 방법이 "
							+ "없어집니다.").formatted(before.getMenuName()));
		}
	}

	/**
	 * 사용중지 경고.
	 *
	 * 쓰지 않는 화면을 접어 두는 것은 정당하므로 막지 않는다. 다만 그룹을
	 * 숨기면 그 안의 항목이 통째로 사라지므로, 몇 개가 함께 숨는지 알려준다.
	 */
	private String warnOnDisable(Menu before, MenuSaveRequest request) {
		if (!"Y".equals(before.getUseYn()) || !"N".equals(request.useYnOrDefault())) {
			return null;
		}
		int children = menuDao.countChildren(before.getMenuSeq());
		if (children == 0) {
			return "'%s' 메뉴를 숨겼습니다. 화면 자체는 남아 있어 주소로는 접근할 수 있습니다."
					.formatted(before.getMenuName());
		}
		return ("'%s' 그룹을 숨겼습니다. 하위 메뉴 %d개도 사이드바에서 함께 사라집니다.")
				.formatted(before.getMenuName(), children);
	}

	/* ------------------------------------------------------------------ */

	private Menu mustFind(String menuId) {
		Menu menu = menuDao.selectByMenuId(menuId);
		if (menu == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"메뉴를 찾을 수 없습니다. (%s)".formatted(menuId));
		}
		return menu;
	}

	/** 그룹 머리글은 상위가 없다 */
	private Long seqOf(Menu menu) {
		return menu == null ? null : menu.getMenuSeq();
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(MenuResponse menu, String warning) {
	}
}
