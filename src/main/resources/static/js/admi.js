function mostrarSeccion(idSeccion, elementoBtn) {
    document.querySelectorAll('.caleta-seccion').forEach(sec => sec.classList.remove('activa'));
    document.querySelectorAll('.caleta-menu-btn').forEach(btn => btn.classList.remove('activo'));

    const seccionDestino = document.getElementById(idSeccion);
    if (seccionDestino) {
        seccionDestino.classList.add('activa');
    }
    if (elementoBtn) {
        elementoBtn.classList.add('activo');
    }

    const titulos = {
        'dashboard': 'Dashboard de Control',
        'productos': 'Catálogo de Pizzas & Menú',
        'stock': 'Control de Insumos y Materia Prima',
        'ventas': 'Historial de Pedidos',
        'clientes': 'Directorio de Clientes'
    };
    document.getElementById('tituloSeccion').innerText = titulos[idSeccion] || 'Panel de Administración';
}

function filtrarTabla(inputId, tablaId) {
    const input = document.getElementById(inputId);
    const filtro = input.value.toLowerCase();
    const tabla = document.getElementById(tablaId);
    const filas = tabla.getElementsByTagName('tr');

    for (let i = 1; i < filas.length; i++) {
        const filaTexto = filas[i].textContent.toLowerCase();
        filas[i].style.display = filaTexto.includes(filtro) ? '' : 'none';
    }
}

function abrirModalEditar(btn) {
    document.getElementById('edit-id').value = btn.getAttribute('data-id');
    document.getElementById('edit-nombre').value = btn.getAttribute('data-nombre');
    document.getElementById('edit-descripcion').value = btn.getAttribute('data-descripcion') || '';
    document.getElementById('edit-precio').value = btn.getAttribute('data-precio');

    if (document.getElementById('edit-categoria')) {
        document.getElementById('edit-categoria').value = btn.getAttribute('data-categoria') || 'clasicas';
        actualizarCapacidadPersonasEdicion();
    }
    if (document.getElementById('edit-capacidad-personas')) {
        document.getElementById('edit-capacidad-personas').value =
            btn.getAttribute('data-capacidad-personas') || '';
    }
    if (document.getElementById('edit-tamano')) {
        const tamano = btn.getAttribute('data-tamano');
        const selectorTamano = document.getElementById('edit-tamano');
        selectorTamano.value = Array.from(selectorTamano.options).some(option => option.value === tamano)
            ? tamano : 'Familiar';
    }
    if (document.getElementById('edit-disponible')) {
        document.getElementById('edit-disponible').value = btn.getAttribute('data-disponible');
    }

    const modal = new bootstrap.Modal(document.getElementById('modalEditarPizza'));
    modal.show();
}

