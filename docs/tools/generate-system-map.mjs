import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
const here=path.dirname(fileURLToPath(import.meta.url)),root=path.resolve(here,'../..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const walk=p=>fs.readdirSync(path.join(root,p),{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(p+'/'+e.name):[p+'/'+e.name]);
const groups=[
 {id:'auth',number:'01',name:'인증 · 접근',desc:'사용자를 인증하고 세션과 비밀번호를 관리합니다.',flow:['로그인','비밀번호 변경','권한에 따른 업무 진입'],prefix:[],backend:'system',tables:['tb_user','tb_role','tb_user_role','tb_permission','tb_role_permission']},
 {id:'system',number:'02',name:'시스템 · 공통관리',desc:'회사와 조직, 사용자 권한, 공통코드와 감사 기준을 설정합니다.',flow:['회사 · 조직','사용자 · 역할','권한 · 정책','메뉴 · 공통코드','업로드 · 감사'],prefix:['COM'],backend:'system',tables:['tb_company','tb_org','tb_user','tb_role','tb_user_role','tb_permission','tb_permission_action','tb_role_permission','tb_role_org_scope','tb_policy','tb_menu','tb_code_group','tb_code','tb_audit_log','tb_audit_log_detail','tb_upload_history','tb_upload_error']},
 {id:'master',number:'03',name:'기준정보',desc:'물류 거점, 제품과 SKU, 거래처 및 판매채널을 정의합니다.',flow:['플랜트 → 창고 → 빈','카테고리 · 브랜드 → 제품 → SKU','공급처 · 고객','판매채널 → SKU 매핑'],prefix:['MST'],backend:'master',tables:['tb_plant','tb_warehouse','tb_location','tb_category','tb_brand','tb_product','tb_sku','tb_channel','tb_channel_sku','tb_supplier','tb_customer','tb_customer_address']},
 {id:'purchase',number:'04',name:'구매 · 발주',desc:'필요 수량을 요청하고 결재한 뒤 공급처에 발주합니다.',flow:['구매요청','구매요청 결재','구매오더 · 발주','발주 진행현황'],prefix:['PUR'],backend:'purchase',tables:['tb_purchase_request','tb_purchase_request_line','tb_purchase_order','tb_purchase_order_line']},
 {id:'inbound',number:'05',name:'입고 · 검수 · 적치',desc:'입고예정부터 검수와 적치를 거쳐 입고완료 시 재고에 반영합니다.',flow:['입고예정','입하 등록','입고검수','초과입고 승인 (필요 시)','적치 · 입고완료','입고정정 요청 → 승인 (필요 시)'],prefix:['INB'],backend:'inbound',tables:['tb_inbound','tb_inbound_line','tb_inbound_inspect','tb_inbound_putaway','tb_inbound_correct','tb_inbound_correct_line']},
 {id:'inventory',number:'06',name:'재고 · 이동 · 실사',desc:'재고를 조회하고 이동·상태전환·조정·실사 결과를 원장으로 기록합니다.',flow:['재고 현황 · 이력 조회','이동 / 판매불가 전환','조정 요청 → 승인','실사 계획 → 입력 → 마감','재고 대사'],prefix:['INV'],backend:'inventory',tables:['tb_stock','tb_stock_history','tb_stock_alloc','tb_stock_adjust','tb_stock_adjust_line','tb_stocktake','tb_stocktake_line','tb_doc_number']},
 {id:'planned',number:'07',name:'후속 업무 · 요구사항',desc:'현재 업무 모듈 외의 프로그램을 요구사항 문서 기준으로 분류합니다. 문서 상태는 실제 구현 확인과 구분합니다.',flow:['주문 · 할당','피킹 · 패킹 · 출고','반품 및 기타 후속 업무'],prefix:[],tables:[]},
 {id:'shared',number:'08',name:'공통 기술 · 실행구조',desc:'화면부터 API, 업무 처리, 데이터 접근과 DB까지 연결되는 공통 구조입니다.',flow:['Vue 화면','API 클라이언트','Spring Controller','Service','DAO · MyBatis XML','PostgreSQL'],prefix:[],tables:[]}
];
for(const g of groups){
 g.routes=[];g.folders=[];
 const module=`frontend/src/modules/${g.id}`;
 if(fs.existsSync(path.join(root,module,'routes.js'))){
  const sql=read(module+'/routes.js').replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g,'');
  const re=/path:\s*'([^']+)'[\s\S]*?component:\s*\(\)\s*=>\s*import\('([^']+)'\)[\s\S]*?meta:\s*\{\s*title:\s*'([^']+)'([^}]*)\}/g;
  g.routes=[...sql.matchAll(re)].map((m,i)=>({number:`${g.number}.${String(i+1).padStart(2,'0')}`,route:m[1],title:m[3],permission:m[4].match(/perm:\s*'([^']+)'/)?.[1]||'',file:path.posix.normalize(module+'/'+m[2])}));
  assert.equal(g.routes.length,(sql.match(/component:/g)||[]).length);
  g.folders.push({label:'화면 · 프론트엔드',path:module,files:walk(module)});
 }
 if(g.backend&&g.id!=='auth')for(const [label,p] of [['업무 처리 · 백엔드',`backend/src/main/java/com/fulfillment/${g.backend}`],['SQL 매퍼',`backend/src/main/resources/mapper/${g.backend}`]])if(fs.existsSync(path.join(root,p)))g.folders.push({label,path:p,files:walk(p)});
 if(g.id==='auth')g.folders.push({label:'인증 구현 포함 · 시스템 백엔드',path:'backend/src/main/java/com/fulfillment/system',files:walk('backend/src/main/java/com/fulfillment/system')});
 if(g.id==='shared')for(const p of ['frontend/src/api','frontend/src/components','frontend/src/composables','frontend/src/router','frontend/src/stores','backend/src/main/java/com/fulfillment/common','backend/src/main/java/com/fulfillment/domain','backend/src/main/resources/db/migration'])g.folders.push({label:p.split('/').at(-1),path:p,files:walk(p)});
}
const programs=read('docs/PROGRAMS.md').split('\n').filter(l=>/^\|\s*\d+\s*\|/.test(l)).map(l=>l.split('|').slice(1,-1).map(s=>s.trim())).filter(c=>/^[A-Z]+-(PG|BT|IF)-\d+$/.test(c[2])).map(c=>({order:c[0],area:c[1],id:c[2],name:c[3],type:c[4],phase:c[6],role:c[7],description:c[8],before:c[9],status:c[10]}));
const prefixes=groups.flatMap(g=>g.prefix);
for(const g of groups)g.programs=programs.filter(p=>g.id==='planned'?!prefixes.includes(p.id.split('-')[0]):g.prefix.includes(p.id.split('-')[0]));
assert(programs.length>0);assert.equal(groups.reduce((n,g)=>n+g.programs.length,0),programs.length);
const movements=[['입고','RECEIVE','적치 · 입고완료','입고완료 시','inbound','/inbound-putaway'],['조정','ADJUST','재고조정 승인 / 재고실사','조정 승인 / 실사 마감 시 차이 수량 반영','inventory','/stock-adjust-approve'],['입고정정','CORRECT','입고정정 승인','정정 승인 시','inbound','/inbound-correct-approve'],['이동','MOVE','로케이션 이동','이동 실행 시 출발지 차감 · 도착지 증가','inventory','/stock-move'],['판매불가','UNSELLABLE','판매불가 전환','판매불가 전환 / 정상 복귀 시','inventory','/stock-unsellable'],['출고 / 반품 / 할당 / 할당해제','ISSUE / RETURN / ALLOCATE / RELEASE','현재 생성 화면 미구현','코드값 정의만 확인','planned','']];
const data={groups,programs,movements,routeCount:groups.reduce((n,g)=>n+g.routes.length,0)};
for(const g of groups)for(const r of g.routes)assert(fs.existsSync(path.join(root,r.file)),r.file);
fs.writeFileSync(path.join(root,'docs/system-map.html'),fs.readFileSync(path.join(here,'system-map-template.html'),'utf8').replace('/*__DATA__*/',()=>JSON.stringify(data).replace(/</g,'\\u003c')));
console.log(JSON.stringify({groups:groups.length,screens:data.routeCount,programs:programs.length,output:'docs/system-map.html'}));
