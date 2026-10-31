// Enlaces de cuando la app estaba en la raiz del sitio (/PanGestion/pedido...): ahora esta en /PanGestion/app/.
// GitHub Pages los sirve con 404.html (esta app), y aqui se llevan a su sitio nuevo.
(function () {
  var base = new URL(document.baseURI).pathname; // /PanGestion/app/ en produccion, / en local
  var parent = base.replace(/[^/]+\/$/, ''); // /PanGestion/
  var path = location.pathname;
  if (parent !== base && path + '/' !== base && path.indexOf(base) !== 0 && path.indexOf(parent) === 0) {
    location.replace(base + path.slice(parent.length) + location.search + location.hash);
  }
})();

// Dos apps instalables en el mismo sitio: la de gestion y la de pedidos de clientes (/pedido).
// Antes de que arranque Angular se elige el manifiesto (nombre, icono y pantalla de inicio) segun la ruta.
try {
  var pedido = new URL('pedido', document.baseURI).pathname;
  if (location.pathname === pedido || location.pathname.indexOf(pedido + '/') === 0) {
    document.querySelector('link[rel="manifest"]').href = 'pedido.webmanifest';
    document.querySelector('link[rel="apple-touch-icon"]').href = 'icons/apple-touch-icon-pedidos.png';
    document.querySelector('meta[name="apple-mobile-web-app-title"]').content = 'Pedir';
  }
} catch (e) {}

// Android/Chrome avisa de que la app se puede instalar (beforeinstallprompt) a veces antes de que arranque Angular:
// se guarda el aviso para que el boton "Instalar" (PwaInstallService) lo use cuando quiera.
window.addEventListener('beforeinstallprompt', function (event) {
  event.preventDefault();
  window.__pgInstallPrompt = event;
  window.dispatchEvent(new Event('pg-installable'));
});
