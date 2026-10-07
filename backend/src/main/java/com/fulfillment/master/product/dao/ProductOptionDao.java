package com.fulfillment.master.product.dao;

import com.fulfillment.master.product.dto.ProductOption;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface ProductOptionDao {
    @Select("SELECT product_seq FROM tb_product WHERE product_id = #{id} FOR UPDATE")
    Long lockProduct(String id);

    @Select("SELECT o.option_type, o.option_code, o.option_name, o.sort_order FROM tb_product_option o JOIN tb_product p USING(product_seq) WHERE p.product_id = #{id} ORDER BY o.option_type, o.sort_order, o.option_code")
    List<ProductOption> list(String id);

    @Select("SELECT option_type, option_code, option_name, sort_order FROM tb_product_option WHERE product_seq = #{seq} ORDER BY sort_order, option_code")
    List<ProductOption> listBySeq(Long seq);

    @Select("SELECT count(*) FROM tb_sku WHERE product_seq = #{seq} AND ((#{type} = 'COLOR' AND color_code = #{code}) OR (#{type} = 'SIZE' AND size_code = #{code}))")
    int used(@Param("seq") Long seq, @Param("type") String type, @Param("code") String code);

    @Delete("DELETE FROM tb_product_option WHERE product_seq = #{seq} AND option_type = #{type} AND option_code = #{code}")
    void delete(@Param("seq") Long seq, @Param("type") String type, @Param("code") String code);

    @Insert("INSERT INTO tb_product_option(product_seq, option_type, option_code, option_name, sort_order, created_by) VALUES(#{seq}, #{o.optionType}, #{o.optionCode}, #{o.optionName}, #{o.sortOrder}, #{actor}) ON CONFLICT(product_seq, option_type, option_code) DO UPDATE SET option_name = EXCLUDED.option_name, sort_order = EXCLUDED.sort_order, updated_by = #{actor}, updated_at = CURRENT_TIMESTAMP")
    void save(@Param("seq") Long seq, @Param("o") ProductOption option, @Param("actor") String actor);

    /**
     * 이미 쓰이고 있는 옵션을 모아 추천한다.
     *
     * <b>사전 테이블을 따로 두지 않는다.</b> 색상·사이즈를 공통코드에서 끊어
     * 낸 것은 시즌 색이 나올 때마다 기준정보 담당을 거치지 않으려는 것이었다
     * (V43). 그런데 아무 도움도 없으면 담당자가 'BK' 인지 'BLK' 인지 기억해
     * 적게 되고, 같은 검정이 스타일마다 다른 코드가 된다 — SKU 코드가 그
     * 값으로 조립되므로 그대로 번진다.
     *
     * 그래서 <b>쌓인 것이 곧 목록</b>이 되게 한다. 처음 쓴 사람이 BK 로
     * 넣으면 다음 사람에게 BK 가 보이고, 쓸수록 위로 올라간다. 강제가 아니라
     * 기본값으로 모으는 방식이라 새 색을 막지 않는다.
     *
     * 코드와 이름을 함께 찾는다. 담당자는 '블랙' 은 알아도 'BK' 는 모른다.
     *
     * 표기가 갈린 것은 숨기지 않고 나란히 보여 준다 — 'BK 블랙 4개' 옆에
     * 'BLK 블랙 1개' 가 뜨면 고르는 순간 눈에 띈다.
     */
    /*
     * 검색어가 없을 때 조건을 아예 빼는 이유.
     *
     * '#{q} IS NULL OR ...' 로 두면 검색어가 없을 때 PostgreSQL 이 바인드
     * 파라미터의 타입을 정하지 못해 터진다 — 같은 ? 가 IS NULL 과 문자열
     * 이어붙이기 양쪽에 쓰여서다. 조건을 넣고 빼는 쪽이 짧고 질의도 가볍다.
     */
    @Select("""
            <script>
            SELECT option_code, option_name, count(*)::int AS usedCount
              FROM tb_product_option
             WHERE option_type = #{type}
            <if test="q != null">
               AND (option_code ILIKE '%' || #{q} || '%'
                 OR option_name ILIKE '%' || #{q} || '%')
            </if>
             GROUP BY option_code, option_name
             ORDER BY count(*) DESC, option_code
             LIMIT #{limit}
            </script>
            """)
    List<OptionSuggestion> suggest(@Param("type") String type, @Param("q") String q,
            @Param("limit") int limit);

    /** 추천 한 줄 — 코드 · 이름 · 몇 스타일이 쓰는지 */
    record OptionSuggestion(String optionCode, String optionName, int usedCount) {}
}
