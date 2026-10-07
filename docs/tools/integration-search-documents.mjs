// Uses the isolated integration clone populated by the earlier regression run.
import {assert, Client, db, dir, root} from './integration-client.mjs';
import fs from 'node:fs';
import path from 'node:path';
assert.equal(process.env.IT_DB, 'fulfillment_def');

const samples = db(`
 select 'PUR_REQUEST' kind, request_no no from tb_purchase_request
 union all select 'PUR_ORDER', order_no from tb_purchase_order
 union all select 'INBOUND', inbound_no from tb_inbound
 union all select 'INBOUND_CORRECT', correct_no from tb_inbound_correct
 union all select 'ORDER', order_no from tb_order
 union all select 'OUTBOUND', outbound_no from tb_outbound
 union all select 'ADJUST', adjust_no from tb_stock_adjust
 union all select 'STOCKTAKE', take_no from tb_stocktake
 union all select distinct ref_type, ref_no from tb_stock_history where ref_type in ('MOVE','UNSELLABLE')
 union all select 'WAYBILL', waybill_no from tb_waybill`);
const a = new Client('admin'); await a.login();
const hits=[];
for (const kind of new Set(samples.map(s=>s.kind))) {
  const s=samples.find(s=>s.kind===kind);
  const found=await a.ok('GET','/search?q='+encodeURIComponent(s.no.toLowerCase()));
  const matches=found.hits.filter(h=>h.kind===kind && h.no===s.no);
  assert.equal(matches.length,1,`${kind}: exactly one document result`);
  const hit=matches[0]; hits.push(hit);
  const chain=await a.ok('GET',`/search/chain?kind=${hit.kind}&seq=${hit.seq}`);
  if (['INBOUND_CORRECT','ADJUST','STOCKTAKE','MOVE','UNSELLABLE'].includes(kind)) assert(chain == null);
  console.log('SEARCH',kind,hit.no);
}
assert.equal(hits.length,11); // 10 document prefixes plus courier waybills
const restricted=new Client('dc2.mgr'); await restricted.login();
for (const hit of hits.filter(h=>['INBOUND_CORRECT','ADJUST','STOCKTAKE','MOVE','UNSELLABLE'].includes(h.kind))) {
  const r=await restricted.ok('GET','/search?q='+hit.no);
  assert.equal(r.hits.length,0,'Other-center documents must be hidden');
  await restricted.no('GET',`/search/chain?kind=${hit.kind}&seq=${hit.seq}`);
}
const no=await a.ok('GET','/search?q=DEF-19000101-9999'); assert.equal(no.hits.length,0);
fs.writeFileSync(path.join(dir,'search-hits.json'),JSON.stringify(hits,null,2));
console.log('API_PASS',hits.length,'kinds, cross-center search and direct access blocked');

if (process.env.IT_BROWSER !== '1') process.exit(0);
const {createServer}=await import('../../frontend/node_modules/vite/dist/node/index.js');
const {chromium}=await import('../../frontend/node_modules/playwright-core/index.mjs');
const server=await createServer({root:path.join(root,'frontend'),configFile:path.join(root,'frontend/vite.config.js'),server:{host:'127.0.0.1',port:15173,strictPort:true,open:false,proxy:{'/api':{target:'http://127.0.0.1:18080',changeOrigin:false}}}});
await server.listen();
const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
try {
  const page=await browser.newPage({viewport:{width:1600,height:1000}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:15173/#/login');
  await page.locator('#login-id').fill('admin');await page.locator('#login-pw').fill('wms1234!');
  await page.locator('button[type=submit]').click();await page.waitForURL(u=>!u.hash.includes('login'));
  for (const hit of hits.filter(h=>['INBOUND_CORRECT','ADJUST','STOCKTAKE','MOVE','UNSELLABLE'].includes(h.kind))) {
    await page.goto('http://127.0.0.1:15173/#/search');
    await page.locator('main input').first().fill(hit.no);
    await page.getByRole('button',{name:'찾기',exact:true}).click();
    const row=page.locator('.hit').filter({hasText:hit.no});await row.waitFor();await row.click();
    await page.waitForURL(u=>u.hash.includes(hit.route));
    await page.waitForLoadState('networkidle');
    try { await page.locator('main').getByText(hit.no,{exact:true}).first().waitFor({timeout:15000}); }
    catch(e) { fs.writeFileSync(path.join(dir,'search-ui-failure.txt'),await page.locator('body').innerText()); throw e; }
    await page.screenshot({path:path.join(dir,`search-${hit.kind}.png`),fullPage:true});
    console.log('UI',hit.kind,'destination visible');
  }
  // Same-view navigation must replace the previous MOV filter with DEF.
  const def=hits.find(h=>h.kind==='UNSELLABLE');
  const mov=hits.find(h=>h.kind==='MOVE');
  for (const hit of [mov,def]) {
    await page.getByTitle('통합검색 (Ctrl+K)').click();
    await page.locator('.gs-input input').fill(hit.no);
    await page.locator('.gs-input button').click();
    const row=page.locator('.gs-list .gs-row').filter({hasText:hit.no});await row.waitFor();await row.click();
    await page.waitForURL(u=>u.hash.includes('refNo='+hit.no));
    await page.waitForLoadState('networkidle');
    await page.locator('main tbody').getByText(hit.no,{exact:true}).first().waitFor({timeout:15000});
    const rowTexts=await page.locator('main tbody tr:visible').allInnerTexts();
    assert(rowTexts.length > 0 && rowTexts.every(t=>t.includes(hit.no)));
    console.log('GLOBAL_SEARCH',hit.kind,'single result and same-view filters passed');
  }
  assert.deepEqual(errors,[]);
  console.log('BROWSER_PASS');
} finally {await browser.close();await server.close();}
