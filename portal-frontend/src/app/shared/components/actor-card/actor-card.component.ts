import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { Actor } from '../../../core/models/claim.model';

@Component({
  selector: 'app-actor-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule],
  template: `
    @if (actor) {
      <mat-card class="actor-card">
        <mat-card-header>
          <mat-icon mat-card-avatar class="actor-icon">{{ icon }}</mat-icon>
          <mat-card-title>{{ actor.name }}</mat-card-title>
          @if (actor.type) {
            <mat-card-subtitle>{{ actor.type | titlecase }}</mat-card-subtitle>
          }
        </mat-card-header>
        <mat-card-content>
          @if (actor.email) {
            <div class="info-row">
              <mat-icon>email</mat-icon>
              <span>{{ actor.email }}</span>
            </div>
          }
          @if (actor.phone) {
            <div class="info-row">
              <mat-icon>phone</mat-icon>
              <span>{{ actor.phone }}</span>
            </div>
          }
          @if (actor.address) {
            <div class="info-row">
              <mat-icon>location_on</mat-icon>
              <span>{{ actor.address }}</span>
            </div>
          }
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: [`
    .actor-card {
      margin-bottom: 16px;
    }

    .actor-icon {
      font-size: 40px;
      width: 40px;
      height: 40px;
      color: #1976d2;
      background: #e3f2fd;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .info-row {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
      font-size: 14px;
      color: #666;

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
        color: #999;
      }
    }
  `]
})
export class ActorCardComponent {
  @Input() actor?: Actor | null;
  @Input() icon: string = 'person';
}
