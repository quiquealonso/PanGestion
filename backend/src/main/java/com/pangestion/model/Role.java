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
    VISOR,
    /** Bares y restaurantes: abre mesas y toma comandas. Fuera del servicio de mesas, solo lectura. */
    CAMARERO,
    /** Bares y restaurantes: pantalla de cocina o barra. Marca lo que prepara y lo que esta listo. */
    COCINA,
    /** Reparte los pedidos a domicilio: se lleva los que estan listos y los marca entregados. Solo ve el reparto. */
    REPARTIDOR
}
