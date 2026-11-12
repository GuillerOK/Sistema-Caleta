function desplazarASeccion(idDestino, boton) {
    const destino = document.getElementById(idDestino);
    if (!destino) {
        console.warn("No se encontró el elemento con ID:", idDestino);
        return;
    }

    const header = document.getElementById('header');
    const navPromo = document.getElementById('categorias-promo');
    const offset = (header ? header.offsetHeight : 0) + (navPromo ? navPromo.offsetHeight : 0) + 15;

    const posicionElemento = destino.getBoundingClientRect().top + window.pageYOffset;
    const posicionFinal = posicionElemento - offset;

    window.scrollTo({
        top: posicionFinal,
        behavior: 'smooth'
    });

    document.querySelectorAll('#categorias-promo .btn-categoria').forEach(btn => btn.classList.remove('active'));
    if (boton) {
        boton.classList.add('active');
    }
}

document.addEventListener("DOMContentLoaded", () => {
    if (typeof verificarAccesoAdmin === "function") {
        verificarAccesoAdmin();
    }
});
// Sincronización del catálogo: muestra nuevos combos disponibles sin refresco manual.
document.addEventListener('DOMContentLoaded', () => {
  const ruta = window.location.pathname;
  if (!['/', '/menu', '/promociones', '/detalles'].includes(ruta)) return;
  let anterior = null;
  const detalle = ruta === '/detalles';
  async function consultar() {
    if (document.hidden) return;
    const respuesta = await fetch('/api/catalogo/disponibilidad', {cache:'no-store'});
    if (!respuesta.ok) return;
    const estados = await respuesta.json();
    const disponibles = new Set(estados.filter(e => e.disponible).map(e => String(e.id)));
    const firma = [...disponibles].sort().join(',');
    if (anterior !== null && firma !== anterior && !detalle) {
      // En el catálogo se vuelve a pintar el listado para incorporar nuevos combos.
      window.location.reload();
      return;
    }
    if (detalle && anterior !== null && firma !== anterior) {
      // Recuperar nuevos bloques/opciones si todavía no hay una selección en curso.
      const configurado = document.querySelector('[data-selection-group][data-role="CONFIGURACION"] [data-selection-option]:checked');
      if (!configurado) { window.location.reload(); return; }
    }
    anterior = firma;
    if (!detalle) return;
    const productoActual = document.querySelector('[data-product-id]')?.dataset.productId;
    if (productoActual && !disponibles.has(String(productoActual))) {
      document.getElementById('btnFinalizar')?.setAttribute('disabled', 'disabled');
      document.getElementById('btnSiguiente')?.setAttribute('disabled', 'disabled');
    }
    document.querySelectorAll('[data-selection-option][data-producto-id]').forEach(input => {
      const id = input.dataset.productoId;
      if (!id || id === 'null') return;
      const disponible = disponibles.has(String(id));
      input.disabled = !disponible;
      if (!disponible && input.checked) {
        input.checked = false;
        input.dispatchEvent(new Event('change', {bubbles:true}));
      }
      const fila = input.closest('.selection-option');
      if (fila) fila.style.display = disponible ? '' : 'none';
    });
  }
  consultar().catch(() => {});
  setInterval(() => consultar().catch(() => {}), 4000);
});
