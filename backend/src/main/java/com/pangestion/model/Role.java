package com.pangestion.model;

/** Roles de acceso al panel de gestion. */
public enum Role {
    /** Gestiona las empresas (panaderias) y sus usuarios. No pertenece a ninguna empresa. */
    SUPERADMIN,
    /** Acceso total a su empresa, incluida la gestion de sus usuarios. */
    ADMIN,
    /** Gestiona pedidos, catalogo, clientes y empresas. */
    GESTOR,
    /** Solo lectura. */
    VISOR
}
