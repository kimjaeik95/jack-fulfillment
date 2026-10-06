import {db,Client,dir} from './integration-client.mjs';
import fs from 'node:fs';
console.log('locations',JSON.stringify(db("select l.location_seq,l.location_id,l.barcode,w.warehouse_id from tb_location l join tb_warehouse w using(warehouse_seq) join tb_plant p using(plant_seq) where p.plant_id='PL001'")));
const c=new Client('admin');await c.login();
const plant=db("select plant_id from tb_plant where plant_id like 'PT%' order by plant_seq desc limit 1")[0].plant_id;
const r=await c.ok('GET',`/stock-recon?plantId=${plant}&warehouseId=GD`);console.log('recon',JSON.stringify(r));
fs.writeFileSync(dir+'/normal-recon.json',JSON.stringify(r,null,2));