function actualizarCapacidadPersonasEdicion() {
    const categoria = document.getElementById('edit-categoria');
    const contenedor = document.getElementById('edit-capacidad-personas-wrap');
    if (!categoria || !contenedor) return;

    const codigo = (categoria.value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    const aplica = ['combos', 'promociones', 'promocion'].includes(codigo);
    contenedor.hidden = !aplica;
}

function abrirModalEditarInsumo(btn) {
    document.getElementById('edit-insumo-id').value = btn.getAttribute('data-id');
    document.getElementById('edit-insumo-nombre').value = btn.getAttribute('data-nombre');
    document.getElementById('edit-insumo-stock').value = btn.getAttribute('data-stock');
    const unidadActual = (btn.getAttribute('data-unidad') || 'kg').trim().toLowerCase();
    const selectorUnidad = document.getElementById('edit-insumo-unidad');
    selectorUnidad.value = ['kg', 'g', 'litros', 'unidades'].includes(unidadActual) ? unidadActual : 'kg';
    document.getElementById('edit-insumo-minimo').value = btn.getAttribute('data-minimo');

    const modal = new bootstrap.Modal(document.getElementById('modalEditarInsumo'));
    modal.show();
}



function cerrarSesion() {
    localStorage.removeItem("sesion_activa");
    localStorage.removeItem("token");
    window.location.href = "/logout";
}

window.addEventListener('DOMContentLoaded', () => {
    fetchDashboardMetrics();
    document.getElementById('edit-categoria')?.addEventListener('change', actualizarCapacidadPersonasEdicion);

    // Permite conservar la pestaña activa si hay hash en la URL (ej. #stock)
    if (window.location.hash) {
        const hash = window.location.hash.substring(1);
        const botonesNav = document.querySelectorAll('.caleta-menu-btn');
        if (hash === 'stock') {
            mostrarSeccion('stock', botonesNav[2]);
        } else if (hash === 'productos') {
            mostrarSeccion('productos', botonesNav[1]);
        } else if (hash === 'dashboard') {
            mostrarSeccion('dashboard', botonesNav[0]);
        }
    }
});

async function fetchDashboardMetrics() {
    try {
        const response = await fetch('/api/admin/dashboard/metricas');
        if (response.ok) {
            const data = await response.json();
            
            const kpiVentas = document.getElementById('kpi-ventas-dia');
            if (kpiVentas) kpiVentas.textContent = `S/ ${data.ventasDelDia.toFixed(2)}`;

            const kpiPizzas = document.getElementById('kpi-pizzas-catalogo');
            if (kpiPizzas) kpiPizzas.textContent = `${data.pizzasCatalogo} Variedades`;

            const kpiInsumos = document.getElementById('kpi-insumos-criticos');
            if (kpiInsumos) kpiInsumos.textContent = `${data.insumosCriticos} Alertas`;

            const kpiClientes = document.getElementById('kpi-clientes');
            if (kpiClientes) kpiClientes.textContent = `${data.clientesRegistrados}`;
        }
    } catch (error) {
        console.error('Error fetching dashboard metrics:', error);
    }
}

// Confirmaciones integradas, sin diálogos nativos del navegador.
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('form[data-confirmar]').forEach(form => {
        form.addEventListener('submit', event => {
            event.preventDefault();
            const modal = document.getElementById('modalConfirmarAdmin');
            document.getElementById('mensajeConfirmarAdmin').textContent = form.dataset.confirmar;
            document.getElementById('aceptarConfirmarAdmin').onclick = () => form.submit();
            bootstrap.Modal.getOrCreateInstance(modal).show();
        });
    });
});

