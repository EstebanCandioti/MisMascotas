package com.MisMascotas.backend.Audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.MisMascotas.backend.Entity.TipoAccionAuditoria;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String entidad();

    TipoAccionAuditoria accion();
}