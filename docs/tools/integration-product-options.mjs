import {Client,assert,db,num,dir,root} from './integration-client.mjs';
import fs from 'node:fs';
import path from 'node:path';
assert.equal(process.env.IT_DB,'fulfillment_options');
assert.equal(process.env.IT_BASE,'http://127.0.0.1:18085/api');
const a=new Client('admin'); await a.login();
assert.equal(num(`select count(*) from tb_sku s where not exists(select 1 from tb_product_option o where o.product_seq=s.product_seq and o.option_type='COLOR' and o.option_code=s.color_code) or not exists(select 1 from tb_product_option o where o.product_seq=s.product_seq and o.option_type='SIZE' and o.option_code=s.size_code)`),0);
const template=(await a.ok('GET','/products?size=1')).rows[0];
const stamp=Date.now().toString().slice(-7),bag='OPB-'+stamp,tee='OPT-'+stamp;
for(const id of [bag,tee]) await a.ok('POST','/products',{...template,productId:id,productName:id,status:'ACTIVE'});
const option=(type,code,name=code)=>({optionType:type,optionCode:code,optionName:name,sortOrder:0});
const bagOptions=[option('COLOR','INK','잉크블랙'),option('SIZE','FREE','프리')];
const teeOptions=[option('COLOR','INK','먹색'),option('SIZE','M','미디엄'),option('SIZE','L','라지')];
for(const [id,options] of [[bag,bagOptions],[tee,teeOptions]]) await a.ok('PUT',`/products/${id}/options`,{options});
const body={skuId:bag+'-INK-FREE',productId:bag,colorCode:'INK',sizeCode:'FREE',status:'ACTIVE',sortOrder:0,useYn:'Y'};
const sku=await a.ok('POST','/skus',body); assert.equal(sku.colorName,'잉크블랙');
await a.no('POST','/skus',{...body,skuId:bag+'-INK-M',sizeCode:'M'});
await a.no('PUT','/skus/'+sku.skuId,{...body,sizeCode:'M'});
await a.no('PUT',`/products/${bag}/options`,{options:[bagOptions[0]]});
await a.no('PUT',`/products/${bag}/options`,{options:[...bagOptions,bagOptions[0]]});
await a.no('PUT',`/products/${bag}/options`,{options:[option('COLOR','lower'),bagOptions[1]]});
const invalid={items:[{productId:tee,colorCodes:['INK'],sizeCodes:['FREE'],status:'ACTIVE'}]};
await a.no('POST','/skus/bulk/preview',invalid);await a.no('POST','/skus/bulk',invalid);
const valid={items:[{productId:tee,colorCodes:['INK'],sizeCodes:['M','L'],status:'ACTIVE'}]};
const preview=await a.ok('POST','/skus/bulk/preview',valid);assert.equal(preview.creatableCount,2);
const made=await a.ok('POST','/skus/bulk',valid);assert.equal(made.createdCount,2);
assert.equal((await a.ok('GET','/skus/'+tee+'-INK-M')).colorName,'먹색');
// A concurrent SKU registration and option removal can never leave a dangling option.
for(let i=0;i<3;i++) {
 const extra=option('SIZE','X'+i);await a.ok('PUT',`/products/${bag}/options`,{options:[...bagOptions,extra]});
 const c=new Client('admin'); await c.login();
 const result=await Promise.all([a.request('POST','/skus',{...body,skuId:bag+'-INK-X'+i,sizeCode:'X'+i}),c.request('PUT',`/products/${bag}/options`,{options:bagOptions})]);
 assert(result.every(r=>r.status<500));
 const exists=num(`select count(*) from tb_sku where sku_id='${bag}-INK-X${i}'`);
 if(exists) {assert((await a.ok('GET',`/products/${bag}/options`)).some(o=>o.optionCode==='X'+i));await a.ok('DELETE','/skus/'+bag+'-INK-X'+i,{reason:'test cleanup'});}
 await a.ok('PUT',`/products/${bag}/options`,{options:bagOptions});
}
const restricted=new Client('dc1.pp01');await restricted.login();await restricted.no('PUT',`/products/${bag}/options`,{options:bagOptions});
fs.writeFileSync(path.join(dir,'options-fixtures.json'),JSON.stringify({bag,tee,sku:sku.skuId}));
console.log('PASS: migration, style isolation, custom names, single/bulk validation, in-use deletion, duplicate/format, permissions, concurrency');

if(process.env.IT_BROWSER!=='1') process.exit(0);
const {createServer}=await import('../../frontend/node_modules/vite/dist/node/index.js');
const {chromium}=await import('../../frontend/node_modules/playwright-core/index.mjs');
const server=await createServer({root:path.join(root,'frontend'),configFile:path.join(root,'frontend/vite.config.js'),server:{host:'127.0.0.1',port:15185,strictPort:true,open:false,proxy:{'/api':{target:'http://127.0.0.1:18085',changeOrigin:false}}}});await server.listen();
const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
try {
 const page=await browser.newPage({viewport:{width:1600,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:15185/#/login');await page.locator('#login-id').fill('admin');await page.locator('#login-pw').fill('wms1234!');await page.locator('button[type=submit]').click();await page.waitForURL(u=>!u.hash.includes('login'));
 await page.goto('http://127.0.0.1:15185/#/products');await page.locator('main input').first().fill(bag);await page.getByRole('button',{name:'검색',exact:true}).click();
 await page.locator('main tbody tr').filter({hasText:bag}).getByRole('button',{name:'옵션 관리',exact:true}).click();await page.getByRole('button',{name:'옵션 저장'}).waitFor();
 await page.getByLabel('색상 이름',{exact:true}).filter({visible:true}).first().waitFor();
 await page.screenshot({path:path.join(dir,'style-options.png'),fullPage:true});
 await page.getByRole('button',{name:'닫기',exact:true}).last().click();
 await page.goto('http://127.0.0.1:15185/#/skus?productId='+bag);
 await Promise.all([page.waitForResponse(r=>r.url().includes('/options') && r.status()===200),page.getByRole('button',{name:'+ SKU 등록',exact:true}).click()]);
 await page.waitForFunction(()=>Array.from(document.querySelectorAll('.modal select option')).some(o=>o.textContent.includes('잉크블랙')));
 const options=await page.locator('.modal select option').allTextContents();
 assert(options.includes('프리 (FREE)'));assert(!options.includes('미디엄 (M)'));
 fs.writeFileSync(path.join(dir,'sku-options-ui.json'),JSON.stringify(options));
 await page.screenshot({path:path.join(dir,'sku-options.png'),fullPage:true});
 await page.goto('http://127.0.0.1:15185/#/sku-bulk?productId='+bag);
 await page.getByText('잉크블랙 (INK)',{exact:true}).first().waitFor();
 assert(!(await page.locator('main').innerText()).includes('미디엄 (M)'));
 await page.screenshot({path:path.join(dir,'bulk-options.png'),fullPage:true});
 assert.deepEqual(errors,[]);console.log('BROWSER_PASS');
}finally{await browser.close();await server.close();}
