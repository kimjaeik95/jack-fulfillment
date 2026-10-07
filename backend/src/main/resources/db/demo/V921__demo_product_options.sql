-- 신규 설치에서는 데모 SKU가 V43 이후 생성되므로 옵션을 보완한다.
INSERT INTO tb_product_option(product_seq, option_type, option_code, option_name, sort_order, created_by)
SELECT DISTINCT s.product_seq, v.kind, v.code, COALESCE(c.code_name, v.code), COALESCE(c.sort_order, 0), 'system'
  FROM tb_sku s
 CROSS JOIN LATERAL (VALUES ('COLOR', s.color_code), ('SIZE', s.size_code)) v(kind, code)
  LEFT JOIN tb_code_group g ON g.code_group_id = v.kind
  LEFT JOIN tb_code c ON c.code_group_seq = g.code_group_seq AND c.code_id = v.code
ON CONFLICT DO NOTHING;
