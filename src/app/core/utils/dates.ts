/** Fecha local en formato yyyy-MM-dd (el que usan la API y los input type=date). */
export function toIsoDate(date: Date = new Date()): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function addDays(days: number, from: Date = new Date()): string {
  const date = new Date(from);
  date.setDate(date.getDate() + days);
  return toIsoDate(date);
}
