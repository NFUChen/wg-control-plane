import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      class="flex items-center justify-center"
      [class]="containerClass"
      [attr.aria-label]="loadingText"
    >
      <div class="animate-spin rounded-full border-4 border-gray-300 dark:border-gray-600 border-t-blue-600 dark:border-t-blue-500"
           [class]="spinnerSize">
      </div>
      <span *ngIf="showText" class="ml-3 text-gray-600 dark:text-gray-300 font-medium">
        {{ loadingText }}
      </span>
    </div>
  `
})
export class LoadingSpinnerComponent {
  @Input() size: 'sm' | 'md' | 'lg' = 'md';
  @Input() showText: boolean = false;
  @Input() loadingText: string = 'Loading...';
  @Input() containerClass: string = 'p-4';

  get spinnerSize(): string {
    switch (this.size) {
      case 'sm':
        return 'h-4 w-4';
      case 'lg':
        return 'h-12 w-12';
      default:
        return 'h-8 w-8';
    }
  }
}