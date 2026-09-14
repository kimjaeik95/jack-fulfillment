-- ============================================================================
-- 데모 빈 바코드를 새 체계로 맞춘다 (로컬 전용)
--
-- V7 로 빈코드가 창고 안에서만 유일해지면서, 스캔 한 번으로 한 곳을 지목하는
-- 역할은 바코드가 혼자 지게 되었다. 그래서 바코드에 센터 · 창고를 담는다.
--
--   전:  1A-01-01  →  LOC1A0101        빈코드만. 다른 센터와 겹칠 수 있다.
--   후:  1A-01-01  →  PL001GD1A0101    센터 · 창고 · 빈. 전사에서 하나다.
--
-- 구분자(-)를 빼는 이유는 바코드가 기계용이기 때문이다. 글자가 줄면 바가
-- 굵어져 스캔이 더 잘 되고, 사람은 라벨에 크게 인쇄된 '1A-01-01' 을 읽는다.
--
-- V900 을 고치지 않고 새 파일로 두는 이유는 이미 적용된 마이그레이션을
-- 수정하면 Flyway 체크섬이 깨져 기존 로컬 DB 가 기동하지 않기 때문이다.
-- ============================================================================

UPDATE tb_location l
   SET barcode    = p.plant_id || w.warehouse_id || REPLACE(l.location_id, '-', ''),
       updated_by = 'system',
       updated_at = CURRENT_TIMESTAMP
  FROM tb_warehouse w, tb_plant p
 WHERE w.warehouse_seq = l.warehouse_seq
   AND p.plant_seq     = w.plant_seq
   AND l.barcode IS NOT NULL;
