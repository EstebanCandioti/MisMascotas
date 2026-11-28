package com.MisMascotas.backend.Controller;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.MisMascotas.backend.DTO.CalendarioItemDTO;
import com.MisMascotas.backend.Service.CalendarioService;
import com.MisMascotas.backend.Service.UsuarioAutenticadoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/calendario")
@RequiredArgsConstructor
public class CalendarioController {
    private final CalendarioService calendario;
    private final UsuarioAutenticadoService autenticado;

    @GetMapping
    public List<CalendarioItemDTO> listar(@RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta, Principal principal) {
        return calendario.listar(desde, hasta, autenticado.obtenerIdUsuario(principal));
    }

    @GetMapping("/{id}")
    public CalendarioItemDTO detalle(@PathVariable String id, @RequestParam(required = false) String fechaProgramada,
            Principal principal) {
        return calendario.detalle(id, fechaProgramada, autenticado.obtenerIdUsuario(principal));
    }
}
