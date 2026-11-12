document.addEventListener("DOMContentLoaded", () => {
    const raiz = document.querySelector("[data-category-code]");
    const categoria = normalizarCategoria(raiz?.dataset.categoryCode || "");
    const esBebida = ["bebida", "bebidas"].includes(categoria);
    const esCombo = ["combo", "combos"].includes(categoria)
        || document.querySelector('[data-selection-group][data-code="sabores-pizza"], [data-selection-group][data-code="sabor-bebida"]') !== null;
    const tieneExtras = document.querySelector(
        '[data-selection-group][data-role="ITEM_CARRITO"]'
    ) !== null;
    const flujoSimple = esBebida || (esCombo && !tieneExtras);
    const nombre = document.getElementById("detalleNombre")?.textContent.trim();
    if (nombre) {
        const breadcrumb = document.getElementById("breadcrumbNombre");
        if (breadcrumb) breadcrumb.textContent = nombre;
        document.title = `La Caleta | ${nombre}`;
    }

    const titulo = document.getElementById("tituloPersonalizar");
    if (titulo) {
        titulo.textContent = esCombo
            ? "Personaliza tu combo"
            : esBebida ? "Elige tu bebida" : "Personaliza tu pedido";
    }

    if (flujoSimple) {
        document.querySelector(".stepper-transparente")?.classList.add("d-none");
        document.getElementById("paso2-extras")?.classList.add("d-none");
        document.getElementById("btnAtras")?.classList.add("d-none");
        document.getElementById("btnSiguiente")?.classList.add("d-none");
        document.getElementById("btnFinalizar")?.classList.remove("d-none");
    }

    document.querySelectorAll("[data-selection-group]").forEach(grupo => {
        const opciones = [...grupo.querySelectorAll("[data-selection-option]")];
        if (opciones.length === 0) {
            grupo.classList.add("d-none");
            return;
        }
        if (grupo.dataset.role === "BASE" && esBebida && opciones.length === 1) {
            opciones[0].checked = true;
        }
        grupo.querySelectorAll("[data-selection-option]").forEach(opcion => {
            opcion.dataset.quantity = opcion.checked ? "1" : "0";
        });
        actualizarGrupo(grupo);
    });

    inicializarDesplegables();
    document.addEventListener("change", evento => {
        const opcion = evento.target instanceof Element
            ? evento.target.closest("[data-selection-option]")
            : null;
        if (!opcion) return;
        opcion.dataset.quantity = opcion.checked ? "1" : "0";
        const grupo = opcion.closest("[data-selection-group]");
        if (!grupo) return;
        procesarSeleccionGrupo(grupo);
    });

    restaurarEdicion();
    sincronizarEstadosSeleccion();
    abrirPrimerBloquePendiente(document.getElementById("paso1-pizza"));
});

function normalizarCategoria(valor) {
    return String(valor)
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .trim()
        .toLowerCase();
}

function cantidadSeleccionada(grupo) {
    return [...grupo.querySelectorAll("[data-selection-option]:checked")]
        .reduce((total, opcion) => total + Number(opcion.dataset.quantity || 1), 0);
}

function actualizarGrupo(grupo) {
    const marcadas = cantidadSeleccionada(grupo);
    const contador = grupo.querySelector("[data-selection-count]");
    const minimo = Number(grupo.dataset.min);
    const maximo = Number(grupo.dataset.max);
    if (contador) contador.textContent = `${marcadas}/${maximo}`;

    grupo.querySelectorAll("[data-selection-option]").forEach(sincronizarEstadoOpcion);

    actualizarEstadoCompletado(grupo, marcadas, minimo, maximo);
    actualizarEstadoEtiqueta(grupo, marcadas, minimo, maximo);
    actualizarEtiquetaDesplegable(grupo);
    const alerta = grupo.querySelector("[data-selection-alert]");
    if (alerta && marcadas >= minimo && marcadas <= maximo) {
        alerta.classList.add("d-none");
    }
}

