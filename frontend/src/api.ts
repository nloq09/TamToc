let token = sessionStorage.getItem('tamtoc-token') || '';
export function setToken(value:string) { token=value; if(value) sessionStorage.setItem('tamtoc-token',value); else sessionStorage.removeItem('tamtoc-token'); }
export async function api<T>(path:string, body?:unknown):Promise<T> {
 const response=await fetch('/api'+path,{method:body===undefined?'GET':'POST',headers:{...(body===undefined?{}:{'Content-Type':'application/json'}),...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 if(!response.ok) { const error=await response.json().catch(()=>({})); throw new Error(error.message || (response.status===401?'Phiên đăng nhập hết hạn hoặc thông tin đăng nhập không đúng.':'Yêu cầu thất bại ('+response.status+').')); }
 return response.json();
}
export function hasToken() { return !!token; }
