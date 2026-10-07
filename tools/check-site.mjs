import { readFile, readdir, stat } from 'node:fs/promises';
import { resolve, relative } from 'node:path';
const root=resolve(import.meta.dirname,'../artifacts/site');
const files=[];
async function walk(dir){for(const entry of await readdir(dir,{withFileTypes:true})){const path=resolve(dir,entry.name);if(entry.isDirectory())await walk(path);else if(path.endsWith('.html'))files.push(path);}}
await walk(root);
let links=0;
for(const path of files){
 const html=await readFile(path,'utf8');
 if(!html.includes('<main')||!html.includes('<title>'))throw new Error(`Missing page landmarks: ${path}`);
 for(const match of html.matchAll(/(?:href|src)="([^"#]+)(?:#[^"]*)?"/g)){
  const href=match[1].split('#')[0];if(!href.startsWith('/'))continue;
  const target=resolve(root,'.'+href);if(relative(root,target).startsWith('..'))throw new Error('Escaping site link');
  const info=await stat(target).catch(()=>null);if(!info)throw new Error(`Broken local link ${href} in ${relative(root,path)}`);
  if(info.isDirectory())await stat(resolve(target,'index.html'));links++;
 }
}
console.log(`Checked ${files.length} HTML pages and ${links} internal links/assets`);
