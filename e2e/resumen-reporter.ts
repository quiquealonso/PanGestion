import { appendFileSync } from 'node:fs';
import type { FullResult, Reporter, TestCase, TestResult } from '@playwright/test/reporter';

/**
 * Informe de las pruebas end-to-end: cada comprobacion que hace el "bot" (los test.step de las pruebas),
 * con ✅ o ❌ y lo que ha tardado. Sale en la consola y, en GitHub Actions, en el resumen de la ejecucion
 * (GITHUB_STEP_SUMMARY), para verlo sin abrir los logs.
 */
export default class ResumenReporter implements Reporter {
  private readonly lines: string[] = [];
  private passed = 0;
  private failed = 0;

  onTestEnd(test: TestCase, result: TestResult): void {
    const ok = result.status === 'passed' || result.status === 'skipped';
    if (ok) this.passed++;
    else this.failed++;
    const project = test.parent.project()?.name ?? '';
    const retry = result.retry ? ` (reintento ${result.retry})` : '';
    console.log(`\n${ok ? '✅' : '❌'} [${project}] ${test.title} · ${seconds(result.duration)}${retry}`);
    this.lines.push(`### ${ok ? '✅' : '❌'} ${test.title} · ${project}${retry}`, '', '| | Comprobación | Tiempo |', '|---|---|---|');

    for (const step of result.steps.filter((s) => s.category === 'test.step')) {
      const mark = step.error ? '❌' : '✅';
      console.log(`   ${mark} ${step.title} · ${seconds(step.duration)}`);
      this.lines.push(`| ${mark} | ${escape(step.title)} | ${seconds(step.duration)} |`);
    }
    if (!ok) {
      const message = firstLine(result.error?.message);
      console.log(`   → ${message}`);
      this.lines.push('', `> ❌ ${escape(message)}`);
    }
    this.lines.push('');
  }

  onEnd(result: FullResult): void {
    const header = `## 🤖 Pruebas end-to-end: ${this.passed} correctas${this.failed ? `, ${this.failed} con fallos` : ''}`;
    console.log(`\n${result.status === 'passed' ? '✅' : '❌'} ${this.passed} correctas, ${this.failed} con fallos`);
    const summary = process.env['GITHUB_STEP_SUMMARY'];
    if (!summary) return;
    const target = process.env['E2E_TARGET'] === 'produccion'
      ? 'PanGestion montada como en producción: Docker Compose, PostgreSQL, Caddy (HTTPS) y el build de producción servido como GitHub Pages.'
      : 'Entorno de desarrollo: API con H2 y ng serve.';
    appendFileSync(summary, [header, '', target, '', ...this.lines, ''].join('\n'));
  }

  printsToStdio(): boolean {
    return true;
  }
}

function seconds(ms: number): string {
  return `${(ms / 1000).toFixed(1)} s`;
}

function firstLine(text: string | undefined): string {
  // Sin los codigos de color de la consola
  return (text ?? 'Error').replace(/\u001b\[[0-9;]*m/g, '').split('\n')[0];
}

function escape(text: string): string {
  return text.replace(/\|/g, '\\|');
}