let productoReceta = null;
async function abrirReceta(id, categoria = "") {
    const esComboDesdeCatalogo = ["combos", "promociones", "promocion"].includes(String(categoria).toLowerCase());
    // Aplicar la vista inmediatamente, sin mostrar una columna antigua mientras carga la API.
    const modalVista = document.getElementById("modalReceta");
    modalVista.classList.toggle("modal-combo-items", esComboDesdeCatalogo);
    document.getElementById("configRecetaCombo").style.display = esComboDesdeCatalogo ? "block" : "none";
    document.getElementById("colContenidoCombo").style.display = esComboDesdeCatalogo ? "block" : "none";
    document.getElementById("colIngredientesReceta").style.display = esComboDesdeCatalogo ? "none" : "block";
    productoReceta = id;
    window.productoRecetaActual = id;
    if (typeof window.cargarConfigRecetaCombo === "function") {
        try { await window.cargarConfigRecetaCombo(id); }
        catch (error) { mostrarModalMensaje("Error", error.message || "No se pudo cargar el contenido."); return; }
    }
    const filtro = document.getElementById('buscarIngredienteReceta');
filtro.value = '';
filtro.dispatchEvent(new Event('input', { bubbles: true }));
    const modal = document.getElementById('modalReceta');
    modal.querySelectorAll('[data-insumo-id]').forEach(input => { input.value = 0; });
    try {
        // Los combos se guardan solo mediante combo_items: nunca leer recetas antiguas.
        if (modal.classList.contains('modal-combo-items')) {
            bootstrap.Modal.getOrCreateInstance(modal).show();
            return;
        }
        const respuesta = await fetch('/api/admin/productos/' + id + '/receta');
        if (!respuesta.ok) throw new Error('No se pudo cargar la receta.');
        const lineas = await respuesta.json();
        lineas.forEach(linea => {
            const input = modal.querySelector('[data-insumo-id="' + linea.insumoId + '"]');
            if (input) input.value = linea.cantidad;
        });
        bootstrap.Modal.getOrCreateInstance(modal).show();
    } catch (error) { mostrarModalMensaje('Error', error.message); }
}
document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('formReceta').addEventListener('submit', async event => {
        event.preventDefault();
        const boton = document.getElementById('guardarReceta');
        if (boton.disabled) return;
        boton.disabled = true;
        try {
            const esCombo = document.getElementById('modalReceta').classList.contains('modal-combo-items');
            if (esCombo) {
                // Un único botón y una única fuente de verdad: combo_items.
                if (typeof window.guardarContenidoCombo !== 'function' ||
                    !await window.guardarContenidoCombo({silencioso:true})) return;
            } else {
                const lineas = Array.from(document.querySelectorAll('#modalReceta [data-insumo-id]'))
                    .filter(input => Number(input.value) > 0)
                    .map(input => ({ insumoId: Number(input.dataset.insumoId), cantidad: input.value }));
                const respuesta = await fetch('/api/admin/productos/' + productoReceta + '/receta', {
                    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(lineas)
                });
                const resultado = await respuesta.json().catch(() => ({}));
                if (!respuesta.ok) throw new Error(resultado.detail || resultado.message || 'No se pudo guardar la receta.');
            }
            await window.refrescarDisponibilidadAdmin?.();
            const modal = document.getElementById('modalReceta');
            modal.addEventListener('hidden.bs.modal', () => mostrarModalMensaje('Guardado correcto', esCombo ? 'Se guardó el contenido del combo.' : 'Se guardaron los insumos de la receta.'), { once: true });
            bootstrap.Modal.getOrCreateInstance(modal).hide();
        } catch (error) { mostrarModalMensaje('Error', error.message); }
        finally { boton.disabled = false; }
    });
});


function filtrarDashboardPedidos() {
  const q = (document.getElementById('buscarDashboardPedidos')?.value || '').trim().toLowerCase();
  document.querySelectorAll('#tablaDashboardPedidos tbody tr.fila-dashboard-pedido').forEach(fila => {
    fila.style.display = fila.textContent.toLowerCase().includes(q) ? '' : 'none';
  });
}

function filtrarHistorialPedidos() {
  const q = (document.getElementById('buscarHistorialPedidos')?.value || '').trim().toLowerCase();
  const estado = (document.getElementById('filtroEstadoHistorial')?.value || '').trim();
  const metodo = (document.getElementById('filtroMetodoHistorial')?.value || '').trim();
  
  let visibles = 0;
  let totalMonto = 0;
  
  document.querySelectorAll('#tablaHistorialPedidos tbody tr.fila-historial-pedido').forEach(fila => {
    const coincideTexto = !q || fila.textContent.toLowerCase().includes(q);
    const coincideEstado = !estado || fila.dataset.estado === estado;
    const coincideMetodo = !metodo || fila.dataset.metodo === metodo;
    const mostrar = coincideTexto && coincideEstado && coincideMetodo;
    
    fila.style.display = mostrar ? '' : 'none';
    if (mostrar) {
      visibles++;
      totalMonto += parseFloat(fila.dataset.monto || '0') || 0;
    }
  });
  
  const badge = document.getElementById('contadorHistorialPedidos');
  if (badge) {
    badge.textContent = `Mostrando ${visibles} pedidos · Total: S/ ${totalMonto.toFixed(2)}`;
  }
}

