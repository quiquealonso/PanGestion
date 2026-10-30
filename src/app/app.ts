import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmDialogComponent } from './shared/confirm-dialog.component';
import { AppUpdateService } from './core/services/app-update.service';
import { ThemeService } from './core/services/theme.service';

@Component({
  imports: [RouterOutlet, ConfirmDialogComponent],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  /** Se crea al arrancar para aplicar el tema (claro u oscuro) tambien en el login. */
  private readonly theme = inject(ThemeService);

  constructor() {
    inject(AppUpdateService).start();
  }
}
