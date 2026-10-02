import { chromium } from '../../frontend/node_modules/playwright-core/index.mjs';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
const browser=await chromium.launch({executablePath:process.env.ERD_BROWSER||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
try{
 const page=await browser.newPage({viewport:{width:1600,height:1000}}),errors=[],requests=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url()))requests.push(r.url())});
 await page.goto(new URL('../system-map.html',import.meta.url).href);
 assert.deepEqual(errors,[]);assert.equal(await page.locator('.domain').count(),8);
 for(const id of ['auth','system','master','purchase','inbound','inventory','planned','shared']){
  await page.locator('#tree summary [data-group="'+id+'"]').click();
  assert.equal(await page.locator('#content h1').count(),1);
 }
 await page.locator('#search').fill('적치');
 assert.equal(await page.locator('.tree-group').count(),1);
 await page.locator('[data-screen="/inbound-putaway"]').click();
 assert((await page.locator('tr.highlight').textContent()).includes('적치'));
 await page.locator('.folder summary').first().click();
 assert((await page.locator('.folder[open] .files a').count())>0);
 await page.locator('#search').fill('no-such-screen');assert.equal(await page.locator('.empty').count(),1);
 await page.locator('#search').fill('');
 await page.locator('#tree [data-group="overview"]').click();
 assert.equal(await page.locator('.domain').count(),8);
 const screenshot=path.join(fs.mkdtempSync(path.join(os.tmpdir(),'system-map-')),'overview.png');
 await page.screenshot({path:screenshot});
 const links=await page.locator('a[href^="../"]').evaluateAll(els=>els.map(el=>el.getAttribute('href')));
 for(const href of links)assert(fs.existsSync(new URL(href,new URL('../system-map.html',import.meta.url))));
 assert.deepEqual(errors,[]);assert.deepEqual(requests,[]);
 console.log(JSON.stringify({status:'passed',checks:['8 group navigation','screen search','screen highlight','folder expansion','empty search','local source links','offline','no browser errors'],screenshot}));
}finally{await browser.close()}
