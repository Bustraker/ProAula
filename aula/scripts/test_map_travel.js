const fs=require('fs'), vm=require('vm');
class El{constructor(tag){this.tag=tag;this.children=[];this.attrs={};this.handlers={};this._t='';this.value='';this.hidden=false;this.disabled=false;this.className='';this.style={setProperty(){}};
 const self=this;this.classList={toggle(c,f){const s=new Set(self.className.split(' ').filter(Boolean));(f===undefined?!s.has(c):f)?s.add(c):s.delete(c);self.className=[...s].join(' ')},add(c){self.classList.toggle(c,true)},remove(c){self.classList.toggle(c,false)},contains(c){return self.className.split(' ').includes(c)}}}
 appendChild(c){if(c.tag==='frag'){this.children.push(...c.children);c.children=[]}else this.children.push(c);return c} removeChild(c){this.children=this.children.filter(x=>x!==c)} get firstChild(){return this.children[0]||null}
 insertBefore(c,ref){const i=this.children.indexOf(ref);this.children.splice(i<0?0:i,0,c);return c}
 replaceChildren(...n){this.children=n} setAttribute(k,v){this.attrs[k]=v} removeAttribute(k){delete this.attrs[k]} getAttribute(k){return this.attrs[k]}
 addEventListener(e,f){(this.handlers[e]=this.handlers[e]||[]).push(f)} fire(e){(this.handlers[e]||[]).forEach(f=>f({currentTarget:this,key:''}))}
 set textContent(v){this._t=String(v);this.children=[]} get textContent(){return this._t+this.children.map(c=>c.textContent||'').join('')}
 add(){} focus(){} get tabIndex(){return 0} set tabIndex(v){} set innerHTML(v){this._t=v} get options(){return this.children}}
const reg={};
const document={readyState:'complete',addEventListener(){},getElementById(id){return reg[id]||(reg[id]=new El('x'))},createElement:t=>new El(t),createDocumentFragment:()=>new El('frag'),createTextNode:t=>{const e=new El('#text');e._t=t;return e},querySelectorAll:()=>[],querySelector:()=>new El('x')};
document.getElementById('origen');
const avisos=[];
const L={layerGroup(){const layers=[];return{layers,addTo(){return this},addLayer(layer){layers.push(layer);return this},clearLayers(){layers.length=0},getLayers(){return layers}}},latLngBounds(a){return{extend(){},isValid:()=>true,getBounds(){return this}}},Control:{extend(o){return function(){return{addTo(){}}}}},DomUtil:{create:()=>new El('d')},DomEvent:{disableClickPropagation(){}},geoJSON(){return{addTo(){return this},getBounds(){return{extend(){},isValid:()=>true}}}},polyline(){return{addTo(){return this},bindPopup(){return this}}},marker(){return{addTo(){return this},bindPopup(){return this},setLatLng(){},getLatLng(){return{}},openPopup(){}}},divIcon:()=>({}),circle(){return{addTo(){return this}}}};
const barrios=[['Centro',10.42,-75.55],['Bocagrande',10.40,-75.56],['Getsemaní',10.423,-75.545],['Manga',10.41,-75.53],['Crespo',10.44,-75.52],['Olaya',10.40,-75.50]].map(([nombre,latitud,longitud])=>({nombre,latitud,longitud}));
const P=(n,b,o,la,lo)=>({nombre:n,barrio:b,orden:o,latitud:la,longitud:lo,referencia:'Ref '+n});
const rutas=[
 {id:1,nombre:'Ruta Larga',horaAproximada:'06:30',barrios:['Centro','Getsemaní','Manga','Crespo'],barriosOrdenados:['Centro','Getsemaní','Manga','Crespo'],ordenBarriosConfirmado:true,paradas:[P('P1','Centro',1,10.42,-75.55),P('P2','Getsemaní',2,10.423,-75.545),P('P3','Manga',3,10.41,-75.53),P('P4','Crespo',4,10.44,-75.52)],buses:[{id:1,placa:'abc123',modelo:'2019',color:'Azul'}],coordenadasBarrios:{}},
 {id:2,nombre:'Ruta Corta',horaAproximada:'15:05:00',barrios:['Bocagrande','Centro','Manga'],barriosOrdenados:['Bocagrande','Centro','Manga'],ordenBarriosConfirmado:true,paradas:[],buses:[],coordenadasBarrios:{Centro:[10.42,-75.55],Manga:[10.41,-75.53],Bocagrande:[10.40,-75.56]}},
 {id:3,nombre:'Ruta sin orden',barrios:['Centro','Olaya'],barriosOrdenados:[],ordenBarriosConfirmado:false,paradas:[],buses:[]}];
