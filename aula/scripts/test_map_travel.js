const fs=require('fs'), vm=require('vm');
class El{constructor(tag){this.tag=tag;this.children=[];this.attrs={};this.handlers={};this._t='';this.value='';this.hidden=false;this.disabled=false;this.className='';this.style={setProperty(){}};
 const self=this;this.classList={toggle(c,f){const s=new Set(self.className.split(' ').filter(Boolean));(f===undefined?!s.has(c):f)?s.add(c):s.delete(c);self.className=[...s].join(' ')},add(c){self.classList.toggle(c,true)},remove(c){self.classList.toggle(c,false)},contains(c){return self.className.split(' ').includes(c)}}}
 appendChild(c){if(c.tag==='frag'){this.children.push(...c.children);c.children=[]}else this.children.push(c);return c} removeChild(c){this.children=this.children.filter(x=>x!==c)} get firstChild(){return this.children[0]||null}
 replaceChildren(...n){this.children=n} setAttribute(k,v){this.attrs[k]=v} getAttribute(k){return this.attrs[k]}
 addEventListener(e,f){(this.handlers[e]=this.handlers[e]||[]).push(f)} fire(e){(this.handlers[e]||[]).forEach(f=>f({currentTarget:this,key:''}))}
 set textContent(v){this._t=String(v);this.children=[]} get textContent(){return this._t+this.children.map(c=>c.textContent||'').join('')}
 add(){} focus(){} get tabIndex(){return 0} set tabIndex(v){} set innerHTML(v){this._t=v} get options(){return this.children}}
const reg={};
const document={readyState:'complete',addEventListener(){},getElementById(id){return reg[id]||(reg[id]=new El('x'))},createElement:t=>new El(t),createDocumentFragment:()=>new El('frag'),createTextNode:t=>{const e=new El('#text');e._t=t;return e},querySelectorAll:()=>[],querySelector:()=>new El('x')};
document.getElementById('origen');
const avisos=[];
const L={layerGroup(){return{addTo(){return this},addLayer(){},clearLayers(){}}},latLngBounds(a){return{extend(){},isValid:()=>true,getBounds(){return this}}},Control:{extend(o){return function(){return{addTo(){}}}}},DomUtil:{create:()=>new El('d')},DomEvent:{disableClickPropagation(){}},geoJSON(){return{addTo(){return this},getBounds(){return{extend(){},isValid:()=>true}}}},marker(){return{addTo(){return this},bindPopup(){return this},setLatLng(){},getLatLng(){return{}},openPopup(){}}},divIcon:()=>({}),circle(){return{addTo(){return this}}}};
const barrios=[['Centro',10.42,-75.55],['Bocagrande',10.40,-75.56],['Getsemaní',10.423,-75.545],['Manga',10.41,-75.53],['Crespo',10.44,-75.52],['Olaya',10.40,-75.50]].map(([nombre,latitud,longitud])=>({nombre,latitud,longitud}));
const P=(n,b,o,la,lo)=>({nombre:n,barrio:b,orden:o,latitud:la,longitud:lo,referencia:'Ref '+n});
const rutas=[
 {id:1,nombre:'Ruta Larga',horaAproximada:'06:30',barrios:['Centro','Getsemaní','Manga','Crespo'],barriosOrdenados:['Centro','Getsemaní','Manga','Crespo'],ordenBarriosConfirmado:true,paradas:[P('P1','Centro',1,10.42,-75.55),P('P2','Getsemaní',2,10.423,-75.545),P('P3','Manga',3,10.41,-75.53),P('P4','Crespo',4,10.44,-75.52)],buses:[{id:1,placa:'abc123',modelo:'2019',color:'Azul'}],coordenadasBarrios:{}},
 {id:2,nombre:'Ruta Corta',horaAproximada:'15:05:00',barrios:['Bocagrande','Centro','Manga'],barriosOrdenados:['Bocagrande','Centro','Manga'],ordenBarriosConfirmado:true,paradas:[],buses:[],coordenadasBarrios:{Centro:[10.42,-75.55],Manga:[10.41,-75.53],Bocagrande:[10.40,-75.56]}},
 {id:3,nombre:'Ruta sin orden',barrios:['Centro','Olaya'],barriosOrdenados:[],ordenBarriosConfirmado:false,paradas:[],buses:[]}];
const M={CENTRO:[0,0],crearMapa:()=>({flyTo(){},removeLayer(){},setView(){},getZoom:()=>13,on(){}}),fetchJSON:async u=>u.includes('barrios')?barrios:u.includes('rutas')?rutas:{features:[]},
 mostrarAviso:(m)=>avisos.push(m),mostrarCargando(){},ocultarCargando(){},ajustarVista(){},crearPin:()=>L.marker(),crearMarcadorParada:()=>L.marker(),crearCapasRuta:()=>({base:L.layerGroup(),linea:L.geoJSON()}),
 obtenerRutaOSRM:async()=>({geometry:{type:'LineString',coordinates:[[-75.55,10.42],[-75.53,10.41]]},distanciaKm:3.2,tiempoMin:11})};
