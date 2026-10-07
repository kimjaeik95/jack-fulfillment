import {Client,assert,db,num,sql} from './integration-client.mjs';
assert.equal(process.env.IT_DB,'fulfillment_policy');
assert.equal(process.env.IT_BASE,'http://127.0.0.1:18087/api');
const admin=new Client('admin');await admin.login();
const stamp=Date.now().toString().slice(-8),productId='POL-'+stamp;
const template=(await admin.ok('GET','/products?size=1')).rows[0];
await admin.ok('POST','/products',{...template,productId,productName:productId,status:'ACTIVE'});
await admin.ok('PUT',`/products/${productId}/options`,{options:[
 {optionType:'COLOR',optionCode:'BK',optionName:'Black',sortOrder:0},
 {optionType:'SIZE',optionCode:'FREE',optionName:'Free',sortOrder:0}
]});
const body={skuId:productId+'-BK-FREE',productId,colorCode:'BK',sizeCode:'FREE',status:'ACTIVE',sortOrder:0,useYn:'Y'};
await admin.ok('POST','/skus',body);
const sku=num(`select sku_seq from tb_sku where sku_id='${body.skuId}'`);
async function discard(blocked=true){
 if(blocked){const r=await admin.no('PUT','/skus/'+body.skuId,{...body,status:'DISCARDED'});assert.equal(r.code,'POLICY_BLOCKED');assert.equal(db(`select status from tb_sku where sku_seq=${sku}`)[0].status,'ACTIVE');}
 else {await admin.ok('PUT','/skus/'+body.skuId,{...body,status:'DISCARDED'});await admin.ok('PUT','/skus/'+body.skuId,body);}
}
await discard(false);
const loc=db(`select l.location_seq,w.warehouse_seq,w.warehouse_id,p.plant_seq,p.plant_id from tb_location l join tb_warehouse w using(warehouse_seq) join tb_plant p using(plant_seq) where p.plant_id='PL001' order by l.location_seq limit 1`)[0];
const stock=Number(sql(`insert into tb_stock(location_seq,sku_seq,created_by) values(${loc.location_seq},${sku},'admin') returning stock_seq`).split('\n')[0]);
for(const values of ['qty_on_hand=1','qty_on_hand=1,qty_allocated=1','qty_on_hand=1,qty_unsellable=1']){
 sql(`update tb_stock set ${values} where stock_seq=${stock}`);await discard();
 sql(`update tb_stock set qty_on_hand=0,qty_allocated=0,qty_unsellable=0 where stock_seq=${stock}`);
}
// Each pending relation is tested in isolation using a temporary reassignment in this disposable DB.
sql(`with h as (insert into tb_stocktake(take_no,take_name,warehouse_seq,take_type,planned_date,created_by)
 values('TAKE-POL-${stamp}','policy test',${loc.warehouse_seq},'FULL',current_date,'admin') returning take_seq)
 insert into tb_stocktake_line(take_seq,location_seq,sku_seq) select h.take_seq,${loc.location_seq},(select min(sku_seq) from tb_sku) from h`);
sql(`with h as (insert into tb_stock_adjust(adjust_no,warehouse_seq,reason_code,requested_by,created_by)
 values('ADJ-POL-${stamp}',${loc.warehouse_seq},'TEST','dc1.mgr','admin') returning adjust_seq)
 insert into tb_stock_adjust_line(adjust_seq,line_no,stock_seq,qty_field,qty_before,qty_after)
 select h.adjust_seq,1,(select min(stock_seq) from tb_stock),'ON_HAND',0,1 from h`);
const putaway=db(`select p.putaway_seq,i.line_seq,i.sku_seq,i.inbound_seq from tb_inbound_putaway p join tb_inbound_line i using(line_seq) join tb_inbound h using(inbound_seq) where h.inbound_status='DONE' limit 1`)[0];assert(putaway);
sql(`with h as (insert into tb_inbound_correct(correct_no,inbound_seq,reason_code,requested_by,created_by)
 values('INBC-POL-${stamp}',${putaway.inbound_seq},'TEST','dc1.mgr','admin') returning correct_seq)
 insert into tb_inbound_correct_line(correct_seq,line_no,putaway_seq,qty_delta) select h.correct_seq,1,${putaway.putaway_seq},1 from h`);
