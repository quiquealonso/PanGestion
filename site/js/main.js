// Web promocional de PanGestion: menu movil, pestanas, recorrido de pantallas, animaciones al hacer scroll,
// precios y formulario de contacto (por email). Sin librerias.
(function () {
  'use strict';

  // ---------- Menu movil ----------
  var toggle = document.querySelector('.nav__toggle');
  var menu = document.getElementById('menu');
  toggle.addEventListener('click', function () {
    var open = toggle.getAttribute('aria-expanded') !== 'true';
    toggle.setAttribute('aria-expanded', String(open));
    toggle.setAttribute('aria-label', open ? 'Cerrar menú' : 'Abrir menú');
    menu.classList.toggle('is-open', open);
  });
  menu.addEventListener('click', function (event) {
    if (event.target.closest('a')) {
      toggle.setAttribute('aria-expanded', 'false');
      menu.classList.remove('is-open');
    }
  });

  // Sombra en la barra al bajar
  var nav = document.querySelector('.nav');
  var onScroll = function () {
    nav.classList.toggle('is-scrolled', window.scrollY > 8);
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  // ---------- Pestanas accesibles (flechas izquierda/derecha) ----------
  function tabs(container, onSelect) {
    var buttons = Array.prototype.slice.call(container.querySelectorAll('[role="tab"]'));
    function select(button, focus) {
      buttons.forEach(function (b) {
        var selected = b === button;
        b.setAttribute('aria-selected', String(selected));
        b.tabIndex = selected ? 0 : -1;
      });
      if (focus) button.focus();
      onSelect(button);
    }
    buttons.forEach(function (button, index) {
      button.addEventListener('click', function () {
        select(button, false);
      });
      button.addEventListener('keydown', function (event) {
        var next = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
        if (!next) return;
        event.preventDefault();
        select(buttons[(index + next + buttons.length) % buttons.length], true);
      });
    });
  }

  // Panaderia / bar
  tabs(document.querySelector('.tabs'), function (button) {
    document.querySelectorAll('.audience').forEach(function (panel) {
      panel.hidden = panel.id !== button.getAttribute('aria-controls');
    });
  });

  // Recorrido de pantallas
  var tourImg = document.getElementById('tour-img');
  var tourText = document.getElementById('tour-text');
  tabs(document.querySelector('.tour__tabs'), function (button) {
    var src = 'assets/capturas/' + button.dataset.shot + '.webp';
    if (tourImg.getAttribute('src') === src) return;
    tourImg.classList.add('is-changing');
    var preload = new Image();
    preload.onload = function () {
      tourImg.src = src;
      tourImg.alt = 'Pantalla de ' + button.textContent.trim() + ' en PanGestión';
      tourText.textContent = button.dataset.text;
      tourImg.classList.remove('is-changing');
    };
    preload.src = src;
  });

  // ---------- Aparecer al hacer scroll ----------
  var items = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-visible');
            observer.unobserve(entry.target);
          }
        });
      },
      { rootMargin: '0px 0px -8% 0px', threshold: 0.08 },
    );
    items.forEach(function (item, i) {
      // Escalonado suave entre elementos seguidos
      item.style.transitionDelay = Math.min(i % 6, 5) * 60 + 'ms';
      observer.observe(item);
    });
  } else {
    items.forEach(function (item) {
      item.classList.add('is-visible');
    });
  }

  // ---------- Precios (se configuran en data-price del HTML) ----------
  document.querySelectorAll('.plan').forEach(function (plan) {
    plan.querySelector('.plan__price').textContent = plan.dataset.price || 'Pide presupuesto';
  });

  // Al pulsar "Me interesa" se apunta el plan en el formulario
  var form = document.getElementById('contact-form');
  document.querySelectorAll('[data-plan]').forEach(function (link) {
    link.addEventListener('click', function () {
      form.elements.mensaje.value = 'Me interesa el plan ' + link.dataset.plan + '. ';
    });
  });

  // ---------- Contacto: abre el correo con el mensaje escrito ----------
  var note = document.getElementById('contact-note');
  var EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  var PHONE = /^\+?[0-9 ]{9,15}$/;

  /** Marca un campo con su error (o lo limpia) y devuelve si es valido. */
  function check(input, message) {
    var error = document.getElementById('err-' + input.name);
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
    error.textContent = message || '';
    return !message;
  }

  function validate() {
    var f = form.elements;
    var contact = f.contacto.value.trim();
    var ok = check(f.nombre, f.nombre.value.trim().length < 2 ? 'Escribe tu nombre.' : '');
    ok = check(f.negocio, f.negocio.value.trim().length < 2 ? 'Escribe el nombre de tu negocio.' : '') && ok;
    ok = check(f.contacto, !contact ? 'Dinos cómo contactarte.' : !EMAIL.test(contact) && !PHONE.test(contact)
      ? 'Escribe un email (nombre@correo.es) o un teléfono de 9 cifras.' : '') && ok;
    return ok;
  }
  ['nombre', 'negocio', 'contacto'].forEach(function (name) {
    form.elements[name].addEventListener('blur', function () {
      if (form.elements[name].value) validate();
    });
  });

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    var f = form.elements;
    if (f.web.value) return; // trampa para bots
    if (!validate()) {
      note.textContent = 'Revisa los campos marcados.';
      form.querySelector('[aria-invalid="true"]').focus();
      return;
    }
    var email = form.dataset.email;
    if (!email) {
      note.textContent = 'El formulario de contacto estará disponible muy pronto.';
      return;
    }
    var body = [
      'Nombre: ' + f.nombre.value.trim(),
      'Negocio: ' + f.negocio.value.trim() + ' (' + f.tipo.value + ')',
      'Contacto: ' + f.contacto.value.trim(),
      '',
      f.mensaje.value.trim(),
    ].join('\n');
    window.location.href =
      'mailto:' + email + '?subject=' + encodeURIComponent('Demo de PanGestión · ' + f.negocio.value.trim()) +
      '&body=' + encodeURIComponent(body);
    note.textContent = 'Se ha abierto tu correo con el mensaje. Envíalo y te responderemos lo antes posible.';
  });

  var year = document.getElementById('year');
  if (year) year.textContent = String(new Date().getFullYear());
})();
