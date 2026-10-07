-- 색상/사이즈는 스타일이 직접 소유한다. 공통코드와 연결하지 않는다.
CREATE TABLE tb_product_option (
    product_seq bigint NOT NULL,
    option_type varchar(10) NOT NULL CHECK (option_type IN ('COLOR', 'SIZE')),
    option_code varchar(20) NOT NULL,
    option_name varchar(100) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    created_by varchar(30) NOT NULL,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by varchar(30),
    updated_at timestamp,
    CONSTRAINT pk_product_option PRIMARY KEY (product_seq, option_type, option_code),
    CONSTRAINT fk_product_option_product FOREIGN KEY (product_seq) REFERENCES tb_product(product_seq) ON DELETE CASCADE
);
COMMENT ON TABLE tb_product_option IS '스타일별 허용 색상 · 사이즈';

-- 기존 SKU가 실제 사용하는 옵션만 이관한다. 이름은 이관 시 한 번만 복사한다.
INSERT INTO tb_product_option(product_seq, option_type, option_code, option_name, sort_order, created_by)
SELECT DISTINCT s.product_seq, v.kind, v.code, COALESCE(c.code_name, v.code), COALESCE(c.sort_order, 0), 'system'
  FROM tb_sku s
 CROSS JOIN LATERAL (VALUES ('COLOR', s.color_code), ('SIZE', s.size_code)) v(kind, code)
  LEFT JOIN tb_code_group g ON g.code_group_id = v.kind
  LEFT JOIN tb_code c ON c.code_group_seq = g.code_group_seq AND c.code_id = v.code;

UPDATE tb_code_group SET use_yn = 'N', updated_by = 'system', updated_at = CURRENT_TIMESTAMP,
       description = '스타일 옵션으로 이관됨. 색상·사이즈는 스타일 화면에서 관리합니다.'
 WHERE code_group_id IN ('COLOR', 'SIZE');
