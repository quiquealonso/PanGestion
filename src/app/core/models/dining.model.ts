/** Forma de una mesa: redonda (ovalada si no es cuadrada) o rectangular. */
export type TableShape = 'ROUND' | 'RECT';

/** Mesa del plano. Posicion y tamano en celdas de la cuadricula de su zona. */
export interface DiningTable {
  id: number;
  areaId: number;
  name: string;
  seats: number;
  shape: TableShape;
  x: number;
  y: number;
  width: number;
  height: number;
}

export type DiningTableRequest = Omit<DiningTable, 'id' | 'areaId'>;

/** Zona de la sala (terraza, interior, salon...): una cuadricula de width x height celdas. */
export interface DiningArea {
  id: number;
  name: string;
  width: number;
  height: number;
  sortOrder: number;
  /** Plazas de todas sus mesas */
  seats: number;
  tables: DiningTable[];
}

export interface DiningAreaRequest {
  name: string;
  width: number;
  height: number;
}