const cases=[
 ['tb_purchase_request_line','line_seq','sku_seq',`select l.line_seq,l.sku_seq from tb_purchase_request_line l join tb_purchase_request h using(request_seq) where h.request_status='REQUESTED' limit 1`],
 ['tb_purchase_order_line','line_seq','sku_seq',`select l.line_seq,l.sku_seq from tb_purchase_order_line l join tb_purchase_order h using(order_seq) where h.order_status not in ('CLOSED','CANCELED') limit 1`],
 ['tb_inbound_line','line_seq','sku_seq',`select l.line_seq,l.sku_seq from tb_inbound_line l join tb_inbound h using(inbound_seq) where h.inbound_status not in ('DONE','CANCELED') limit 1`],
 ['tb_order_line','line_seq','sku_seq',`select l.line_seq,l.sku_seq from tb_order_line l join tb_order h using(order_seq) where h.order_status not in ('SHIPPED','CANCELED') and l.line_status!='CANCELED' and l.sku_seq is not null limit 1`],
 ['tb_outbound_line','line_seq','sku_seq',`select l.line_seq,l.sku_seq from tb_outbound_line l join tb_outbound h using(outbound_seq) where h.outbound_status not in ('SHIPPED','CANCELED') limit 1`],
 ['tb_stocktake_line','line_seq','sku_seq',`select l.line_seq,l.sku_seq from tb_stocktake_line l join tb_stocktake h using(take_seq) where h.take_status in ('PLANNED','COUNTING') limit 1`],
 ['tb_stock_adjust_line','line_seq','stock_seq',`select l.line_seq,l.stock_seq from tb_stock_adjust_line l join tb_stock_adjust h using(adjust_seq) where h.adjust_status='REQUESTED' limit 1`],
 ['tb_inbound_line','line_seq','sku_seq',`select line_seq,sku_seq from tb_inbound_line where line_seq=${putaway.line_seq}`]
];
for(const [table,key,field,q] of cases){
 const row=db(q)[0];assert(row,`Missing fixture: ${table}`);
 sql(`update ${table} set ${field}=${field==='stock_seq'?stock:sku} where ${key}=${row[key]}`);
 try{await discard();}finally{sql(`update ${table} set ${field}=${row[field]} where ${key}=${row[key]}`);}
 await discard(false);
}
// Approved request with unplaced quantity remains an open business obligation.
const req=Number(sql(`insert into tb_purchase_request(request_no,plant_seq,reason_code,required_date,requested_by,created_by,request_status,decided_by,decided_at) values('REQ-POL-${stamp}',${loc.plant_seq},'TEST',current_date,'dc1.mgr','admin','APPROVED','admin',current_timestamp) returning request_seq`).split('\n')[0]);
sql(`insert into tb_purchase_request_line(request_seq,line_no,sku_seq,request_qty,approved_qty) values(${req},1,${sku},1,1)`);
await discard();sql(`delete from tb_purchase_request where request_seq=${req}`);await discard(false);

// Existing P005 must not block all inspection, but DONE must still block changes.
const worker=new Client('dc1.in01');await worker.login();
assert.equal(db(`select policy_type from tb_policy where policy_id='P005'`)[0].policy_type,'CONDITION');
const inbound=db(`select h.inbound_seq,l.line_seq from tb_inbound h join tb_inbound_line l using(inbound_seq) where h.plant_seq=${loc.plant_seq} and h.inbound_status in ('ARRIVED','INSPECTING') order by h.inbound_seq limit 1`)[0];
assert(inbound,'Missing inspectable inbound');
await worker.ok('POST',`/inbounds/${inbound.inbound_seq}/inspect`,{lines:[{lineSeq:inbound.line_seq,passedQty:1,rejectedQty:0}]});
const done=db(`select h.inbound_seq,l.line_seq from tb_inbound h join tb_inbound_line l using(inbound_seq) where h.plant_seq=${loc.plant_seq} and h.inbound_status='DONE' limit 1`)[0];assert(done);
await worker.no('POST',`/inbounds/${done.inbound_seq}/inspect`,{lines:[{lineSeq:done.line_seq,passedQty:1,rejectedQty:0}]});

