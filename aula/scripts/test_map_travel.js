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
const body=new El('body');
const document={readyState:'complete',body,addEventListener(){},getElementById(id){return reg[id]||(reg[id]=new El('x'))},createElement:t=>new El(t),createDocumentFragment:()=>new El('frag'),createTextNode:t=>{const e=new El('#text');e._t=t;return e},querySelectorAll:()=>[],querySelector:()=>new El('x')};
document.getElementById('origen');
const avisos=[], trazados=[];
const L={layerGroup(){const layers=[];return{layers,addTo(){return this},addLayer(layer){layers.push(layer);return this},clearLayers(){layers.length=0},getLayers(){return layers}}},latLngBounds(a){return{extend(){},isValid:()=>true,getBounds(){return this}}},Control:{extend(o){return function(){return{addTo(){return this},onAdd(){return new El('div')},onRemove(){}}}}},DomUtil:{create:()=>new El('d')},DomEvent:{disableClickPropagation(){}},geoJSON(){return{addTo(){return this},getBounds(){return{extend(){},isValid:()=>true}}}},polyline(){return{addTo(){return this},bindPopup(){return this},bindTooltip(){return this}}},marker(){return{addTo(){return this},bindPopup(){return this},setLatLng(){},getLatLng(){return{}},openPopup(){}}},divIcon:()=>({}),circle(){return{addTo(){return this}}}};
const barrios=[['Centro',10.42,-75.55],['Bocagrande',10.40,-75.56],['Getsemaní',10.423,-75.545],['Manga',10.41,-75.53],['Crespo',10.44,-75.52],['Olaya',10.40,-75.50],['Olaya ST. Central',10.40,-75.50],['Prueba A',10.30,-75.40],['Prueba B',10.31,-75.40],['Prueba Destino',10.32,-75.40],['Origen Distancia',10.30,-75.30],['Destino Distancia',10.33,-75.30],['Transbordo Lejano',10.31,-75.30],['Transbordo Cercano',10.32,-75.30]].map(([nombre,latitud,longitud])=>({nombre,latitud,longitud}));
const P=(n,b,o,la,lo)=>({nombre:n,barrio:b,orden:o,latitud:la,longitud:lo,referencia:'Ref '+n});
const rutas=[
 {id:1,nombre:'Ruta Larga',horaAproximada:'06:30',barrios:['Centro','Getsemaní','Manga','Crespo'],barriosOrdenados:['Centro','Getsemaní','Manga','Crespo'],ordenBarriosConfirmado:true,paradas:[P('P1','Centro',1,10.42,-75.55),P('P2','Getsemaní',2,10.423,-75.545),P('P3','Manga',3,10.41,-75.53),P('P4','Crespo',4,10.44,-75.52)],buses:[{id:1,placa:'abc123',modelo:'2019',color:'Azul'}],coordenadasBarrios:{}},
 {id:2,nombre:'Ruta Corta',horaAproximada:'15:05:00',barrios:['Bocagrande','Centro','Manga'],barriosOrdenados:['Bocagrande','Centro','Manga'],ordenBarriosConfirmado:true,paradas:[],buses:[],coordenadasBarrios:{Centro:[10.42,-75.55],Manga:[10.41,-75.53],Bocagrande:[10.40,-75.56]}},
 {id:3,nombre:'Ruta sin orden',barrios:['Centro','Olaya'],barriosOrdenados:[],ordenBarriosConfirmado:false,paradas:[],buses:[]},
 {id:4,nombre:'Ruta con sentido confirmado',estado:'ACTIVA',barrios:['Centro','Olaya'],barriosOrdenados:['Centro','Olaya'],ordenBarriosConfirmado:true,paradas:[P('Parada Olaya vuelta','Olaya',1),Object.assign(P('Parada inactiva','Centro',2),{estado:'INACTIVA'}),P('Parada Centro vuelta','Centro',3)],paradasPorSentido:{IDA:[],VUELTA:[P('Parada Olaya vuelta','Olaya',1),Object.assign(P('Parada inactiva','Centro',2),{estado:'INACTIVA'}),P('Parada Centro vuelta','Centro',3)]},buses:[]},
 {id:5,nombre:'Ruta inactiva',estado:'INACTIVA',barrios:['Olaya ST. Central','Centro'],barriosOrdenados:['Olaya ST. Central','Centro'],ordenBarriosConfirmado:true,paradas:[],buses:[]},
 {id:6,nombre:'Ruta barrio repetido',barrios:['Prueba A','Prueba B','Prueba A','Prueba Destino'],barriosOrdenados:['Prueba A','Prueba B','Prueba A','Prueba Destino'],ordenBarriosConfirmado:true,paradas:[P('Parada A primera','Prueba A',1),P('Parada B','Prueba B',2),P('Parada A segunda','Prueba A',3),P('Parada Destino','Prueba Destino',4)],buses:[]},
 {id:7,nombre:'Ruta origen lejano',barrios:['Origen Distancia','Transbordo Lejano'],barriosOrdenados:['Origen Distancia','Transbordo Lejano'],ordenBarriosConfirmado:true,paradasPorSentido:{IDA:[P('Bajada lejana','Transbordo Lejano',1,10.3000,-75.3000)]},paradas:[],buses:[]},
 {id:8,nombre:'Ruta destino lejano',barrios:['Transbordo Lejano','Destino Distancia'],barriosOrdenados:['Transbordo Lejano','Destino Distancia'],ordenBarriosConfirmado:true,paradasPorSentido:{IDA:[P('Abordaje lejano','Transbordo Lejano',1,10.3060,-75.3000)]},paradas:[],buses:[]},
 {id:9,nombre:'Ruta origen cercano',barrios:['Origen Distancia','Transbordo Cercano'],barriosOrdenados:['Origen Distancia','Transbordo Cercano'],ordenBarriosConfirmado:true,paradasPorSentido:{IDA:[P('Bajada cercana','Transbordo Cercano',1,10.3200,-75.3000)]},paradas:[],buses:[]},
 {id:10,nombre:'Ruta destino cercano',barrios:['Transbordo Cercano','Destino Distancia'],barriosOrdenados:['Transbordo Cercano','Destino Distancia'],ordenBarriosConfirmado:true,paradasPorSentido:{IDA:[P('Abordaje cercano','Transbordo Cercano',1,10.3205,-75.3000)]},paradas:[],buses:[]}];
