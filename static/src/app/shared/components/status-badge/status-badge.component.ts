import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export type BadgeVariant = 'success' | 'danger' | 'warning' | 'info' | 'gray';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium" [class]="badgeClasses">
      <span *ngIf="showDot" class="w-2 h-2 rounded-full" [class]="dotClasses"></span>
      {{ label }}
    </span>
  `
})
export class StatusBadgeComponent {
  @Input() label: string = '';
  @Input() variant: BadgeVariant = 'gray';
  @Input() showDot: boolean = true;

  get badgeClasses(): string {
    const variants = {
      success: 'bg-green-100 text-green-800 dark:bg-green-900/50 dark:text-green-300',
      danger: 'bg-red-100 text-red-800 dark:bg-red-900/50 dark:text-red-300',
      warning: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/50 dark:text-yellow-300',
      info: 'bg-blue-100 text-blue-800 dark:bg-blue-900/50 dark:text-blue-300',
      gray: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200'
    };
    return variants[this.variant];
  }

  get dotClasses(): string {
    const dotVariants = {
      success: 'bg-green-500 dark:bg-green-400',
      danger: 'bg-red-500 dark:bg-red-400',
      warning: 'bg-yellow-500 dark:bg-yellow-400',
      info: 'bg-blue-500 dark:bg-blue-400',
      gray: 'bg-gray-500 dark:bg-gray-400'
    };
    return dotVariants[this.variant];
  }
}