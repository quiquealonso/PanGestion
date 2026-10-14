import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ROLE_LABELS } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-shell',
  styleUrl: './shell.component.scss',
  templateUrl: './shell.component.html',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly notifications = inject(NotificationService);
  protected readonly roleLabels = ROLE_LABELS;
}
