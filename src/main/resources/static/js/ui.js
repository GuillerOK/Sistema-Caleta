// Ajustar el ancho de cada tarjeta para mostrar solo tarjetas completas.
function ajustarCarruseles() {
    document.querySelectorAll('.carrusel-horizontal').forEach(contenedor => {
        const primera = contenedor.querySelector('.tarjeta-pizza, .tarjeta-producto');
        if (!primera) return;
        const estilos = getComputedStyle(contenedor);
        const separacion = parseFloat(estilos.columnGap || estilos.gap) || 0;
        const ancho = contenedor.getBoundingClientRect().width;
        if (!ancho) return;
        const minimo = ancho < 600 ? 235 : 290;
        const visibles = Math.min(4, Math.max(1, Math.floor((ancho + separacion) / (minimo + separacion))));
        const anchoTarjeta = (ancho - separacion * (visibles - 1)) / visibles;
        contenedor.style.setProperty('--ancho-tarjeta-caleta', `${anchoTarjeta}px`);
        contenedor.dataset.visibles = String(visibles);
        const paso = anchoTarjeta + separacion;
        const indice = Math.round(contenedor.scrollLeft / paso);
        contenedor.scrollLeft = indice * paso;
    });
}
function desplazar(id, direccion) {
    const contenedor = document.getElementById(id);
    if (!contenedor) return;
    ajustarCarruseles();
    const tarjeta = contenedor.querySelector('.tarjeta-pizza, .tarjeta-producto');
    if (!tarjeta) return;
    const estilos = getComputedStyle(contenedor);
    const separacion = parseFloat(estilos.columnGap || estilos.gap) || 0;
    const paso = tarjeta.getBoundingClientRect().width + separacion;
    const indice = Math.round(contenedor.scrollLeft / paso);
    const ultimo = Math.max(0, contenedor.children.length - Number(contenedor.dataset.visibles || 1));
    const destino = Math.max(0, Math.min(ultimo, indice + (direccion < 0 ? -1 : 1)));
    contenedor.scrollTo({left: destino * paso, behavior: 'smooth'});
}
window.addEventListener('DOMContentLoaded', () => {
    ajustarCarruseles();
    if ('ResizeObserver' in window) {
        const observador = new ResizeObserver(() => ajustarCarruseles());
        document.querySelectorAll('.carrusel-horizontal').forEach(el => observador.observe(el));
    } else window.addEventListener('resize', ajustarCarruseles, {passive: true});
});

// Filtrado de pestañas de categorías
function cambiarSeccion(idSeccion, botonPresionado = null) {
    const secciones = document.querySelectorAll('.seccion-menu');
    secciones.forEach(sec => sec.classList.add('d-none'));

    const seccionActiva = document.getElementById(idSeccion);
    if (seccionActiva) {
        seccionActiva.classList.remove('d-none');
    }

    const botones = document.querySelectorAll('.btn-categoria');
    botones.forEach(btn => btn.classList.remove('active'));

    if (botonPresionado) {
        botonPresionado.classList.add('active');
    } else {
        const botonCoincidente = Array.from(botones).find(btn => 
            btn.getAttribute('onclick')?.includes(idSeccion)
        );
        if (botonCoincidente) {
            botonCoincidente.classList.add('active');
        }
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function procesarHash() {
    const hash = window.location.hash.replace('#', '');
    if (hash && document.getElementById(hash)) {
        cambiarSeccion(hash);
    }
}

window.addEventListener('DOMContentLoaded', procesarHash);
window.addEventListener('hashchange', procesarHash);