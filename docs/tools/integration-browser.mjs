import {createServer} from '../../frontend/node_modules/vite/dist/node/index.js';
import {chromium} from '../../frontend/node_modules/playwright-core/index.mjs';
import fs from 'node:fs';
import path from 'node:path';
import {root,dir,state} from './integration-client.mjs';
const server=await createServer({root:path.join(root,'frontend'),configFile:path.join(root,'frontend/vite.config.js'),server:{host:'127.0.0.1',port:15173,strictPort:true,open:false,proxy:{'/api':{target:'http://127.0.0.1:18080',changeOrigin:false}}}});
await server.listen();
const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
const findings=[];
try{
 const context=await browser.newContext({viewport:{width:1600,height:1000}}),page=await context.newPage();
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:15173/#/login');await page.locator('#login-id').fill('admin');await page.locator('#login-pw').fill('wms1234!');await page.locator('button[type=submit]').click();await page.waitForURL(u=>!u.hash.includes('login'));
 const routes=[];for(const mod of fs.readdirSync(path.join(root,'frontend/src/modules'))){const p=path.join(root,'frontend/src/modules',mod,'routes.js');if(fs.existsSync(p))for(const m of fs.readFileSync(p,'utf8').matchAll(/path:\s*'([^']+)'/g))if(!['/login','/password'].includes(m[1]))routes.push(m[1]);}
 for(const route of routes){let note='';try{await page.goto('http://127.0.0.1:15173/#'+route);await page.reload();await page.waitForLoadState('networkidle',{timeout:12000});note=(await page.locator('main').innerText().catch(()=>page.locator('body').innerText())).slice(0,1800);if(['/stocks','/outbound-chain','/delivery-track','/notifications','/label-print','/stock-recon'].includes(route))await page.screenshot({path:path.join(dir,route.slice(1)+'.png'),fullPage:true});findings.push({route,url:page.url(),errors:[...errors],text:note});console.log('UI',route,errors.length);}catch(e){findings.push({route,error:e.message,text:note});}errors.length=0;}
 await page.goto('http://127.0.0.1:15173/#/outbound-chain');const input=page.locator('input').first();if(await input.count()){await input.fill(state.out.outboundNo);await page.screenshot({path:path.join(dir,'e2e-chain.png'),fullPage:true});}
 fs.writeFileSync(path.join(dir,'browser-evidence.json'),JSON.stringify(findings,null,2));
 console.log('BROWSER_SUMMARY',JSON.stringify({pages:findings.length,errors:findings.filter(r=>r.error||r.errors?.length).length}));
}finally{await browser.close();await server.close();}
