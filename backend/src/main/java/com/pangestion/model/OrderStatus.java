package com.pangestion.model;

/** Ciclo de vida de un pedido. */
public enum OrderStatus {
    PENDIENTE,
    EN_PROCESO,
    LISTO,
    /** Pedido a domicilio que un repartidor lleva de camino. */
    TRANSPORTANDO,
    ENTREGADO,
    CANCELADO
}
