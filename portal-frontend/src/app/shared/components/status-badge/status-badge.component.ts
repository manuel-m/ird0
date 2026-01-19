import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ClaimStatus,
  CLAIM_STATUS_LABELS,
  CLAIM_STATUS_COLORS
} from '../../../core/models/claim.model';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span
      class="status-badge"
      [style.backgroundColor]="getColor()"
      [style.color]="getTextColor()">
      {{ getLabel() }}
    </span>
  `,
  styles: [`
    .status-badge {
      display: inline-flex;
      align-items: center;
      padding: 4px 12px;
      border-radius: 16px;
      font-size: 12px;
      font-weight: 500;
      text-transform: uppercase;
      white-space: nowrap;
    }
  `]
})
export class StatusBadgeComponent {
  @Input({ required: true }) status!: ClaimStatus | string;

  getLabel(): string {
    return CLAIM_STATUS_LABELS[this.status as ClaimStatus] || this.status;
  }

  getColor(): string {
    return CLAIM_STATUS_COLORS[this.status as ClaimStatus] || '#9e9e9e';
  }

  getTextColor(): string {
    const darkBackgrounds = ['DECLARED', 'IN_PROGRESS', 'CLOSED', 'ABANDONED'];
    return darkBackgrounds.includes(this.status as string) ? 'white' : 'black';
  }
}
