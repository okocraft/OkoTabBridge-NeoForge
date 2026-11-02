import {access,mkdir,readFile,writeFile,copyFile,cp,rm,realpath} from 'node:fs/promises';
import {createWriteStream} from 'node:fs';
import {spawn,execFileSync} from 'node:child_process';
import {once} from 'node:events';
import {createInterface} from 'node:readline';
import {fileURLToPath} from 'node:url';
import {join,resolve,sep} from 'node:path';
import assert from 'node:assert/strict';
const root=fileURLToPath(new URL('../../../',import.meta.url));
const workspace=resolve(root,'..');
const work=join(root,'build/smoke'),server=join(work,'server');
const java=process.env.JAVA_HOME?join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'):'java';
async function exists(p){try{await access(p);return true;}catch{return false;}}
await mkdir(join(server,'mods'),{recursive:true});
if(!await exists(join(server,'libraries/net/neoforged/neoforge/26.2.0.88/unix_args.txt'))){
 const local=join(workspace,'Bolt/neoforge/build/smoke/server/libraries');
 if(await exists(local))await cp(local,join(server,'libraries'),{recursive:true});
 const response=await fetch('https://maven.neoforged.net/releases/net/neoforged/neoforge/26.2.0.88/neoforge-26.2.0.88-installer.jar');
 assert(response.ok,'NeoForge installer download failed');
 await writeFile(join(work,'installer.jar'),Buffer.from(await response.arrayBuffer()));
 execFileSync(java,['-jar',join(work,'installer.jar'),'--installServer'],{cwd:server,stdio:'inherit',timeout:240000,windowsHide:true});
}
await copyFile(join(root,'build/test-dependencies/TAB.v6.2.1.jar'),join(server,'mods/TAB.jar'));
await copyFile(join(root,'build/libs/OkoTabBridge-NeoForge-0.1.0.jar'),join(server,'mods/OkoTabBridge-NeoForge.jar'));
await copyFile(join(root,'build/testmods/oko-tab-test.jar'),join(server,'mods/oko-tab-test.jar'));
const lpLocal=join(workspace,'WorldGuard-NeoForge/build/smoke/server/mods/LuckPerms-NeoForge-5.5.85.jar');
if(await exists(lpLocal))await copyFile(lpLocal,join(server,'mods/LuckPerms-NeoForge-5.5.85.jar'));
else { const r=await fetch('https://cdn.modrinth.com/data/Vebnzrzj/versions/cLEqLHhz/LuckPerms-NeoForge-5.5.85.jar');assert(r.ok,'LuckPerms download failed');await writeFile(join(server,'mods/LuckPerms-NeoForge-5.5.85.jar'),Buffer.from(await r.arrayBuffer())); }
const actual=await realpath(server);assert(actual.startsWith(await realpath(root)+sep));
for(const name of ['world','config/tab']){const target=resolve(actual,name);assert(target.startsWith(actual+sep));await rm(target,{recursive:true,force:true});}
await mkdir(join(server,'config/tab'),{recursive:true});
await writeFile(join(server,'eula.txt'),'eula=true\n');
await writeFile(join(server,'ops.json'),'[]\n');
await writeFile(join(server,'server.properties'),'server-ip=127.0.0.1\nserver-port=25586\nonline-mode=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\nlevel-type=minecraft:flat\n');
await writeFile(join(server,'user_jvm_args.txt'),'-Xms256M\n-Xmx2G\n-Dfile.encoding=UTF-8\n-Dstdout.encoding=UTF-8\n-Dstderr.encoding=UTF-8\n');
const endpoint={host:'127.0.0.1',port:25586};
await writeFile(join(server,'config/oko-tab-bridge.json'),JSON.stringify({refreshSeconds:1,timeoutMillis:1000,network:endpoint,servers:{main:endpoint,hub:endpoint,creative:endpoint,dev:endpoint}}));
const config={
 'header-footer':{enabled:true,designs:{default:{header:['OKOCRAFT'],footer:['%bungee_total%']}}},
 'tablist-name-formatting':{enabled:true},
 layout:{enabled:true,direction:'ROWS','default-skin':'texture:469b3bac406b51ba0e76c2c218aa4d45fde9ea7c101c85fbd8106c92c4aa36dd',layouts:{default:{'fixed-slots':['1|Network: %bungee_total%'],groups:{players:{slots:['2-80']}}}}},
 'scoreboard-teams':{enabled:false},'belowname-objective':{enabled:false},bossbar:{enabled:false},scoreboard:{enabled:false},'playerlist-objective':{enabled:false},'proxy-support':{enabled:false},'config-version':7
};
await writeFile(join(server,'config/tab/config.yml'),JSON.stringify(config,null,2));
await writeFile(join(server,'config/tab/groups.yml'),JSON.stringify({_DEFAULT_:{tabprefix:'%luckperms-prefix%',customtabname:'%player%',tabsuffix:'%luckperms-suffix%'}}));
const args='libraries/net/neoforged/neoforge/26.2.0.88/'+(process.platform==='win32'?'win_args.txt':'unix_args.txt');
const child=spawn(java,['@user_jvm_args.txt','@'+args,'nogui'],{cwd:server,stdio:['pipe','pipe','pipe'],windowsHide:true});
const log=createWriteStream(join(work,'server-output.log'));
let ready=false,initialized=false,passed=false,failed=false,stopping=false;
for(const stream of [child.stdout,child.stderr])createInterface({input:stream}).on('line',line=>{
 log.write(line+'\n');console.log(line);ready ||= line.includes('Done (');initialized ||= line.includes('OkoTabBridge initialized');
 passed ||= line.includes('OKO_TAB_NATIVE_TESTS_PASSED');failed ||= line.includes('OKO_TAB_NATIVE_TESTS_FAILED');
 if(ready&&passed&&!stopping){stopping=true;child.stdin.write('stop\n');}
});
const timeout=setTimeout(()=>child.kill(),300000);
try{const [code]=await once(child,'close');assert(ready&&initialized&&passed&&!failed,'TAB native fixture did not pass');assert.equal(code,0,'Native server did not stop normally');}
finally{clearTimeout(timeout);if(child.exitCode===null&&child.signalCode===null)child.kill();log.end();}
console.log('OKO_TAB_PACKAGED_TESTS_PASSED');
