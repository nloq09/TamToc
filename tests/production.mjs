import assert from 'node:assert/strict';
import http from 'node:http';
import {randomBytes} from 'node:crypto';
const base='http://127.0.0.1:8080';
const account=await fetch(base+'/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'security_'+Date.now().toString(36),password:'Security_Test_123'})}).then(r=>r.json());
assert.ok(account.token);
const created=await fetch(base+'/api/characters',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+account.token},body:JSON.stringify({name:'Secure_'+Date.now().toString(36),race:'BEAST'})}).then(r=>r.json());
const admin=await fetch(base+'/api/dev/characters/'+created.id+'/level10',{method:'POST',headers:{Authorization:'Bearer '+account.token}});assert.equal(admin.status,404);
const tampered=await fetch(base+'/api/characters',{headers:{Authorization:'Bearer '+account.token.slice(0,-12)+'invalidtoken'}});assert.equal(tampered.status,401);
const cors=await fetch(base+'/api/characters',{method:'OPTIONS',headers:{Origin:'https://untrusted.example','Access-Control-Request-Method':'GET'}});assert.equal(cors.status,403);
await new Promise((resolve,reject)=>{
 const req=http.request(base+'/ws',{headers:{Connection:'Upgrade',Upgrade:'websocket','Sec-WebSocket-Version':'13','Sec-WebSocket-Key':randomBytes(16).toString('base64'),Origin:'https://untrusted.example'}},response=>{try{assert.equal(response.statusCode,403);response.resume();resolve();}catch(e){reject(e);}});
 req.on('upgrade',(_r,s)=>{s.destroy();reject(Error('Untrusted WS Origin was accepted'));});req.on('error',reject);req.setTimeout(5000,()=>req.destroy(Error('Origin check timeout')));req.end();
});
console.log('PASS default Docker context: dev endpoint absent, invalid JWT rejected, HTTP CORS and WebSocket Origin rejected');
if(process.env.PERSISTENCE_USERNAME){
 const login=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:process.env.PERSISTENCE_USERNAME,password:'Progress_Test_123'})}).then(r=>r.json());
 const headers={Authorization:'Bearer '+login.token};
 const chars=await fetch(base+'/api/characters',{headers}).then(r=>r.json());const c=chars.find(c=>c.name.startsWith('Cultivator_'));assert.ok(c);assert.equal(c.cultivationPath,'KIEM_TU');assert.equal(c.realmStage,1);
 const inventory=await fetch(base+'/api/characters/'+c.id+'/inventory',{headers}).then(r=>r.json());assert.equal(inventory.equipment.WEAPON,'QI_SWORD');assert.equal(inventory.stones,140);
 const quests=await fetch(base+'/api/characters/'+c.id+'/quests',{headers}).then(r=>r.json());assert.ok(quests.filter(q=>['WOLVES','PATH'].includes(q.definition.id)).every(q=>q.status==='CLAIMED'));
 const skills=await fetch(base+'/api/characters/'+c.id+'/skills',{headers}).then(r=>r.json());assert.ok(skills.some(s=>s.id==='KIEM_TU'));
 console.log('PASS full progression survives backend shutdown and restart in Docker with the same PostgreSQL volume');
}
