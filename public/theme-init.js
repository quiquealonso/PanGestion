// Tema claro/oscuro antes de que arranque Angular (evita el parpadeo). Lo gestiona ThemeService.
try {
  var saved = localStorage.getItem('pangestion.theme');
  document.documentElement.dataset.theme = saved === 'light' || saved === 'dark' ? saved
    : (window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
} catch (e) {}
