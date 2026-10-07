package com.fulfillment.master.product.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.master.product.dao.ProductDao;
import com.fulfillment.master.product.dao.ProductOptionDao;
import com.fulfillment.master.product.dto.ProductOption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ProductOptionService {
    private final ProductOptionDao dao;
    private final ProductDao products;
    private final PermissionChecker permissions;
    private final AuditRecorder audit;

    public ProductOptionService(ProductOptionDao dao, ProductDao products, PermissionChecker permissions, AuditRecorder audit) {
        this.dao = dao; this.products = products; this.permissions = permissions; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProductOption> list(LoginUser actor, String id) {
        if (!permissions.can(actor, "MST_SKU", "R") && !permissions.can(actor, "MST_SKU", "C"))
            permissions.require(actor, "MST_PRODUCT", "R");
        if (products.selectByProductId(id) == null) throw new BusinessException(ErrorCode.NOT_FOUND, "스타일을 찾을 수 없습니다.");
        return dao.list(id);
    }

    /**
     * 옵션 코드 추천 (MST-PG-00x).
     *
     * 스타일 옵션 화면이 코드를 칠 때 부른다. 이미 쓰이고 있는 값에서 뽑으므로
     * 사전 테이블이 없고, 없는 값은 그냥 치면 된다 — 막다른 길을 만들지 않는
     * 것이 이 기능의 요점이다.
     *
     * 권한은 목록과 같게 둔다. 추천은 '어떤 색이 쓰이고 있나' 를 보는 것이라
     * 스타일 옵션을 볼 수 있으면 같이 볼 수 있어야 한다.
     */
    @Transactional(readOnly = true)
    public List<ProductOptionDao.OptionSuggestion> suggest(LoginUser actor, String type, String q) {
        if (!permissions.can(actor, "MST_SKU", "R") && !permissions.can(actor, "MST_SKU", "C"))
            permissions.require(actor, "MST_PRODUCT", "R");
        if (!"COLOR".equals(type) && !"SIZE".equals(type))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "옵션 종류는 COLOR 또는 SIZE 입니다.");
        String keyword = q == null || q.isBlank() ? null : q.trim();
        return dao.suggest(type, keyword, 20);
    }

    @Transactional
    public List<ProductOption> save(LoginUser actor, String id, List<ProductOption> options) {
        permissions.require(actor, "MST_PRODUCT", "U");
        Long seq = dao.lockProduct(id);
        if (seq == null) throw new BusinessException(ErrorCode.NOT_FOUND, "스타일을 찾을 수 없습니다.");
        Set<String> keys = new HashSet<>();
        for (ProductOption o : options) {
            if (!keys.add(o.optionType() + ":" + o.optionCode()))
                throw new BusinessException(ErrorCode.DUPLICATE, "같은 종류의 옵션코드를 중복 등록할 수 없습니다.");
        }
        List<ProductOption> before = dao.list(id);
        for (ProductOption o : before) {
            if (!keys.contains(o.optionType() + ":" + o.optionCode())) {
                if (dao.used(seq, o.optionType(), o.optionCode()) > 0)
                    throw new BusinessException(ErrorCode.IN_USE, "SKU에서 사용 중인 옵션은 삭제하거나 코드를 바꿀 수 없습니다. (" + o.optionCode() + ")");
                dao.delete(seq, o.optionType(), o.optionCode());
            }
        }
        for (ProductOption o : options) dao.save(seq, o, actor.getUserId());
        audit.recordUpdate(actor, "tb_product_option", id, before, options,
                List.of(new AuditRecorder.Field<List<ProductOption>>("options", Object::toString)), "스타일 옵션 변경");
        return dao.list(id);
    }

    /** SKU 저장과 옵션 제거는 같은 스타일 행을 잠가 경합을 막는다. */
    public void requireAllowed(String id, List<String> colors, List<String> sizes, boolean lock) {
        if (lock && dao.lockProduct(id) == null) throw new BusinessException(ErrorCode.NOT_FOUND, "스타일을 찾을 수 없습니다.");
        List<ProductOption> allowed = dao.list(id);
        check(id, allowed, "COLOR", colors);
        check(id, allowed, "SIZE", sizes);
    }

    private void check(String id, List<ProductOption> allowed, String type, List<String> codes) {
        for (String code : codes) {
            if (allowed.stream().noneMatch(o -> o.optionType().equals(type) && o.optionCode().equals(code)))
                throw new BusinessException(ErrorCode.INVALID_INPUT, "%s 스타일에 등록되지 않은 %s입니다. (%s) 스타일 옵션을 먼저 등록하세요."
                        .formatted(id, type.equals("COLOR") ? "색상" : "사이즈", code));
        }
    }
}