const policy=await admin.ok('GET','/policies/P007');
await admin.no('PUT','/policies/P007',{...policy,limitAmount:5000000});
await admin.no('PUT','/policies/P007',{...policy,permId:'PUR_REQ_APPROVE'});
await admin.no('PUT','/policies/P007',{...policy,enforceLevel:'WARN'});
await admin.no('PUT','/policies/P007',{...policy,limitQty:0});
const inboundPolicy=await admin.ok('GET','/policies/P005');
await admin.no('PUT','/policies/P005',{...inboundPolicy,policyType:'DENY'});
// Changed threshold is applied after login. Boundary allowed, over-limit rolled back.
await admin.ok('PUT','/policies/P007',{...policy,limitQty:2,limitAmount:null});
const mgr=new Client('dc1.mgr');await mgr.login();
const reason=db(`select c.code_id from tb_code c join tb_code_group g using(code_group_seq) where g.code_group_id='REASON_ADJUST' and c.use_yn='Y' order by c.sort_order limit 1`)[0].code_id;
const create=async qty=>admin.ok('POST','/stock-adjusts',{plantId:loc.plant_id,warehouseId:loc.warehouse_id,reasonCode:reason,lines:[{stockSeq:stock,qtyField:'ON_HAND',qtyAfter:qty}]});
const over=await create(3);const beforeHistory=num(`select count(*) from tb_stock_history where stock_seq=${stock}`);
await mgr.no('POST',`/stock-adjusts/${over.adjustSeq}/approve`,{});
assert.equal(num(`select qty_on_hand from tb_stock where stock_seq=${stock}`),0);
assert.equal(num(`select count(*) from tb_stock_history where stock_seq=${stock}`),beforeHistory);
assert.equal(db(`select adjust_status from tb_stock_adjust where adjust_seq=${over.adjustSeq}`)[0].adjust_status,'REQUESTED');
await admin.no('POST',`/stock-adjusts/${over.adjustSeq}/approve`,{}); // self approval
const boundary=await create(2);await mgr.ok('POST',`/stock-adjusts/${boundary.adjustSeq}/approve`,{});
assert.equal(num(`select qty_on_hand from tb_stock where stock_seq=${stock}`),2);
const loc2=num(`select location_seq from tb_location where warehouse_seq=${loc.warehouse_seq} and location_seq!=${loc.location_seq} order by location_seq limit 1`);
assert(loc2);
const stock2=Number(sql(`insert into tb_stock(location_seq,sku_seq,created_by) values(${loc2},${sku},'admin') returning stock_seq`).split('\n')[0]);
const huge=await admin.ok('POST','/stock-adjusts',{plantId:loc.plant_id,warehouseId:loc.warehouse_id,reasonCode:reason,
 lines:[{stockSeq:stock,qtyField:'ON_HAND',qtyAfter:2000000002},{stockSeq:stock2,qtyField:'ON_HAND',qtyAfter:2000000000}]});
const hugeResult=await mgr.no('POST',`/stock-adjusts/${huge.adjustSeq}/approve`,{});assert.equal(hugeResult.code,'POLICY_BLOCKED');
assert.equal(num(`select qty_on_hand from tb_stock where stock_seq=${stock2}`),0);
await admin.ok('PUT','/policies/P007',{...policy,limitAmount:null});
console.log('PASS: discard stock/pending/clean paths, normal and completed inspection, unsupported policy settings, quantity boundary/overflow/rollback, self approval');
