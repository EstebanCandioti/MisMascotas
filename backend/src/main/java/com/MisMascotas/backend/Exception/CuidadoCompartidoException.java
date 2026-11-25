package com.MisMascotas.backend.Exception;

public class CuidadoCompartidoException extends RuntimeException {
    private final String codigo;
    private final boolean expirada;

    public CuidadoCompartidoException(String codigo, String mensaje) { this(codigo, mensaje, false); }

    private CuidadoCompartidoException(String codigo, String mensaje, boolean expirada) {
        super(mensaje); this.codigo = codigo; this.expirada = expirada;
    }

    public static CuidadoCompartidoException invitacionExpirada() {
        return new CuidadoCompartidoException("INVITACION_EXPIRADA", "La invitacion ya no esta vigente", true);
    }

    public String getCodigo() { return codigo; }
    public boolean isExpirada() { return expirada; }
}
