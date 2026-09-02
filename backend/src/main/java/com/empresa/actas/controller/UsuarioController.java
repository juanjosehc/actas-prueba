package com.empresa.actas.controller;

import com.empresa.actas.dto.response.UsuarioResponse;
import com.empresa.actas.service.UsuarioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador para la búsqueda de usuarios del directorio GLPI.
 *
 * Endpoint:
 * - GET /usuarios?texto=juan → Busca usuarios por firstname o realname.
 *
 * Utilizado por el componente de autocompletado del frontend
 * para diligenciar los campos de personas (Entregado a,
 * Entregado por, Recibido por).
 */
@RestController
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /**
     * Busca usuarios cuyo firstname o realname contengan el texto.
     *
     * @param texto Texto de búsqueda (mínimo 3 caracteres).
     * @return Lista con id y nombreCompleto de cada coincidencia.
     */
    @GetMapping("/usuarios")
    public List<UsuarioResponse> buscarUsuarios(
            @RequestParam(value = "texto", required = false, defaultValue = "") String texto
    ) {
        return usuarioService.buscarUsuarios(texto);
    }
}