const win={BustrakerMap:M,location:{search:'',pathname:'/viajar_public'},history:{replaceState(){}},innerWidth:1200,Option:function(t,v){return{text:t,value:v}}};
const ctx={Option:win.Option,window:win,document,L,URLSearchParams,Number,console,navigator:{},setTimeout};
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(__dirname+'/../src/main/resources/static/js/map-travel.js','utf8'),ctx);
const esperar=()=>new Promise(r=>setTimeout(r,20));
const txt=id=>reg[id].textContent;
let fallos=0;const ok=(c,m)=>{console.log((c?'  OK  ':' FALLA ')+m);if(!c)fallos++};
(async()=>{
 win.BustrakerTravel.iniciar({privado:false}); await esperar();
 const opts=reg.listaOrigen.children.map(o=>o.value);
 ok(JSON.stringify(opts)===JSON.stringify(['Bocagrande','Centro','Crespo','Getsemaní','Manga','Olaya']),'origen ofrece todos los barrios registrados: '+opts.join(', '));
 reg.origen.value='centro'; reg.origen.fire('input');
 const dest=reg.listaDestino.children.map(o=>o.value).sort();
 ok(JSON.stringify(dest)===JSON.stringify(['Crespo','Getsemaní','Manga']),'destinos desde Centro: '+dest.join(', '));
 ok(!dest.includes('Olaya'),'ignora destinos que solo aparecen en rutas sin orden confirmado');
 ok(reg.destino.disabled===false,'destino se habilita');
 reg.destino.value='Manga'; reg.destino.fire('input'); await esperar();
 const cards=reg.resultados.children.filter(c=>c.tag==='button');
 ok(cards.length===2,'2 tramos Centro→Manga ('+cards.length+')');
 ok(cards[0].textContent.includes('Ruta Corta')&&cards[0].textContent.includes('Más corta'),'Ruta Corta primero y rotulada "Más corta"');
 ok(cards[0].textContent.includes('3:05 p. m.'),'hora 15:05 → 3:05 p. m.');
 ok(cards[1].textContent.includes('Ruta Larga')&&cards[1].textContent.includes('1 bus')&&cards[1].textContent.includes('3 paradas'),'Ruta Larga: 1 bus, 3 paradas en el tramo ('+cards[1].textContent.replace(/\s+/g,' ')+')');
 ok(cards[0].textContent.includes('Sin paradas registradas')&&cards[0].textContent.includes('Sin buses asignados'),'Ruta Corta: sin paradas ni buses (no se inventan datos)');
 cards[1].fire('click'); await esperar();
 ok(reg.rutaDetalle.hidden===false,'detalle visible');
 ok(txt('detalleTramo').includes('Centro → Manga'),'tramo: '+txt('detalleTramo'));
 ok(txt('contadorParadas')==='3'&&txt('contadorBuses')==='1','contadores paradas=3 buses=1');
 ok(txt('panelBuses').includes('ABC123')&&!txt('panelBuses').includes('conductor'),'placa en mayúsculas');
 ok(txt('rutaStats').includes('3.2 km')&&txt('rutaStats').includes('~11 min'),'estadísticas OSRM: '+txt('rutaStats'));
 cards[0].fire('click'); await esperar();
 ok(txt('contadorParadas')==='2','Ruta Corta usa centros de barrio como respaldo (Centro y Manga = 2 puntos)');
 ok(txt('panelParadas').includes('no tiene paradas físicas'),'avisa que son centros de barrio');
 reg.destino.value='Crespo'; reg.destino.fire('input'); await esperar();
 ok(reg.resultados.children.filter(c=>c.tag==='button').length===1 && reg.rutaDetalle.hidden===false,'un solo tramo se selecciona solo');
 reg.origen.value='Manga'; reg.origen.fire('input');
 ok(reg.destino.value==='' && reg.rutaDetalle.hidden===true,'cambiar origen reinicia destino y detalle');
 ok(reg.listaDestino.children.length===1,'desde Manga solo Crespo (Ruta Larga)');
 reg.origen.value='Crespo'; reg.origen.fire('input');
 ok(txt('resultados').includes('No hay rutas verificadas que salgan'),'Crespo es fin de línea');
 ok(reg.destino.disabled===false && reg.listaDestino.children.length===5,'destino sigue disponible al final de línea');
 reg.destino.value='Olaya'; reg.destino.fire('input'); await esperar();
 ok(txt('resultados').includes('Sin rutas verificadas en este sentido'),'barrio válido sin ruta muestra resultado vacío, no bloquea el destino');
 reg.origen.value='';
 for (const c of 'Olaya') { reg.origen.value+=c; reg.origen.fire('input'); }
 ok(reg.origen.value==='Olaya' && reg.destino.disabled===false,'permite origen en barrio registrado sin servicio confirmado');
 reg.destino.value='';
 for (const c of 'Centro') { reg.destino.value+=c; reg.destino.fire('input'); }
 await esperar();
 ok(txt('resultados').includes('Sin rutas verificadas en este sentido'),'consulta entre barrios sin ruta confirmada termina sin inventar recorrido');
 console.log(fallos?`\n${fallos} FALLOS`:'\nTODAS LAS PRUEBAS OK');process.exit(fallos?1:0);
})();
