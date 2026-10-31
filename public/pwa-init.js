// GitHub Pages sirve 404.html (esta app) en cualquier ruta que no existe. Fuera de /PanGestion/app/:
// - enlaces de cuando la app estaba en la raiz (/PanGestion/pedido, /PanGestion/auth/login...): a su sitio nuevo
// - cualquier otra ruta: a la pagina "no encontrada" de la web
(function () {
  var base = new URL(document.baseURI).pathname; // /PanGestion/app/ en produccion, / en local
  var parent = base.replace(/[^/]+\/$/, ''); // /PanGestion/
  var path = location.pathname;
  if (parent === base || path + '/' === base || path.indexOf(base) === 0 || path.indexOf(parent) !== 0) return;
  var first = path.slice(parent.length).split('/')[0];
  var appRoutes = ['pedido', 'auth', 'dashboard', 'orders', 'production', 'customers', 'catalog', 'service', 'bar',
    'dining-report', 'delivery', 'kitchen', 'dining', 'company', 'companies', 'users'];
  location.replace(appRoutes.indexOf(first) >= 0
    ? base + path.slice(parent.length) + location.search + location.hash
    : parent + 'no-encontrada.html');
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