function actualizarEstadoCompletado(grupo, seleccionadas, minimo, maximo) {
    // El bloque queda verde en cuanto la selección cumple el mínimo y máximo,
    // sin depender de que el usuario cierre manualmente el desplegable.
    const completo = seleccionadas > 0
        && seleccionadas >= minimo
        && seleccionadas <= maximo;
    grupo.classList.toggle("is-completed", completo);
}

function actualizarEstadoEtiqueta(grupo, seleccionadas, minimo, maximo) {
    const etiqueta = grupo.querySelector("[data-selection-status]");
    if (!etiqueta) return;

    const correcto = seleccionadas >= minimo && seleccionadas <= maximo;
    const requerido = etiqueta.dataset.required === "true";
    etiqueta.classList.toggle("estado-completado", correcto && requerido);
    etiqueta.classList.toggle("estado-requerido", !correcto && requerido);
    etiqueta.classList.toggle("estado-opcional", !requerido);

    if (correcto && requerido) {
        etiqueta.textContent = "Seleccionado";
    } else {
        etiqueta.textContent = requerido ? "Obligatorio" : "Opcional";
    }
}

function procesarSeleccionGrupo(grupo) {
    const seleccionadas = cantidadSeleccionada(grupo);
    const minimo = Number(grupo.dataset.min);
    const maximo = Number(grupo.dataset.max);
    const seleccionValida = seleccionadas > 0
        && seleccionadas >= minimo
        && seleccionadas <= maximo;

    if (seleccionValida) {
        grupo.classList.add("is-collapsed");
        grupo.querySelector(".option-dropdown-trigger")
            ?.setAttribute("aria-expanded", "false");
        actualizarGrupo(grupo);
        abrirSiguienteBloque(grupo);
        return;
    }

    grupo.classList.remove("is-collapsed");
    grupo.querySelector(".option-dropdown-trigger")
        ?.setAttribute("aria-expanded", "true");
    actualizarGrupo(grupo);
}

function abrirSiguienteBloque(grupoActual) {
    const seccion = grupoActual.closest("#paso1-pizza, #paso2-extras");
    if (!seccion) return;

    const grupos = [...seccion.querySelectorAll("[data-selection-group]")];
    const indiceActual = grupos.indexOf(grupoActual);
    const siguiente = grupos.slice(indiceActual + 1)
        .find(grupo => grupo.querySelector("[data-selection-option]"));
    if (!siguiente) return;

    siguiente.classList.remove("is-collapsed");
    siguiente.querySelector(".option-dropdown-trigger")
        ?.setAttribute("aria-expanded", "true");
    actualizarGrupo(siguiente);
    siguiente.scrollIntoView({ behavior: "smooth", block: "nearest" });
}

function inicializarDesplegables() {
    document.querySelectorAll("[data-selection-group]").forEach(grupo => {
        const tarjeta = grupo.querySelector(".card-acordeon-caleta");
        if (!tarjeta) return;

        const lista = tarjeta.querySelector("[data-selection-options]");
        if (!lista
                || !lista.querySelector("[data-selection-option]")
                || grupo.querySelector(".option-dropdown-trigger")) return;
        lista.id = `options-${grupo.dataset.code}`;

        const desplegable = document.createElement("button");
        desplegable.type = "button";
        desplegable.className = "option-dropdown-trigger";
        desplegable.setAttribute("aria-expanded", "false");
        desplegable.setAttribute("aria-controls", lista.id);
        desplegable.innerHTML = '<span data-option-trigger-label>Selecciona una opción</span><i class="fa-solid fa-chevron-down" aria-hidden="true"></i>';
        desplegable.addEventListener("click", () => {
            const abrir = grupo.classList.contains("is-collapsed");
            grupo.classList.toggle("is-collapsed", !abrir);
            desplegable.setAttribute("aria-expanded", String(abrir));
            actualizarGrupo(grupo);
        });

        tarjeta.insertBefore(desplegable, lista);
        grupo.classList.add("is-option-dropdown", "is-collapsed");
        actualizarEtiquetaDesplegable(grupo);
    });
}

