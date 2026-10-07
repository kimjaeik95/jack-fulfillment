// Run against an isolated clone with test_history_before/test_stock_before captured before V42.
import {assert, Client, db, num, snap} from './integration-client.mjs';

assert.equal(process.env.IT_DB, 'fulfillment_def', 'Use the isolated fulfillment_def test database only.');
assert.equal(num(`select count(*) from tb_stock_history h join test_history_before b using(history_seq)
 where (to_jsonb(h) - 'ref_type' - 'ref_no') <> (to_jsonb(b) - 'ref_type' - 'ref_no')`), 0);
assert.equal(num(`select count(*) from tb_stock s join test_stock_before b using(stock_seq)
 where to_jsonb(s) <> to_jsonb(b)`), 0);
const legacy = db(`select h.* from tb_stock_history h join test_history_before b using(history_seq)
 where b.move_type='UNSELLABLE' and b.qty_field='UNSELLABLE' and b.ref_no is null and b.ref_type is null`);
assert(legacy.length > 0);
for (const h of legacy) {
  assert.equal(h.ref_type, 'UNSELLABLE');
  assert(h.ref_no.startsWith('DEF-' + h.occurred_at.slice(0,10).replaceAll('-','') + '-'));
}
assert.equal(new Set(legacy.map(h=>h.ref_no)).size, legacy.length);
assert.equal(num(`select count(*) from tb_stock_history h join test_history_before b using(history_seq)
 where b.ref_no is not null and (h.ref_no is distinct from b.ref_no or h.ref_type is distinct from b.ref_type)`),0);

const c = new Client('admin'); await c.login();
const s = db('select * from tb_stock where qty_available >= 12 order by stock_seq limit 1')[0];
assert(s);
const body = {stockSeq:s.stock_seq, direction:'TO_UNSELLABLE', qty:1, reasonCode:'DAMAGED'};
const responses = await Promise.all(Array.from({length:8},()=>c.ok('POST','/stocks/unsellable',body)));
const refs=responses.map(r=>r.refNo);
assert.equal(new Set(refs).size,8);
for (const r of responses) {
  assert.match(r.refNo,/^DEF-\d{8}-\d{4,}$/);
  const h=db(`select * from tb_stock_history where ref_no='${r.refNo}'`);
  assert.equal(h.length,1); assert.equal(h[0].ref_type,'UNSELLABLE'); assert.equal(h[0].qty_delta,1);
}
const restored=await c.ok('POST','/stocks/unsellable',{...body,direction:'TO_NORMAL',qty:8,reasonCode:'REPAIRED'});
assert(!refs.includes(restored.refNo));
assert.equal(restored.stocks[0].qtyUnsellable,s.qty_unsellable);
assert.equal(restored.stocks[0].qtyOnHand,s.qty_on_hand);
const counters=db("select * from tb_doc_number where doc_type='DEF' order by doc_date");
const count=num('select count(*) from tb_stock_history');
await c.no('POST','/stocks/unsellable',{...body,qty:s.qty_available+1});
assert.deepEqual(db("select * from tb_doc_number where doc_type='DEF' order by doc_date"),counters);
assert.equal(num('select count(*) from tb_stock_history'),count);
const date=restored.refNo.slice(4,12).replace(/(\d{4})(\d{2})(\d{2})/,'$1-$2-$3');
const page=await c.ok('GET',`/stocks/history?fromDate=${date}&toDate=${date}&refType=UNSELLABLE&refNo=${restored.refNo}`);
assert.equal(page.rows.length,1); assert.equal(page.rows[0].refNo,restored.refNo);
snap('불량 전표 검증',"select ref_type,ref_no,qty_delta from tb_stock_history where ref_type='UNSELLABLE' order by history_seq");
console.log(JSON.stringify({legacyBackfilled:legacy.length,concurrentUnique:refs.length,restored:restored.refNo,rollback:'PASS',historyFilter:'PASS',quantityPreserved:'PASS'}));
