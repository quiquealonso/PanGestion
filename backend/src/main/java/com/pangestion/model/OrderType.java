package com.pangestion.model;

/** Como recibe el cliente su pedido: lo recoge en el local o se lo llevan a casa. */
public enum OrderType {
    /** Lo recoge el cliente en el local a la hora elegida. */
    RECOGER,
    /** Se lo lleva un repartidor a su direccion a la hora elegida. */
    DOMICILIO
}
