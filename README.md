# 🍕 La Caleta - Pizzería Secreta

Sistema web de comercio electrónico y gestión de pedidos para **La Caleta**, desarrollado con **Java 17**, **Spring Boot 4**, **Thymeleaf**, **PostgreSQL (Neon)** y **Bootstrap 5.3**.

---

## 🚀 Mejoras y Optimizaciones Realizadas

### ⚡ 1. Rendimiento y Velocidad de Carga (Ultra Rápido)
- **Eliminación del recálculo síncrono repetitivo:** Se desacopló la sincronización pesada de stock por base de datos de cada petición HTTP de lectura (`/`, `/menu`, `/promociones`, `/detalles`), implementando un mecanismo throttled (frecuencia mínima controlada) junto con el proceso programado `@Scheduled(fixedDelay = 20000)`.
- **Eliminación de consultas N+1 en combos:** Se optimizó la consulta de combos cargando el catálogo en un Map en memoria (`O(1)`), reduciendo drásticamente las peticiones hacia la base de datos cloud de Neon.
- **Compresión HTTP GZIP:** Activada en `application.properties` para HTML, CSS, JavaScript, JSON y SVG, reduciendo los tiempos de transferencia de red hasta en un 70%.
- **Caché HTTP en Navegador:** Configurada política de caché de 7 días (`Cache-Control: max-age=7d, public`) para todos los recursos estáticos.
- **Optimización de imágenes y banners pesados:**
  - Los 3 banners principales (`banner_menu`, `banner_promociones`, `banner_cuenta`) pasaron de pesar **~5.4 MB en PNG sin comprimir** a solo **~360 KB en JPEG optimizado** (**93% de reducción de peso** sin pérdida perceptible de calidad).
  - Logo (`lacaleta.png`) optimizado de 268 KB a 52 KB.
  - Imagen de descripción optimizada de 270 KB a 40 KB.
  - Implementación de atributos nativos `loading="lazy"` y `decoding="async"` para carga diferida de imágenes y `fetchpriority="high"` para el LCP inicial.

### 📱 2. Diseño Responsivo y Experiencia Móvil (Mobile-First)
- **Cuadrícula de productos estilo app (2 columnas en móvil):** En celulares (< 576px), el menú y promociones ahora se despliegan en 2 columnas elegantes (`row-cols-2 row-cols-md-3 row-cols-lg-4`) en lugar de 1 sola columna gigante y pesada, ofreciendo una experiencia similar a apps líderes como Rappi o UberEats.
- **Deslizamiento táctil en carruseles horizontales:** Se corrigió el bloqueo de scroll (`overflow-x: hidden`) por scroll táctil fluido nativo (`overflow-x: auto; -webkit-overflow-scrolling: touch; scroll-snap-type: x mandatory;`).
- **Encabezado y navegación móvil adaptables:**
  - Botón de login responsivo que no desborda la barra en pantallas estrechas (360px).
  - Offcanvas lateral del carrito y menú ajustados al 100vw en dispositivos compactos sin desbordamiento horizontal.
  - Campos de entrada con tamaño de fuente adecuado para prevenir el auto-zoom no deseado en iOS Safari.
- **Detalle de producto y personalizador:**
  - Ajuste de altura e imagen proporcional en pantallas móviles.
  - Bloques de personalización con áreas de toque confortables (touch targets de al menos 44px).
  - Controles de carrito y tablas de resumen responsivas con scroll horizontal suave.

### ☁️ 3. Preparado para Despliegue en Render y GitHub
- **Soporte de proxies inversos y SSL (Render):** `SameOriginInterceptor` actualizado para soportar encabezados `X-Forwarded-Proto` y `X-Forwarded-Host`, evitando errores 403 Forbidden al enviar formularios desde el dominio seguro `https://*.onrender.com`.
- **Puerto dinámico:** Soporta `${PORT:8082}` requerido por Render.
- **Dockerfile optimizado multi-etapa:** Construcción con Maven JDK 17 y ejecución con JRE 17 Alpine minimalista, limitando memoria con `-Xmx384m` para operar fluidamente dentro del plan gratuito de Render (512MB RAM).
- **Blueprint `render.yaml`:** Listo para despliegue automatizado con 1 clic en Render.

---

## 🛠️ Requisitos Previos

- **Java Development Kit (JDK) 17** o superior.
- **Maven 3.8+** (o utilizar el wrapper incluido `./mvnw`).
- Base de datos **PostgreSQL** (local o en la nube como Neon / Supabase / Render).

---

## 💻 Ejecución Local

1. **Clonar el repositorio:**
   ```bash
   git clone <URL_DE_TU_REPOSITORIO>
   cd Caletayata
   ```

2. **Configurar las variables de entorno:**
   Copia el archivo `.env.example` como `.env`:
   ```bash
   cp .env.example .env
   ```
   Asegúrate de configurar `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, y los datos de correo SMTP.

3. **Compilar y ejecutar:**
   - En Windows (PowerShell / CMD):
     ```cmd
     .\mvnw.cmd spring-boot:run
     ```
   - En Linux / macOS:
     ```bash
     ./mvnw spring-boot:run
     ```

4. **Acceder a la aplicación:**
   Abre tu navegador en: [http://localhost:8082](http://localhost:8082)

---

## 📦 Despliegue en Render

### Método A: Despliegue con Blueprint (`render.yaml`) - Recomendado
1. Sube este repositorio a tu cuenta de **GitHub**.
2. Ingresa a tu panel de **Render** ([dashboard.render.com](https://dashboard.render.com)).
3. Haz clic en **New +** y selecciona **Blueprint**.
4. Conecta tu repositorio de GitHub. Render detectará automáticamente `render.yaml`.
5. En las variables de entorno, ingresa los valores secretos:
   - `DATABASE_URL`: Tu cadena JDBC (ejemplo: `jdbc:postgresql://...neon.tech/neondb?sslmode=require`)
   - `DATABASE_USERNAME`: Tu usuario de BD
   - `DATABASE_PASSWORD`: Tu contraseña de BD
   - `MAIL_USERNAME`: Tu correo de soporte
   - `MAIL_PASSWORD`: Contraseña de aplicación de Gmail
6. Haz clic en **Apply** y tu aplicación estará en línea en minutos con HTTPS gratis.

### Método B: Despliegue Manual como Web Service (Docker)
1. En Render, crea un **Web Service**.
2. Conecta tu repositorio.
3. Elige el runtime **Docker**.
4. En **Root Directory** pon `Caletayata` (o déjalo en blanco si el repo está en la raíz).
5. Agrega las variables de entorno listadas en `.env.example`.
6. Haz clic en **Deploy Web Service**.

---

## 🔒 Variables de Entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `PORT` | Puerto HTTP donde corre la aplicación | `8082` (en Render es `10000`) |
| `DATABASE_URL` | URL JDBC de conexión PostgreSQL | Configurado en `.env` |
| `DATABASE_USERNAME` | Usuario de base de datos | Configurado en `.env` |
| `DATABASE_PASSWORD` | Contraseña de base de datos | Configurado en `.env` |
| `DDL_AUTO` | Estrategia de Hibernate (`validate`, `update`) | `validate` |
| `MAIL_HOST` | Host del servidor SMTP | `smtp.gmail.com` |
| `MAIL_PORT` | Puerto SMTP | `587` |
| `MAIL_USERNAME` | Correo de envío | Configurado en `.env` |
| `MAIL_PASSWORD` | Contraseña de aplicación | Configurado en `.env` |

---

## 📝 Licencia y Créditos
Desarrollado para **La Caleta - Pizzería Secreta**. Todos los derechos reservados.

