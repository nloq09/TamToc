import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {readFile} from 'node:fs/promises';
import {Client,api} from './helpers/clients.mjs';
const env=Object.fromEntries((await readFile('.env','utf8')).split(/\r?\n/).filter(s=>/^(POSTGRES_USER|POSTGRES_DB)=/.test(s)).map(s=>s.split('=')));
const query=sql=>execFileSync('docker',['compose','exec','-T','db','psql','-U',env.POSTGRES_USER||'tamtoc','-d',env.POSTGRES_DB||'tamtoc','-v','ON_ERROR_STOP=1','-At','-F','|','-c',sql],{encoding:'utf8'}).trim();
const row=query("SELECT u.username,c.id FROM users u JOIN characters c ON c.user_id=u.id WHERE u.username LIKE 'routeh0_%' AND c.organization IS NOT NULL ORDER BY u.username DESC LIMIT 1");
assert.ok(row,'Run world15-routes.mjs first to create a known QA character with earned organization');
const [username,id]=row.split('|');assert.match(username,/^routeh0_[a-z0-9]+$/);assert.match(id,/^[0-9a-f-]{36}$/);let c;
try{
 const account=await api('/auth/login',{username,password:'Integration_Test_123'});const character=await api('/characters/'+id,undefined,account.token);c=new Client(account,character,3);await c.open();const before={level:c.self().level,exp:c.self().exp,organization:c.self().organization,path:c.self().path,cultivation:c.self().cultivation};const inventory=await api('/characters/'+id+'/inventory',undefined,account.token),quests=await api('/characters/'+id+'/quests',undefined,account.token);assert.ok(before.organization);c.close();await new Promise(r=>setTimeout(r,500));
 assert.equal(query("UPDATE characters SET map_id='linh_tuyen_khe' WHERE id='"+id+"' RETURNING map_id").split(/\r?\n/)[0],'linh_tuyen_khe');
 await c.open();assert.equal(c.snapshot.mapId,'thanh-van');for(const [key,value]of Object.entries(before))assert.equal(c.self()[key],value);assert.deepEqual(await api('/characters/'+id+'/inventory',undefined,account.token),inventory);const restored=await api('/characters/'+id+'/quests',undefined,account.token);assert.equal(restored.find(q=>q.definition.id==='WOLVES').status,'CLAIMED');assert.equal(restored.find(q=>q.definition.id==='PATH').status,'CLAIMED');assert.equal(quests.find(q=>q.definition.id==='WOLVES').status,'CLAIMED');
 console.log('PASS actual PostgreSQL archived-map save falls back safely, preserving earned level/EXP, organization/path, cultivation, inventory and claimed quest rewards');
}finally{c?.close();}
