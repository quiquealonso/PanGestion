// Comun a todas las paginas de la web: aviso de privacidad y analitica opcional (GoatCounter, sin cookies).
(function () {
  'use strict';

  document.documentElement.classList.remove('no-js');

  // ---------- Aviso de privacidad ----------
  // La web no usa cookies de seguimiento, asi que no hace falta pedir consentimiento: solo se informa, una vez.
  var KEY = 'pangestion.aviso-privacidad';
  var seen = false;
  try {
    seen = localStorage.getItem(KEY) === '1';
  } catch (e) {
    seen = false;
  }
  if (!seen) {
    var notice = document.createElement('div');
    notice.className = 'privacy-notice';
    notice.setAttribute('role', 'region');
    notice.setAttribute('aria-label', 'Aviso de privacidad');
    notice.innerHTML =
      '<p>🍪 Aquí no hay cookies de publicidad ni de seguimiento. Solo guardamos en tu navegador lo imprescindible ' +
      'para que todo funcione. <a href="privacidad.html">Más información</a></p>' +
      '<button type="button" class="btn btn--small">Entendido</button>';
    notice.querySelector('button').addEventListener('click', function () {
      try {
        localStorage.setItem(KEY, '1');
      } catch (e) {
        // Sin almacenamiento (modo privado): se oculta hasta la proxima visita
      }
      notice.remove();
    });
    document.addEventListener('DOMContentLoaded', function () {
      document.body.appendChild(notice);
    });
    if (document.readyState !== 'loading') document.body.appendChild(notice);
  }

  // ---------- Analitica (GoatCounter: sin cookies ni datos personales) ----------
  // Se activa poniendo el codigo de GoatCounter en <body data-goatcounter="pangestion">. Vacio = sin analitica.
  function analytics() {
    var code = document.body.dataset.goatcounter;
    if (!code || !/^[a-z0-9-]+$/.test(code)) return;
    var script = document.createElement('script');
    script.async = true;
    script.src = 'https://gc.zgo.at/count.js';
    script.dataset.goatcounter = 'https://' + code + '.goatcounter.com/count';
    document.head.appendChild(script);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', analytics);
  else analytics();
})();