function exportarHistorialCSV() {
  const filas = document.querySelectorAll('#tablaHistorialPedidos tr');
  if (!filas.length) return;
  
  const lineas = [];
  filas.forEach(fila => {
    if (fila.style.display === 'none') return;
    const celdas = Array.from(fila.querySelectorAll('th, td')).map(celda => {
      let texto = celda.innerText.replace(/"/g, '""').replace(/\r?\n/g, ' ').trim();
      return `"${texto}"`;
    });
    lineas.push(celdas.join(';'));
  });
  
  const blob = new Blob(["\uFEFF" + lineas.join('\n')], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `historial_pedidos_${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// Filtros locales del administrador: no envían formularios ni cambian de sección.
function filtrarProductosAdmin() {
  const texto = (document.getElementById('buscarProducto')?.value || '').trim().toLowerCase();
  const categoria = (document.getElementById('filtroCategoriaPizza')?.value || '').toLowerCase();
  const estado = document.getElementById('filtroDisponibilidadPizza')?.value || '';
  let visibles = 0;
  
  document.querySelectorAll('#tablaProductos tbody tr').forEach(fila => {
    const coincideTexto = !texto || fila.textContent.toLowerCase().includes(texto);
    const coincideCategoria = !categoria || (fila.dataset.categoria || '').toLowerCase() === categoria;
    const coincideEstado = !estado || fila.dataset.disponibilidad === estado;
    const mostrar = coincideTexto && coincideCategoria && coincideEstado;
    fila.style.display = mostrar ? '' : 'none';
    if (mostrar) visibles++;
  });
  
  const badge = document.getElementById('contadorPizzasAdmin');
  if (badge) badge.textContent = `Mostrando ${visibles} pizzas`;
}

function filtrarInsumosAdmin() {
  const texto = (document.getElementById('buscarInsumoAdmin')?.value || '').trim().toLowerCase();
  const estado = document.getElementById('filtroEstadoInsumo')?.value || '';
  let visibles = 0;
  let criticos = 0;
  
  document.querySelectorAll('#tablaInsumosAdmin tbody tr').forEach(fila => {
    const coincideTexto = !texto || fila.textContent.toLowerCase().includes(texto);
    const coincideEstado = !estado || fila.dataset.estado === estado;
    const mostrar = coincideTexto && coincideEstado;
    fila.style.display = mostrar ? '' : 'none';
    if (mostrar) {
      visibles++;
      if (fila.dataset.estado === 'critico') criticos++;
    }
  });
  
  const badge = document.getElementById('contadorInsumosAdmin');
  if (badge) badge.textContent = `Mostrando ${visibles} insumos (${criticos} críticos)`;
}

function filtrarClientesAdmin() {
  const dni = (document.getElementById('buscarClienteDni')?.value || '').trim().toLowerCase();
  const nombre = (document.getElementById('buscarClienteNombre')?.value || '').trim().toLowerCase();
  let visibles = 0;
  
  document.querySelectorAll('#tablaClientesAdmin tbody tr.fila-cliente-admin').forEach(fila => {
    const celdas = fila.querySelectorAll('td');
    const mostrar = (!dni || (celdas[0]?.textContent || '').toLowerCase().includes(dni)) &&
                    (!nombre || ((celdas[1]?.textContent || '') + ' ' + (celdas[2]?.textContent || '')).toLowerCase().includes(nombre));
    fila.style.display = mostrar ? '' : 'none';
    if (mostrar) visibles++;
  });
  
  const badge = document.getElementById('contadorClientesAdmin');
  if (badge) badge.textContent = `Mostrando ${visibles} clientes`;
}

document.addEventListener('DOMContentLoaded', () => {
  const filtroReceta = document.getElementById('buscarIngredienteReceta');
  const listaReceta = document.getElementById('listaIngredientesReceta');
  const actualizarFiltroReceta = () => {
    if (!filtroReceta || !listaReceta) return;
    const q = filtroReceta.value.trim().toLowerCase();
    let visibles = 0;
    listaReceta.querySelectorAll('.ingrediente-receta-item').forEach(item => {
     const texto = (
    item.dataset.busqueda || item.textContent
).toLowerCase();

const coincide = q.length === 0 || texto.includes(q);

item.style.setProperty(
    'display',
    coincide ? 'block' : 'none',
    'important'
);

if (coincide) visibles++;
    });
    let aviso = document.getElementById('avisoFiltroReceta');
    if (!aviso) { aviso = document.createElement('div'); aviso.id = 'avisoFiltroReceta'; aviso.className = 'small text-secondary py-2'; listaReceta.before(aviso); }
    aviso.textContent = q.length === 0 ? `Mostrando todos los insumos: ${visibles}` : (visibles ? `Insumos encontrados: ${visibles}` : 'No se encontraron insumos.');
  };
  if (filtroReceta) filtroReceta.addEventListener('input', actualizarFiltroReceta);
  actualizarFiltroReceta();

  // Inicializar contadores al cargar la página
  filtrarHistorialPedidos();
  filtrarProductosAdmin();
  filtrarInsumosAdmin();
  filtrarClientesAdmin();

  // Al crear una pizza, el controlador puede redirigir con ?receta=ID para abrir su receta.
  const parametros = new URLSearchParams(window.location.search);
const nuevaRecetaId = parametros.get('receta');
const categoriaReceta = parametros.get('categoria') || '';

if (nuevaRecetaId) {
    mostrarSeccion(
        'productos',
        document.querySelectorAll('.caleta-menu-btn')[1]
    );
setTimeout(() => {
    abrirReceta(nuevaRecetaId, categoriaReceta);
}, 0);
}
});

// Editor de artículos del combo: los botones + son únicamente del panel administrador.
document.addEventListener('DOMContentLoaded', () => {
  const panel = document.getElementById('configRecetaCombo');
  const cont = document.getElementById('itemsComboAdmin');
  if (!panel || !cont) return;
  let catalogo = [];
  const normalizar = s => (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  const escapar = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function actualizarResumen(){const r=document.getElementById('resumenCantidadCombo');if(!r)return;let pizzas=0,bebidas=0,acompanamientos=0;cont.querySelectorAll('.combo-item-admin').forEach(row=>{const tipo=row.querySelector('.ci-tipo')?.value;const modalidad=row.querySelector('.ci-modalidad')?.value;const cantidad=Number(row.querySelector('.ci-cantidad')?.value)||0;if(modalidad==='NINGUNA')return;if(tipo==='PIZZA')pizzas+=cantidad;else if(tipo==='BEBIDA')bebidas+=cantidad;else if(tipo==='ACOMPANAMIENTO')acompanamientos+=cantidad;});r.textContent=`${pizzas} pizzas · ${bebidas} bebidas · ${acompanamientos} acompañamientos`;}
  function opcionesProducto(tipo, tamano, seleccionado) {
    const esPizza = p => {
      const categoria = normalizar((p.categoria || '') + ' ' + (p.categoriaNombre || ''));
      const nombre = normalizar(p.nombre);
      return /pizza|principal|clasica|clasico/.test(categoria)
        || (/personal|mediana|familiar|grande/.test(normalizar(p.tamano)) && !/bebida|gaseosa|combo|promocion|acompanamiento/.test(categoria + ' ' + nombre));
    };
    const esBebida = p => /bebida|gaseosa/.test(normalizar((p.categoria || '') + ' ' + (p.categoriaNombre || '')))
      || /inca kola|coca cola|bebida|gaseosa|chicha|limonada|sprite|fanta|pepsi/.test(normalizar(p.nombre));
    const esAcompanamiento = p => /acompanamiento|adicional|entrada/.test(normalizar((p.categoria || '') + ' ' + (p.categoriaNombre || '')));
    return catalogo.filter(p => tipo === 'PIZZA' ? esPizza(p) : tipo === 'BEBIDA' ? esBebida(p) : esAcompanamiento(p))
      .filter(p => tipo === 'ACOMPANAMIENTO' || !tamano || !p.tamano || normalizar(p.tamano) === normalizar(tamano) || normalizar(p.nombre).includes(normalizar(tamano).replace(/\s/g,'')))
      .map(p => {
        const etiquetaTamano = tipo === 'BEBIDA' ? (presentacionProducto(p) || p.tamano) : p.tamano;
        return `<option value="${p.id}" ${String(p.id)===String(seleccionado)?'selected':''}>${escapar(p.nombre)}${etiquetaTamano?' ('+escapar(etiquetaTamano)+')':''}</option>`;
      }).join('');
  }
  function presentacionProducto(producto) {
    const texto = normalizar((producto?.nombre || '') + ' ' + (producto?.tamano || '')).replace(/\s/g, '');
    if (/3l|3000ml/.test(texto)) return '3 L';
    if (/1[,.]5l|1500ml/.test(texto)) return '1.5 L';
    if (/600ml|0[,.]6l/.test(texto)) return '600 ml';
    if (/(^|[^0-9])1l|1000ml/.test(texto)) return '1 L';
    if (/personal/.test(texto)) return 'Personal';
    return '';
  }
  function fila(item={tipoItem:'PIZZA',modalidad:'PERSONALIZAR',cantidad:1,tamano:'Mediana'}) {
    const div=document.createElement('div'); div.className='combo-item-admin border rounded p-2'; div.style.borderColor='#555';
    div.innerHTML=`<div class="row g-2 align-items-end">
      <div class="col-6 col-md-2"><label class="form-label small">Tipo</label><select class="form-select ci-tipo"><option value="PIZZA">Pizza</option><option value="BEBIDA">Bebida</option><option value="ACOMPANAMIENTO">Acompañamiento</option></select></div>
      <div class="col-6 col-md-3"><label class="form-label small">Configuración</label><select class="form-select ci-modalidad"><option value="DEFINIDA">Definida</option><option value="PERSONALIZAR">Personalizar</option><option value="NINGUNA">Sin seleccionar / no incluye</option></select></div>
      <div class="col-6 col-md-2 ci-tamano-wrap"><label class="form-label small">Tamaño</label><select class="form-select ci-tamano"><option>Personal</option><option>Mediana</option><option>Familiar</option><option>Grande</option><option>600 ml</option><option>1 L</option><option>1.5 L</option><option>3 L</option></select></div>
      <div class="col-12 col-md-2 ci-producto-wrap"><label class="form-label small">Producto incluido</label><select class="form-select ci-producto"><option value="">Seleccionar...</option></select></div>
      <div class="col-6 col-md-2 ci-cantidad-wrap"><label class="form-label small" for="">Cantidad</label><input class="form-control ci-cantidad" type="number" min="1" max="20" value="1" inputmode="numeric" aria-label="Cantidad de artículos del combo"></div>
      <div class="col-6 col-md-1"><button type="button" class="btn btn-outline-danger w-100 ci-quitar" title="Quitar artículo">×</button></div></div>`;
    cont.appendChild(div);
    const tipo=div.querySelector('.ci-tipo'), modalidad=div.querySelector('.ci-modalidad'), tamano=div.querySelector('.ci-tamano'), prod=div.querySelector('.ci-producto');
    tipo.value=item.tipoItem||'PIZZA'; modalidad.value=item.modalidad||'PERSONALIZAR'; tamano.value=item.tamano|| (tipo.value==='PIZZA'?'Mediana':'Personal'); div.querySelector('.ci-cantidad').value=item.cantidad||1;
    function refrescar(){
      const productoActual = prod.value || item.productoId || '';
      const tamanoActual = tamano.value || item.tamano || '';
      const esPizza=tipo.value==='PIZZA';
      const esAcompanamiento=tipo.value==='ACOMPANAMIENTO';
      // Los acompañamientos pueden ser definidos o elegidos por el cliente.
      div.querySelector('.ci-modalidad').disabled=false;
      div.querySelector('.ci-tamano-wrap').style.display=esAcompanamiento?'none':'';
      tamano.innerHTML=esPizza?'<option>Personal</option><option>Mediana</option><option>Familiar</option><option>Grande</option>':'<option>600 ml</option><option>1 L</option><option>1.5 L</option><option>3 L</option>';
      let tamanoGuardado=Array.from(tamano.options).some(o=>o.value===tamanoActual)?tamanoActual:(item.tamano && Array.from(tamano.options).some(o=>o.value===item.tamano)?item.tamano:(esPizza?'Mediana':'600 ml'));
      tamano.value=tamanoGuardado;
      const necesitaProducto = modalidad.value==='DEFINIDA';
      div.querySelector('.ci-producto-wrap').style.display=necesitaProducto?'':'none';
      tamano.disabled = modalidad.value==='NINGUNA' || esAcompanamiento;
      prod.innerHTML='<option value="">Seleccionar...</option>'+opcionesProducto(tipo.value,tamano.value,productoActual);
      if(!necesitaProducto) prod.value='';
    }
    tipo.addEventListener('change',()=>{item={...item,productoId:null,tamano:tipo.value==='PIZZA'?'Mediana':'600 ml'};refrescar();actualizarResumen();});
    modalidad.addEventListener('change',()=>{refrescar();actualizarResumen();}); tamano.addEventListener('change',()=>{item={...item,tamano:tamano.value};refrescar();});
    prod.addEventListener('change',()=>{
      if(tipo.value==='BEBIDA' && modalidad.value==='DEFINIDA'){
        const elegido=catalogo.find(p=>String(p.id)===String(prod.value));
        const presentacion=presentacionProducto(elegido);
        if(presentacion && Array.from(tamano.options).some(o=>o.value===presentacion)) tamano.value=presentacion;
      }
    });
    div.querySelector('.ci-quitar').addEventListener('click',()=>{div.remove();actualizarResumen();}); div.querySelector('.ci-cantidad').addEventListener('input',actualizarResumen); tipo.addEventListener('change',actualizarResumen); refrescar(); actualizarResumen();
  }
  async function cargar(id){
    cont.innerHTML='';
    try {
      const [catRes,itemsRes]=await Promise.all([fetch(`/api/admin/productos/${id}/items-combo/catalogo`),fetch(`/api/admin/productos/${id}/items-combo`)]);
      if(!catRes.ok||!itemsRes.ok) throw new Error('No se pudo cargar el contenido del combo.');
      catalogo=await catRes.json(); const items=await itemsRes.json();
      if(items.length) items.forEach(i=>fila(i));
      else { // Compatibilidad para combos antiguos: tomar cantidades existentes como punto de partida.
        const confRes=await fetch(`/api/admin/productos/${id}/configuracion-combo`); const c=confRes.ok?await confRes.json():{};
        if(c.tipoPizza==='DEFINIDA' && c.pizzaId) fila({tipoItem:'PIZZA',modalidad:'DEFINIDA',productoId:c.pizzaId,cantidad:c.cantidadPizzas||1,tamano:c.tamanoPizza||'Mediana'});
        else if(c.tipoPizza!=='NINGUNA' && (c.cantidadPizzas||0)>0) fila({tipoItem:'PIZZA',modalidad:'PERSONALIZAR',cantidad:c.cantidadPizzas||1,tamano:c.tamanoPizza||'Mediana'});
        if((c.cantidadBebidas||0)>0) for(let n=0;n<c.cantidadBebidas;n++) fila({tipoItem:'BEBIDA',modalidad:'PERSONALIZAR',cantidad:1,tamano:c.tamanoBebida||'Personal'});
      }
    } catch(e){console.warn(e);}
  }
  document.getElementById('agregarPizzaCombo')?.addEventListener('click',()=>fila({tipoItem:'PIZZA',modalidad:'PERSONALIZAR',cantidad:1,tamano:'Mediana'}));
  document.getElementById('agregarBebidaCombo')?.addEventListener('click',()=>fila({tipoItem:'BEBIDA',modalidad:'PERSONALIZAR',cantidad:1,tamano:'600 ml'}));
  document.getElementById('agregarAcompanamientoCombo')?.addEventListener('click',()=>fila({tipoItem:'ACOMPANAMIENTO',modalidad:'DEFINIDA',cantidad:1,tamano:''}));
  window.cargarConfigRecetaCombo=async id=>{
    panel.style.display='none';
    const colCombo=document.getElementById('colContenidoCombo');
    const colInsumos=document.getElementById('colIngredientesReceta');
    try{
      const r=await fetch(`/api/admin/productos/${id}/configuracion-combo`);
      if(!r.ok)throw new Error('No se pudo consultar la configuración del combo.');
      const c=await r.json();
      const esCombo=!!c.esCombo;
      document.getElementById('modalReceta').classList.toggle('modal-combo-items', esCombo);
      panel.style.display=esCombo?'block':'none';
      if(colCombo) {
        colCombo.style.display=esCombo?'':'none';
        colCombo.className='col-12';
      }
      if(colInsumos) {
        colInsumos.style.display=esCombo?'none':'';
        colInsumos.className='col-12';
      }
      document.getElementById('tituloModalReceta').textContent=esCombo?'Contenido del combo o promoción':'Insumos por unidad vendida';
      document.getElementById('guardarReceta').textContent=esCombo?'Guardar combo o promoción':'Guardar receta';
      await cargar(id);
    }catch(e){console.warn('Error al cargar el tipo de receta:',e);throw e;}
  };
  window.guardarContenidoCombo=async({silencioso=false}={})=>{
    const id=window.productoRecetaActual;if(!id)return;
    const lista=[];let error='';
    cont.querySelectorAll('.combo-item-admin').forEach(row=>{
      const tipoItem=row.querySelector('.ci-tipo').value, modalidad=row.querySelector('.ci-modalidad').value, productoId=row.querySelector('.ci-producto').value, cantidad=Number(row.querySelector('.ci-cantidad').value), tamano=row.querySelector('.ci-tamano').value;
      if(!Number.isInteger(cantidad)||cantidad<1||cantidad>20)error='Cada cantidad debe estar entre 1 y 20.';
      if(modalidad==='DEFINIDA'&&!productoId)error='Selecciona el producto de cada artículo definido.';
      if(modalidad!=='NINGUNA') lista.push({tipoItem,modalidad,productoId:productoId?Number(productoId):null,cantidad,tamano:tipoItem==='ACOMPANAMIENTO'?'':tamano});
    });
    if(error){mostrarModalMensaje('Revisa el contenido',error);return false;}
    try{const r=await fetch(`/api/admin/productos/${id}/items-combo`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(lista)});const data=await r.json().catch(()=>({}));if(!r.ok)throw new Error(data.message||data.detail||'No se pudo guardar.');actualizarResumen();await window.refrescarDisponibilidadAdmin?.();if(!silencioso)mostrarModalMensaje('Contenido guardado','Los artículos y cantidades quedaron guardados en la base de datos.');return true;}
    catch(e){mostrarModalMensaje('Error al guardar',e.message);return false;}
  };

});

// Actualizar las etiquetas de disponibilidad del administrador sin recargar la página.
window.refrescarDisponibilidadAdmin = async function () {
  const respuesta = await fetch('/api/catalogo/disponibilidad', {cache:'no-store'});
  if (!respuesta.ok) return;
  const estados = await respuesta.json();
  const porId = new Map(estados.map(e => [String(e.id), e]));
  document.querySelectorAll('#tablaProductos tbody tr').forEach(fila => {
    const boton = fila.querySelector('button[data-id][onclick*="abrirReceta"]');
    const estado = boton && porId.get(String(boton.dataset.id));
    if (!estado) return;
    const badge = fila.querySelector('td:nth-child(8) .caleta-badge');
    if (!badge) return;
    const manual = fila.querySelector('button[data-disponible]')?.dataset.disponible;
    const habilitado = manual !== 'false';
    fila.dataset.disponibilidad = estado.disponible ? 'disponible' : 'agotado';
    badge.className = estado.disponible ? 'caleta-badge success' : 'caleta-badge danger';
    badge.textContent = !habilitado ? 'Inhabilitado' : (estado.disponible ? 'Disponible' : 'Agotado');
  });
};
document.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('tablaProductos')) {
    window.refrescarDisponibilidadAdmin().catch(() => {});
    setInterval(() => {
      if (!document.hidden) window.refrescarDisponibilidadAdmin().catch(() => {});
    }, 4000);
  }
});
