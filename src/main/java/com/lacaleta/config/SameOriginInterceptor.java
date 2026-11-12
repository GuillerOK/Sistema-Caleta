package com.lacaleta.config;

import jakarta.servlet.http.*;
import org.springframework.web.servlet.HandlerInterceptor;
import java.net.URI;
import java.util.Set;

/** Rechaza mutaciones iniciadas desde otros sitios con cookies de sesión, soportando proxies inversos como Render. */
public class SameOriginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) return true;
        String origin = request.getHeader("Origin");
        boolean valido = !"cross-site".equals(request.getHeader("Sec-Fetch-Site"));
        if (origin != null) {
            try {
                URI origen = URI.create(origin);
                URI destino = URI.create(request.getRequestURL().toString());

                // Soporte para despliegues con terminación SSL en proxies (Render, AWS, Cloudflare)
                String forwardedProto = request.getHeader("X-Forwarded-Proto");
                String forwardedHost = request.getHeader("X-Forwarded-Host");

                String destinoScheme = (forwardedProto != null && !forwardedProto.isBlank())
                        ? forwardedProto
                        : destino.getScheme();

                String rawHost = (forwardedHost != null && !forwardedHost.isBlank())
                        ? forwardedHost
                        : destino.getHost();
                String destinoHost = rawHost != null && rawHost.contains(":")
                        ? rawHost.substring(0, rawHost.indexOf(":"))
                        : rawHost;

                boolean schemeCoincide = origen.getScheme() != null
                        && origen.getScheme().equalsIgnoreCase(destinoScheme);
                boolean hostCoincide = origen.getHost() != null
                        && origen.getHost().equalsIgnoreCase(destinoHost);

                valido &= schemeCoincide && hostCoincide;
            } catch (IllegalArgumentException error) { valido = false; }
        }
        if (!valido) response.sendError(403, "Origen no permitido");
        return valido;
    }
}