const M={CENTRO:[0,0],url:p=>p,fetchJSON:async u=>u.includes('barrios')?barrios:u.includes('rutas')?rutas:{features:[]},
 crearMapa:()=>({flyTo(){},removeLayer(){},addLayer(){return this},addControl(){return this},removeControl(){return this},setView(){},getZoom:()=>13,on(){},off(){},fitBounds(){},getBounds(){return {extend(){},isValid:()=>true}},getCenter(){return {lat:10.42,lng:-75.55}},invalidateSize(){}}),
 mostrarAviso:(m)=>avisos.push(m),mostrarCargando(){},ocultarCargando(){},ajustarVista(){},crearPin:()=>L.marker(),crearMarcadorParada:()=>L.marker(),crearCapasRuta:()=>({base:L.layerGroup(),linea:L.geoJSON()}),crearFlechasDireccion:()=>L.layerGroup(),vincularFlechas:()=>()=>{},
 obtenerRutaOSRM:async puntos=>{trazados.push(puntos.map(p=>[p.lat,p.lng]));return{geometry:{type:'LineString',coordinates:[[-75.55,10.42],[-75.53,10.41]]},distanciaKm:3.2,tiempoMin:11}}};
const win={BustrakerMap:M,location:{search:'',pathname:'/viajar_public'},history:{},innerWidth:1200,Option:function(t,v){return{text:t,value:v}},addEventListener(){},setInterval(){return 1},clearInterval(){}};
win.history.replaceState=(state,title,url)=>{win.location.search=url.includes('?')?url.slice(url.indexOf('?')):'';};
const ctx={Option:win.Option,window:win,document,L,URLSearchParams,Number,console,navigator:{},setTimeout,AbortController,fetch:async()=>({ok:true,json:async()=>({estado:'SIN_REPORTES',mensaje:'No hay reportes recientes.',reportantesActivos:0,llegadas:[]})})};
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(__dirname+'/../src/main/resources/static/js/map-travel.js','utf8'),ctx);
const esperar=()=>new Promise(r=>setTimeout(r,20));
const txt=id=>reg[id].textContent;
let fallos=0;const ok=(c,m)=>{console.log((c?'  OK  ':' FALLA ')+m);if(!c)fallos++};
(async()=>{
 win.BustrakerTravel.iniciar({privado:false}); await esperar();
 reg.btnPlanificar.fire('click');
 ok(txt('plannerStatus').includes('origen')&&reg.origen.attrs['aria-invalid']==='true','valida el origen vacío y orienta al usuario');
 const opts=reg.listaOrigen.children.map(o=>o.value);
 ok(JSON.stringify(opts)===JSON.stringify(['Bocagrande','Centro','Crespo','Destino Distancia','Getsemaní','Manga','Olaya','Olaya ST. Central','Origen Distancia','Prueba A','Prueba B','Prueba Destino','Transbordo Cercano','Transbordo Lejano']),'origen ofrece todos los barrios registrados: '+opts.join(', '));
 reg.origen.value='centro'; reg.origen.fire('input');
 const dest=reg.listaDestino.children.map(o=>o.value).sort();
 ok(JSON.stringify(dest)===JSON.stringify(['Bocagrande','Crespo','Destino Distancia','Getsemaní','Manga','Olaya','Olaya ST. Central','Origen Distancia','Prueba A','Prueba B','Prueba Destino','Transbordo Cercano','Transbordo Lejano']),'destinos válidos desde Centro: '+dest.join(', '));
 ok(reg.destino.disabled===false,'destino se habilita');
 reg.btnPlanificar.fire('click');
 ok(txt('plannerStatus').includes('destino')&&reg.destino.attrs['aria-invalid']==='true','valida el destino vacío');
 reg.destino.value='Manga'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 const cards=reg.resultados.children.filter(c=>c.tag==='button');
 ok(cards.length===2,'muestra todas las opciones directas Centro→Manga ('+cards.length+')');
 ok(cards[0].textContent.includes('Ruta Corta')&&cards[0].textContent.includes('Recomendada'),'Ruta Corta recomendada por recorrer menos barrios');
 ok(cards[1]&&cards[1].textContent.includes('Ruta Larga')&&cards[1].textContent.includes('Directa'),'mantiene disponible la alternativa directa más larga');
 ok(txt('resumenBusqueda').includes('2 opciones'),'resume la cantidad de opciones encontradas');
 ok(txt('detalleHora').includes('3:05 p. m.'),'hora 15:05 → 3:05 p. m.');
 ok(cards[0].textContent.includes('Sin paradas registradas')&&cards[0].textContent.includes('Sin buses asignados'),'Ruta Corta: sin paradas ni buses (no se inventan datos)');
 ok(reg.rutaDetalle.hidden===false,'itinerario recomendado se selecciona y dibuja tras planificar');
 ok(reg.llegadasRutaPanel.textContent.includes('Llegadas estimadas'),'la vista pública muestra el panel de estimaciones para la ruta elegida');
 ok(txt('detalleTramo').includes('Centro → Manga'),'tramo recomendado: '+txt('detalleTramo'));
 ok(txt('contadorParadas')==='2'&&txt('contadorBuses')==='0','Ruta Corta usa 2 centros de barrio y no inventa buses');
 ok(txt('panelBuses').includes('Sin buses asignados'),'informa cuando no hay buses registrados');
 ok(txt('rutaStats').includes('3.2 km')&&txt('rutaStats').includes('~11 min'),'estadísticas OSRM: '+txt('rutaStats'));
 cards[1].fire('click'); await esperar();
 ok(txt('detalleTitulo').includes('Ruta Larga'),'seleccionar una alternativa actualiza el detalle');
 ok(trazados.length>=2&&trazados[trazados.length-1].length>trazados[0].length,'seleccionar una alternativa dibuja su propio recorrido en el mapa');
 reg.origen.value='Bocagrande'; reg.origen.fire('input');
 reg.destino.value='Crespo'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 const transbordo=reg.resultados.children.filter(c=>c.tag==='button');
 ok(transbordo.length===2,'sin ruta directa muestra las combinaciones disponibles ('+transbordo.length+')');
 ok(transbordo[0].textContent.includes('Ruta Corta')&&transbordo[0].textContent.includes('Ruta Larga'),'elige el transbordo con menos barrios intermedios');
 ok(txt('resumenBusqueda').includes('1 transbordo')&&txt('resumenBusqueda').includes('2 barrios intermedios'),'resume el coste del transbordo: '+txt('resumenBusqueda'));
 ok(reg.rutaDetalle.hidden===false,'la primera combinación de transbordo se selecciona y dibuja');
 const rutaCompartidaInicial=new URLSearchParams(win.location.search).get('r');
 transbordo[1].fire('click'); await esperar();
 const rutaCompartidaAlternativa=new URLSearchParams(win.location.search).get('r');
 ok(rutaCompartidaInicial!==rutaCompartidaAlternativa,'el enlace distingue alternativas con las mismas rutas y diferente punto de transbordo');
 reg.origen.value='Centro'; reg.origen.fire('input');
 reg.destino.value='Crespo'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 const ordenadas=reg.resultados.children.filter(c=>c.tag==='button');
 ok(ordenadas.length===2&&ordenadas[0].textContent.includes('Directa')&&ordenadas[1].textContent.includes('1 transbordo'),'ordena primero las rutas directas y después las opciones con transbordo');
 ok(ordenadas[0].textContent.includes('Recomendada')&&txt('detalleTitulo').includes('Ruta Larga'),'selecciona y dibuja la opción mejor ordenada');
 ordenadas[1].fire('click'); await esperar();
 ok(txt('panelInfo').includes('Transbordo en'),'el detalle informa cada transbordo de la opción seleccionada: '+txt('detalleTramo')+' | '+txt('panelInfo'));
 reg.origen.value='Manga'; reg.origen.fire('input');
 ok(reg.destino.value==='' && reg.rutaDetalle.hidden===true,'cambiar origen reinicia destino y detalle');
 ok(reg.listaDestino.children.length===13,'desde Manga se pueden elegir todos los otros barrios');
 reg.origen.value='Crespo'; reg.origen.fire('input');
 ok(reg.destino.disabled===false && reg.listaDestino.children.length===13,'destino disponible aunque el barrio no tenga rutas salientes');
 reg.destino.value='  OLAYA   ST. CENTRAL  '; reg.destino.fire('input'); await esperar();
 ok(reg.destino.value==='Olaya ST. Central','resuelve el barrio ignorando mayúsculas y espacios repetidos');
 reg.btnPlanificar.fire('click'); await esperar();
 ok(txt('resultados').includes('No hay combinación de rutas'),'barrio válido sin ruta muestra resultado vacío, no bloquea el destino');
 reg.origen.value='OLAYA   ST. CENTRAL'; reg.origen.fire('input');
 ok(reg.origen.value==='Olaya ST. Central' && reg.destino.disabled===false,'permite origen con espacios repetidos aunque no tenga servicio confirmado');
 reg.destino.value='';
 for (const c of 'Centro') { reg.destino.value+=c; reg.destino.fire('input'); }
 await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 ok(txt('resultados').includes('No hay combinación de rutas'),'consulta entre barrios sin ruta confirmada termina sin inventar recorrido');
 reg.origen.value='Olaya'; reg.origen.fire('input');
 reg.destino.value='Centro'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 const vuelta=reg.resultados.children.filter(c=>c.tag==='button');
 ok(vuelta.length===1&&vuelta[0].textContent.includes('Vuelta'),'planifica el sentido VUELTA solo cuando está confirmado por paradas: '+(vuelta[0]&&vuelta[0].textContent));
 ok(txt('detalleTitulo').includes('Vuelta')&&!txt('panelInfo').includes('Parada inactiva'),'el detalle identifica el sentido y descarta las paradas inactivas');
 const tarjetaLlegadas=reg.llegadasRutaPanel.children.find(c=>c.tag==='article');
 ok(tarjetaLlegadas&&tarjetaLlegadas.children[0].children[1].value==='VUELTA','las llegadas usan por defecto el sentido real del tramo');
 reg.origen.value='Centro'; reg.origen.fire('input');
 reg.destino.value='Olaya'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 ok(!txt('panelInfo').includes('Parada Olaya vuelta')&&!txt('panelInfo').includes('Parada Centro vuelta'),
   'no reutiliza las paradas VUELTA como si fueran paradas IDA');
 reg.origen.value='Prueba A'; reg.origen.fire('input');
 reg.destino.value='Prueba Destino'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 const repetidas=reg.resultados.children.filter(c=>c.tag==='button');
 ok(repetidas.length===2,'conserva ambas ocurrencias válidas del barrio repetido ('+repetidas.length+')');
 ok(txt('panelInfo').includes('Parada A segunda')&&!txt('panelInfo').includes('Parada A primera'),'el tramo corto asocia la parada a la ocurrencia exacta del barrio');
 const firmaRepetida=new URLSearchParams(win.location.search).get('r');
 repetidas[1].fire('click'); await esperar();
 ok(firmaRepetida!==new URLSearchParams(win.location.search).get('r'),'el enlace diferencia las ocurrencias del mismo barrio');
 reg.origen.value='Origen Distancia'; reg.origen.fire('input');
 reg.destino.value='Destino Distancia'; reg.destino.fire('input'); await esperar();
 reg.btnPlanificar.fire('click'); await esperar();
 const caminatas=reg.resultados.children.filter(c=>c.tag==='button');
 ok(caminatas.length===2&&caminatas[0].textContent.includes('Ruta origen cercano')
   &&caminatas[0].textContent.includes('Ruta destino cercano'),'recomienda el transbordo con menor distancia entre paradas');
 ok(caminatas[0].textContent.includes('Distancia directa estimada'),'explica que la conexión se calcula entre paradas registradas');
 console.log(fallos?`\n${fallos} FALLOS`:'\nTODAS LAS PRUEBAS OK');process.exit(fallos?1:0);
})();