function sincronizarEstadoOpcion(opcion) {
    const seleccionada = opcion.checked;
    const etiqueta = opcion.labels?.[0];
    const fila = opcion.closest(".selection-option");
    if (etiqueta) etiqueta.classList.toggle("is-selected", seleccionada);
    if (fila) fila.classList.toggle("is-selected", seleccionada);
    opcion.setAttribute("aria-checked", String(seleccionada));
}

function actualizarEtiquetaDesplegable(grupo) {
    const etiqueta = grupo.querySelector("[data-option-trigger-label]");
    if (!etiqueta) return;
    const seleccionadas = [...grupo.querySelectorAll("[data-selection-option]:checked")]
        .map(opcion => opcion.dataset.label || opcion.value);
    etiqueta.textContent = seleccionadas.length
        ? seleccionadas.join(", ")
        : grupo.querySelector('[data-selection-option][type="checkbox"]')
            ? "Selecciona una o más opciones"
            : "Selecciona una opción";
}

function validarGrupos(incluirExtras) {
    let valido = true;
    let primerInvalido = null;
    document.querySelectorAll("[data-selection-group]").forEach(grupo => {
        if (!incluirExtras && grupo.dataset.role === "ITEM_CARRITO") return;
        // No bloquear el pedido por un bloque vacío: puede ocurrir si el catálogo
        // no tiene adicionales activos en una categoría determinada.
        if (!grupo.querySelector("[data-selection-option]")) {
            grupo.classList.add("d-none");
            return;
        }
        grupo.classList.remove("d-none");

        const cantidad = cantidadSeleccionada(grupo);
        const correcto = cantidad >= Number(grupo.dataset.min)
            && cantidad <= Number(grupo.dataset.max);
        const alerta = grupo.querySelector("[data-selection-alert]");

        if (alerta) alerta.classList.toggle("d-none", correcto);
        if (!correcto) {
            valido = false;
            if (grupo.classList.contains("is-option-dropdown")) {
                grupo.classList.remove("is-collapsed");
                grupo.querySelector(".option-dropdown-trigger")
                    ?.setAttribute("aria-expanded", "true");
                actualizarGrupo(grupo);
            }
            primerInvalido ??= grupo;
        }
    });

    if (primerInvalido) {
        primerInvalido.scrollIntoView({ behavior: "smooth", block: "center" });
    }
    return valido;
}

function irAlPaso(paso) {
    if (paso === 2 && !validarGrupos(false)) return;

    sincronizarEstadosSeleccion();
    document.getElementById("paso1-pizza").classList.toggle("d-none", paso !== 1);
    document.getElementById("paso2-extras").classList.toggle("d-none", paso !== 2);
    abrirPrimerBloquePendiente(
        document.getElementById(paso === 1 ? "paso1-pizza" : "paso2-extras")
    );
    document.getElementById("btnAtras").classList.toggle("d-none", paso === 1);
    document.getElementById("btnSiguiente").classList.toggle("d-none", paso === 2);
    document.getElementById("btnFinalizar").classList.toggle("d-none", paso !== 2);

    const circulo = document.getElementById("stepCircle2");
    const etiqueta = document.getElementById("stepLabel2");
    const linea = document.getElementById("stepLine");
    if (paso === 1) {
        circulo.className = "rounded-circle d-flex align-items-center justify-content-center fw-bold border border-secondary text-secondary bg-transparent";
        circulo.textContent = "2";
        etiqueta.className = "small fw-bold mt-1 text-secondary";
        linea.style.backgroundColor = "rgba(255,255,255,0.2)";
    } else {
        circulo.className = "rounded-circle d-flex align-items-center justify-content-center fw-bold bg-ro text-white shadow-sm";
        circulo.innerHTML = '<i class="fa-solid fa-check"></i>';
        etiqueta.className = "small fw-bold mt-1 text-ro";
        linea.style.backgroundColor = "var(--color-rojo-naranja)";
    }
}

