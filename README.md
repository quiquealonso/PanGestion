# PanGestión

Plataforma de pedidos y gestión para panaderías.

- **Frontend** (esta carpeta): Angular 22 — panel de gestión + pantalla pública de pedidos.
- **Backend** ([backend/](backend/README.md)): Spring Boot 4 + Hibernate + PostgreSQL (H2 en desarrollo).

## Requisitos

- Java 21 o superior (Maven se descarga solo con `mvnw`)
- Node.js **24.15** o superior (lo exige Angular CLI 22)

## Arrancar en local

1. Backend (puerto 8080):

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

2. Frontend (puerto 4200). `npm start` usa `proxy.conf.json`, que redirige `/api` al backend:

   ```bash
   npm install --no-bin-links
   npm start
   ```

3. Abre http://localhost:4200. Los usuarios de prueba están en `backend/src/main/java/com/pangestion/config/DataInitializer.java`.
   La pantalla pública para clientes está en http://localhost:4200/pedido.

## Cambiar de sistema operativo (Windows ⇄ Linux)

`node_modules`, `.angular/`, `dist/`, `backend/target/` y `backend/data/` son **locales de cada máquina**: no se copian ni se suben a Git (están en `.gitignore`). Al cambiar de sistema:

```bash
rm -rf node_modules .angular dist backend/target
npm ci                      # en un disco exFAT/FAT: npm ci --no-bin-links
```

- **Binarios nativos**: esbuild y otros paquetes instalan un ejecutable distinto en Windows y en Linux. Un `node_modules` instalado en un sistema no funciona en el otro.
- **Discos exFAT** (como el SSD portátil): no admiten enlaces simbólicos ni permisos de ejecución. Por eso se instala con `--no-bin-links` y los scripts de `package.json` llaman al CLI de Angular con `node` directamente. En Linux, sobre ext4, basta con `npm ci`.
- **`mvnw` en Linux**: si pierde el permiso de ejecución, lanza `chmod +x backend/mvnw` o `sh backend/mvnw spring-boot:run`.
- **Finales de línea**: `.gitattributes` y `.editorconfig` fuerzan LF (y CRLF solo para `.cmd`), así `mvnw` y los scripts funcionan igual en los dos sistemas.
- **Versión de Node**: Angular CLI 22 pide Node 24.15 o superior. Usa la misma versión (LTS) en las dos máquinas.

## Compilar

```bash
npm run build            # frontend
cd backend && ./mvnw package   # backend (jar en backend/target)
```
