import {clients,state,test,assert,snap,record,results} from './integration-client.mjs';
const cs=await clients(['admin','dc1.mgr','dc2.inv01']);
await test('E2E-007,INB-007',async()=>{
 const rows=snap('검수회차 원본',`select i.* from tb_inbound_inspect i join tb_inbound_line l on l.line_seq=i.line_seq where l.inbound_seq=${state.inbound.inboundSeq}`);
 assert.equal(rows.length,2);assert.equal(rows.reduce((a,r)=>a+r.passed_qty,0),97);assert.equal(rows.reduce((a,r)=>a+r.rejected_qty,0),3);
 return '원본 검수2회 합격60+37=97·거부3 확인; 정정 후 라인 received_qty=92. 초회 자동화의 passed_qty 컬럼명 오류 수정.';
});
await test('COM-004',async()=>{
 await cs['dc2.inv01'].no('GET',`/inbounds/${state.inbound.inboundSeq}`);
 const r=await cs['dc2.inv01'].ok('GET','/stocks?skuId=IT26-E2E-BK-M');console.log('stock-response',JSON.stringify(r).slice(0,1500));
 assert.equal((r.rows||r.page?.rows||r).length,0);return '김해 재고담당의 이천 입고상세403·전용 SKU 재고목록0';
});
await test('INB-014',async()=>{await cs['dc1.mgr'].no('DELETE',`/inbounds/${state.inbound.inboundSeq}?reason=TEST`,undefined,/입하|완료|취소/);return '취소권한 있는 센터관리자로 입하된 입고 취소 요청; 업무규칙에 의해 거부';});