function abrirPrimerBloquePendiente(seccion) {
    if (!seccion) return;

    const grupoPendiente = [...seccion.querySelectorAll("[data-selection-group]")]
        .find(grupo => grupo.querySelector("[data-selection-option]")
            && !grupo.classList.contains("is-completed"));
    if (!grupoPendiente) return;

    grupoPendiente.classList.remove("is-collapsed");
    grupoPendiente.querySelector(".option-dropdown-trigger")
        ?.setAttribute("aria-expanded", "true");
    actualizarGrupo(grupoPendiente);
}

function sincronizarEstadosSeleccion() {
    document.querySelectorAll("[data-selection-group]").forEach(actualizarGrupo);
}

function restaurarEdicion() {
    const indiceEdicion = new URLSearchParams(window.location.search).get("editar");
    if (indiceEdicion === null) return;

    let carrito;
    try {
        carrito = obtenerCarrito();
    } catch (error) {
        console.error("No se pudo leer el carrito para editar el producto.", error);
        return;
    }
    const item = carrito[Number(indiceEdicion)];
    if (!item) return;

    const boton = document.getElementById("btnFinalizar");
    if (boton) boton.innerHTML = 'Guardar cambios <i class="fa-solid fa-check ms-1"></i>';

    document.querySelectorAll("[data-selection-group]").forEach(grupo => {
        const clavesGuardadas = item.personalizaciones?.[grupo.dataset.code];
        grupo.querySelectorAll("[data-selection-option]").forEach(opcion => {
            const etiqueta = opcion.dataset.label || opcion.value;
            if (Array.isArray(clavesGuardadas)) {
                opcion.checked = clavesGuardadas.includes(opcion.value);
            } else if (grupo.dataset.role !== "ITEM_CARRITO") {
                opcion.checked = Boolean(etiqueta && item.nombre.includes(etiqueta));
            }
        });
        actualizarGrupo(grupo);
    });
    document.querySelectorAll("[data-selection-group].is-option-dropdown")
        .forEach(grupo => grupo.classList.add("is-collapsed"));
    sincronizarEstadosSeleccion();
}

function recopilarSelecciones() {
    const selecciones = {};
    document.querySelectorAll("[data-selection-group]").forEach(grupo => {
        selecciones[grupo.dataset.code] = [
            ...grupo.querySelectorAll("[data-selection-option]:checked")
        ].map(opcion => ({
            clave: opcion.value,
            etiqueta: (opcion.dataset.label || opcion.value) + (Number(opcion.dataset.quantity || 1) > 1 ? ` x${opcion.dataset.quantity}` : ""),
            cantidad: Number(opcion.dataset.quantity || 1),
            productoId: opcion.dataset.productoId ? Number(opcion.dataset.productoId) : null
        }));
    });
    return selecciones;
}

