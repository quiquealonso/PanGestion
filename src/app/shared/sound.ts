let context: AudioContext | null = null;

/**
 * Pitido corto para los avisos (cocina: pedido nuevo; camarero: plato listo).
 * Los navegadores solo dejan sonar despues de que el usuario haya tocado la pagina; si no, no suena.
 */
export function beep(times = 2): void {
  try {
    context ??= new AudioContext();
    const start = context.currentTime;
    for (let i = 0; i < times; i++) {
      const osc = context.createOscillator();
      const gain = context.createGain();
      osc.frequency.value = 880;
      gain.gain.setValueAtTime(0.0001, start + i * 0.25);
      gain.gain.exponentialRampToValueAtTime(0.25, start + i * 0.25 + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + i * 0.25 + 0.18);
      osc.connect(gain).connect(context.destination);
      osc.start(start + i * 0.25);
      osc.stop(start + i * 0.25 + 0.2);
    }
  } catch {
    // sin audio: el aviso se ve igualmente
  }
}

/** Vibracion en moviles (si el navegador la permite). */
export function vibrate(): void {
  try {
    navigator.vibrate?.([120, 80, 120]);
  } catch {
    // sin vibracion
  }
}