const M={CENTRO:[0,0],crearMapa:()=>({flyTo(){},removeLayer(){},setView(){},getZoom:()=>13,on(){}}),fetchJSON:async u=>u.includes('barrios')?barrios:u.includes('rutas')?rutas:{features:[]},
 mostrarAviso:(m)=>avisos.push(m),mostrarCargando(){},ocultarCargando(){},ajustarVista(){},crearPin:()=>L.marker(),crearMarcadorParada:()=>L.marker(),crearCapasRuta:()=>({base:L.layerGroup(),linea:L.geoJSON()}),crearFlechasDireccion:()=>L.layerGroup(),vincularFlechas:()=>()=>{},
 obtenerRutaOSRM:async()=>({geometry:{type:'LineString',coordinates:[[-75.55,10.42],[-75.53,10.41]]},distanciaKm:3.2,tiempoMin:11})};
const win={BustrakerMap:M,location:{search:'',pathname:'/viajar_public'},history:{replaceState(){}},innerWidth:1200,Option:function(t,v){return{text:t,value:v}}};
const ctx={Option:win.Option,window:win,document,L,URLSearchParams,Number,console,navigator:{},setTimeout,AbortController};
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
 ok(JSON.stringify(dest)===JSON.stringify(['Bocagrande','Crespo','Getsemaní','Manga','Olaya']),'destinos válidos desde Centro: '+dest.join(', '));
 ok(reg.destino.disabled===false,'destino se habilita');
 reg.destino.value='Manga'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 const cards=reg.resultados.children.filter(c=>c.tag==='button');
 ok(cards.length===1,'solo se muestra una recomendación Centro→Manga ('+cards.length+')');
 ok(cards[0].textContent.includes('Ruta Corta')&&cards[0].textContent.includes('Recomendada'),'Ruta Corta recomendada por recorrer menos barrios');
 ok(txt('detalleHora').includes('3:05 p. m.'),'hora 15:05 → 3:05 p. m.');
 ok(!cards[0].textContent.includes('Ruta Larga'),'no presenta la ruta directa con más barrios');
 ok(cards[0].textContent.includes('Sin paradas registradas')&&cards[0].textContent.includes('Sin buses asignados'),'Ruta Corta: sin paradas ni buses (no se inventan datos)');
 ok(reg.rutaDetalle.hidden===false,'itinerario recomendado se selecciona y dibuja tras planificar');
 ok(txt('detalleTramo').includes('Centro → Manga'),'tramo recomendado: '+txt('detalleTramo'));
 ok(txt('contadorParadas')==='2'&&txt('contadorBuses')==='0','Ruta Corta usa 2 centros de barrio y no inventa buses');
 ok(txt('panelBuses').includes('Sin buses asignados'),'informa cuando no hay buses registrados');
 ok(txt('rutaStats').includes('3.2 km')&&txt('rutaStats').includes('~11 min'),'estadísticas OSRM: '+txt('rutaStats'));
 cards[0].fire('click'); await esperar();
 ok(txt('contadorParadas')==='2','Ruta Corta usa centros de barrio como respaldo (Centro y Manga = 2 puntos)');
 ok(txt('panelParadas').includes('no tiene paradas físicas'),'avisa que son centros de barrio');
 reg.origen.value='Bocagrande'; reg.origen.fire('input');
 reg.destino.value='Crespo'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 const transbordo=reg.resultados.children.filter(c=>c.tag==='button');
 ok(transbordo.length===1,'sin ruta directa muestra una sola recomendación con transbordo');
 ok(transbordo[0].textContent.includes('Ruta Corta')&&transbordo[0].textContent.includes('Ruta Larga'),'elige el transbordo con menos barrios intermedios');
 ok(txt('resumenBusqueda').includes('1 transbordo')&&txt('resumenBusqueda').includes('2 barrios intermedios'),'resume el coste del transbordo: '+txt('resumenBusqueda'));
 ok(reg.resultados.children.filter(c=>c.tag==='button').length===1 && reg.rutaDetalle.hidden===false,'recomendación de transbordo se selecciona y dibuja');
 reg.origen.value='Manga'; reg.origen.fire('input');
 ok(reg.destino.value==='' && reg.rutaDetalle.hidden===true,'cambiar origen reinicia destino y detalle');
 ok(reg.listaDestino.children.length===5,'desde Manga se pueden elegir todos los otros barrios');
 reg.origen.value='Crespo'; reg.origen.fire('input');
 ok(reg.destino.disabled===false && reg.listaDestino.children.length===5,'destino disponible aunque el barrio no tenga rutas salientes');
 reg.destino.value='Olaya'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 ok(txt('resultados').includes('No hay combinación de rutas'),'barrio válido sin ruta muestra resultado vacío, no bloquea el destino');
 reg.origen.value='';
 for (const c of 'Olaya') { reg.origen.value+=c; reg.origen.fire('input'); }
 ok(reg.origen.value==='Olaya' && reg.destino.disabled===false,'permite origen en barrio registrado sin servicio confirmado');
 reg.destino.value='';
 for (const c of 'Centro') { reg.destino.value+=c; reg.destino.fire('input'); }
 await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 ok(txt('resultados').includes('No hay combinación de rutas'),'consulta entre barrios sin ruta confirmada termina sin inventar recorrido');
 console.log(fallos?`\n${fallos} FALLOS`:'\nTODAS LAS PRUEBAS OK');process.exit(fallos?1:0);
})();