function enviarAlCarritoDesdeDetalle() {
    if (!validarGrupos(true)) return;

    const elementoNombre = document.getElementById("detalleNombre");
    const elementoImagen = document.getElementById("detalleImagen");
    const elementoDesc = document.getElementById("detalleDescripcion");
    if (!elementoNombre || !elementoImagen) return;

    const selecciones = recopilarSelecciones();
    const grupoBase = document.querySelector('[data-selection-group][data-role="BASE"]');
    const opcionBase = grupoBase?.querySelector("[data-selection-option]:checked");
    const productoId = Number(opcionBase?.dataset.productoId);
    const precio = Number(opcionBase?.dataset.price);
    if (!Number.isInteger(productoId) || productoId <= 0 || !Number.isFinite(precio)) {
        if (typeof mostrarModalMensaje === "function") {
            mostrarModalMensaje("Selecciona una opción", "Selecciona una opción de producto válida.");
        } else {
            console.error("Selecciona una opción de producto válida.");
        }
        return;
    }

    const nombreBase = elementoNombre.textContent.trim();
    const descripcion = elementoDesc?.textContent.trim() || "";
    const opcionesConfiguracion = [...document.querySelectorAll(
        '[data-selection-group][data-role="CONFIGURACION"]'
    )].flatMap(grupo => {
        const marcadas = [...grupo.querySelectorAll("[data-selection-option]:checked")];
        return marcadas.map(opcion => ({
            codigo: grupo.dataset.code,
            etiqueta: (opcion.dataset.label || opcion.value) + (Number(opcion.dataset.quantity || 1) > 1 ? ` x${opcion.dataset.quantity}` : ""),
            cantidad: Number(opcion.dataset.quantity || 1)
        }));
    });
    const grupoBaseTitulo = grupoBase?.querySelector("h5")?.textContent.toLowerCase() || "";
    const etiquetasDetalle = [
        ...(grupoBaseTitulo.includes("tamaño")
            ? [opcionBase.dataset.label || opcionBase.value]
            : []),
        ...opcionesConfiguracion
        .filter(opcion => !opcion.codigo.toLowerCase().includes("cortesia"))
        .map(opcion => opcion.etiqueta)
    ];
    const cortesias = opcionesConfiguracion
        .filter(opcion => opcion.codigo.toLowerCase().includes("cortesia"))
        .map(opcion => opcion.etiqueta);

    const detalleProductoFinal = `${nombreBase}${etiquetasDetalle.length
        ? ` (${etiquetasDetalle.join(", ")})`
        : ""}${cortesias.length ? ` [Cortesía: ${cortesias.join(", ")}]` : ""}`;
    const imagenUrl = elementoImagen.getAttribute("src");
    const indiceEdicion = new URLSearchParams(window.location.search).get("editar");

    if (indiceEdicion !== null) {
        const carrito = obtenerCarrito();
        const indice = Number(indiceEdicion);
        const item = carrito[indice];
        if (!item) {
            if (typeof mostrarModalMensaje === "function") {
                mostrarModalMensaje("Producto no encontrado", "No se encontró el producto que intentas editar en el carrito.");
            } else {
                console.error("No se encontró el producto que intentas editar en el carrito.");
            }
            return;
        }
        item.productoId = productoId;
        item.nombre = detalleProductoFinal;
        item.precio = precio;
        item.imagenUrl = imagenUrl;
        item.descripcion = descripcion;
        item.personalizaciones = opcionesPersonalizacionPersistibles(selecciones);
        guardarCarrito(carrito);
    } else {
        agregarAlCarrito(detalleProductoFinal, precio, imagenUrl, descripcion, productoId);
        const carrito = obtenerCarrito();
        const item = carrito.find(producto =>
            producto.productoId === productoId && producto.nombre === detalleProductoFinal
        );
        if (item) {
            item.personalizaciones = opcionesPersonalizacionPersistibles(selecciones);
            guardarCarrito(carrito);
        }
    }

    document.querySelectorAll(
        '[data-selection-group][data-role="ITEM_CARRITO"] [data-selection-option]:checked'
    ).forEach(opcion => {
        const opcionId = Number(opcion.dataset.productoId);
        const opcionPrecio = Number(opcion.dataset.price);
        if (!Number.isInteger(opcionId) || opcionId <= 0 || !Number.isFinite(opcionPrecio)) {
            console.error("Se omitió una opción extra con datos inválidos.");
            return;
        }
        agregarAlCarrito(
            opcion.dataset.label || opcion.value,
            opcionPrecio,
            opcion.dataset.image || "/images/banner_promocional.jpg",
            "",
            opcionId
        );
    });

    window.setTimeout(() => {
        window.location.href = indiceEdicion !== null ? "/carrito" : "/menu";
    }, indiceEdicion !== null ? 300 : 1000);
}

function opcionesPersonalizacionPersistibles(selecciones) {
    return Object.fromEntries(
        Object.entries(selecciones)
            .filter(([codigo]) => {
                const grupo = document.querySelector(
                    `[data-selection-group][data-code="${CSS.escape(codigo)}"]`
                );
                return grupo?.dataset.role !== "ITEM_CARRITO";
            })
            .map(([codigo, opciones]) => [codigo, opciones.map(opcion => opcion.clave)])
    );
}
