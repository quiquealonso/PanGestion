package com.pangestion.model;

/** Roles de acceso al panel de gestion. */
public enum Role {
    /** Acceso total, incluida la gestion de usuarios. */
    ADMIN,
    /** Gestiona pedidos, catalogo, clientes y empresas. */
    GESTOR,
    /** Solo lectura. */
    VISOR
}
