import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
export {assert};
export const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
export const dir=path.join(root,'docs/test-results',process.env.IT_RUN||'20261006');
fs.mkdirSync(dir,{recursive:true});
export const base=process.env.IT_BASE||'http://127.0.0.1:18080/api';
export const stateFile=path.join(dir,'state.json');
export const state=fs.existsSync(stateFile)?JSON.parse(fs.readFileSync(stateFile,'utf8')):{};
export function save(){fs.writeFileSync(stateFile,JSON.stringify(state,null,2));}
export function sql(q){return execFileSync('C:/Program Files/PostgreSQL/18/bin/psql.exe',['-X','-h','127.0.0.1','-p','55432','-U','integration','-d',process.env.IT_DB||'fulfillment_it','-v','ON_ERROR_STOP=1','-At','-c',q],{encoding:'utf8',windowsHide:true}).trim();}
export function db(q){return JSON.parse(sql(`select coalesce(json_agg(t),'[]'::json) from (${q}) t`));}
export function num(q){return Number(sql(q));}
let current='setup';
export const resultsFile=path.join(dir,'results.json');
export const results=fs.existsSync(resultsFile)?JSON.parse(fs.readFileSync(resultsFile,'utf8')):{};
export function record(ids,status,note){for(const id of ids.split(','))results[id]={status,note,at:new Date().toISOString(),evidence:'http-evidence.jsonl / db-evidence.jsonl'};fs.writeFileSync(resultsFile,JSON.stringify(results,null,2));console.log(ids,status,note.slice(0,240));save();}
export async function test(ids,fn){if(process.env.IT_RESUME==='1'&&ids.split(',').every(id=>results[id]?.status==='통과'))return;current=ids;try{const note=await fn();record(ids,'통과',note||'API 응답 및 DB 상태 검증 통과');}catch(e){record(ids,e instanceof TypeError?'차단':'실패',e.message);console.log(e.stack?.split('\n').slice(0,4).join('\n'));}finally{current='setup';}}
export function snap(label,q){const rows=db(q);fs.appendFileSync(path.join(dir,'db-evidence.jsonl'),JSON.stringify({at:new Date().toISOString(),case:current,label,query:q,rows})+'\n');return rows;}
export class Client{
 constructor(user){this.user=user;this.cookies={};}
 async request(method,p,body){
  const headers={Accept:'application/json',Cookie:Object.entries(this.cookies).map(([k,v])=>`${k}=${v}`).join('; ')};
  if(body!==undefined)headers['Content-Type']='application/json';
  if(this.cookies['XSRF-TOKEN'])headers['X-XSRF-TOKEN']=decodeURIComponent(this.cookies['XSRF-TOKEN']);
  const r=await fetch(base+p,{method,headers,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(20000)});
  for(const c of r.headers.getSetCookie()){const pair=c.split(';')[0],idx=pair.indexOf('=');this.cookies[pair.slice(0,idx)]=pair.slice(idx+1);}
  const txt=await r.text();let payload;try{payload=JSON.parse(txt);}catch{payload={raw:txt.slice(0,1000)};}
  fs.appendFileSync(path.join(dir,'http-evidence.jsonl'),JSON.stringify({at:new Date().toISOString(),case:current,user:this.user,method,path:p,request:p.includes('/auth/')?'[인증정보 제외]':body,status:r.status,response:payload})+'\n');
  return {status:r.status,...payload};
 }
 async ok(method,p,body){const r=await this.request(method,p,body);assert(r.status<300&&r.success!==false,`${method} ${p}: HTTP${r.status} ${r.code||''} ${r.message||''}`);return r.data;}
 async no(method,p,body,pattern){const r=await this.request(method,p,body);assert(r.status>=400&&r.status<500,`거부 기대: ${method} ${p} -> HTTP${r.status} ${JSON.stringify(r).slice(0,350)}`);if(pattern)assert.match(r.message||r.code||'',pattern);return r;}
 async login(password='wms1234!'){await this.ok('GET','/auth/csrf');const me=await this.ok('POST','/auth/login',{userId:this.user,password});await this.ok('GET','/auth/csrf');return me;}
}
export async function clients(names){const cs={};for(const n of names){const c=new Client(n);await c.login();cs[n]=c;}return cs;}
export const stock=(sku)=>snap('재고 수량',`select s.stock_seq,s.location_seq,s.qty_on_hand,s.qty_allocated,s.qty_unsellable,s.qty_available from tb_stock s join tb_sku k using(sku_seq) where k.sku_id='${sku}' order by s.stock_seq`);
export const sum=(rows,key)=>rows.reduce((a,r)=>a+Number(r[key]||0),0);
