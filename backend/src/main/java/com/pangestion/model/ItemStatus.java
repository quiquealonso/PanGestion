package com.pangestion.model;

/**
 * Estado de cada producto pedido, por separado: un plato puede estar listo mientras otro aun se prepara.
 * Avanza de uno en uno (y se puede deshacer el ultimo paso): PENDIENTE, PREPARANDO, LISTO, SERVIDO.
 */
public enum ItemStatus {
    PENDIENTE,
    PREPARANDO,
    LISTO,
    /** Llevado a la mesa por el camarero. */
    SERVIDO;

    /** Estado inicial de un producto segun donde se prepara: lo que no se prepara sale ya listo. */
    public static ItemStatus initial(Station station) {
        return station == Station.NINGUNA ? LISTO : PENDIENTE;
    }

    /** Solo se pasa al estado siguiente o se vuelve al anterior (para corregir un toque de mas). */
    public boolean canMoveTo(ItemStatus target) {
        return Math.abs(target.ordinal() - ordinal()) == 1;
    }

    /**
     * Como canMoveTo, pero en la barra no hay "preparando": la bebida pasa de pendiente a lista para servir
     * (y vuelve de lista a pendiente). Lo que ya estaba preparandose en barra puede seguir a listo.
     */
    public boolean canMoveTo(ItemStatus target, Station station) {
        if (station == Station.BARRA
                && (this == PENDIENTE && target == LISTO || this == LISTO && target == PENDIENTE)) {
            return true;
        }
        return canMoveTo(target);
    }
}
