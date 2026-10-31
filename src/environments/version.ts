/**
 * Version de la app. En local vale "dev"; al desplegar, deploy.yml reescribe este fichero con
 * <version de package.json sin el ultimo numero>.<numero de despliegue>, el commit y la fecha.
 * Para subir de version mayor o menor, cambia "version" en package.json (por ejemplo 1.1.0 -> 1.2.x).
 */
export const APP_VERSION = {
  version: 'dev',
  commit: 'local',
  date: '',
};
