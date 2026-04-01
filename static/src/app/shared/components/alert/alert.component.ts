import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export type AlertType = 'success' | 'error' | 'warning' | 'info';

@Component({
  selector: 'app-alert',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (show) {
    <div class="rounded-md p-4 mb-4" [class]="alertClasses">
      <div class="flex">
        <!-- Icon -->
        <div class="flex-shrink-0">
          <svg class="h-5 w-5" [class]="iconClasses" fill="currentColor" viewBox="0 0 20 20">
            @if (type === 'success') {
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
            }
            @if (type === 'error') {
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd" />
            }
            @if (type === 'warning') {
              <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd" />
            }
            @if (type === 'info') {
              <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
            }
          </svg>
        </div>

        <!-- Content -->
        <div class="ml-3 flex-1">
          @if (title) {
            <p class="text-sm font-medium" [class]="titleClasses">
              {{ title }}
            </p>
          }
          <div class="text-sm" [class]="messageClasses" [class.mt-1]="title">
            <ng-content>
              @if (message) {
                <span>{{ message }}</span>
              }
            </ng-content>
          </div>
        </div>

        <!-- Dismiss button -->
        @if (dismissible) {
        <div class="ml-auto pl-3">
          <div class="-mx-1.5 -my-1.5">
            <button
              type="button"
              (click)="dismiss()"
              class="inline-flex rounded-md p-1.5 focus:outline-none focus:ring-2 focus:ring-offset-2"
              [class]="dismissButtonClasses"
            >
              <svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
              </svg>
            </button>
          </div>
        </div>
        }
      </div>
    </div>
    }
  `
})
export class AlertComponent {
  @Input() type: AlertType = 'info';
  @Input() title: string = '';
  @Input() message: string = '';
  @Input() show: boolean = true;
  @Input() dismissible: boolean = true;

  @Output() dismissed = new EventEmitter<void>();

  get alertClasses(): string {
    const variants = {
      success:
        'bg-green-50 dark:bg-green-950/40 border border-green-200 dark:border-green-800',
      error: 'bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-800',
      warning:
        'bg-yellow-50 dark:bg-yellow-950/40 border border-yellow-200 dark:border-yellow-800',
      info: 'bg-blue-50 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-800'
    };
    return variants[this.type];
  }

  get iconClasses(): string {
    const variants = {
      success: 'text-green-400 dark:text-green-500',
      error: 'text-red-400 dark:text-red-500',
      warning: 'text-yellow-400 dark:text-yellow-500',
      info: 'text-blue-400 dark:text-blue-500'
    };
    return variants[this.type];
  }

  get titleClasses(): string {
    const variants = {
      success: 'text-green-800 dark:text-green-200',
      error: 'text-red-800 dark:text-red-200',
      warning: 'text-yellow-800 dark:text-yellow-200',
      info: 'text-blue-800 dark:text-blue-200'
    };
    return variants[this.type];
  }

  get messageClasses(): string {
    const variants = {
      success: 'text-green-700 dark:text-green-300',
      error: 'text-red-700 dark:text-red-300',
      warning: 'text-yellow-700 dark:text-yellow-300',
      info: 'text-blue-700 dark:text-blue-300'
    };
    return variants[this.type];
  }

  get dismissButtonClasses(): string {
    const variants = {
      success:
        'text-green-500 hover:bg-green-100 dark:hover:bg-green-900/50 focus:ring-green-600 focus:ring-offset-green-50 dark:focus:ring-offset-gray-900',
      error:
        'text-red-500 hover:bg-red-100 dark:hover:bg-red-900/50 focus:ring-red-600 focus:ring-offset-red-50 dark:focus:ring-offset-gray-900',
      warning:
        'text-yellow-500 hover:bg-yellow-100 dark:hover:bg-yellow-900/50 focus:ring-yellow-600 focus:ring-offset-yellow-50 dark:focus:ring-offset-gray-900',
      info:
        'text-blue-500 hover:bg-blue-100 dark:hover:bg-blue-900/50 focus:ring-blue-600 focus:ring-offset-blue-50 dark:focus:ring-offset-gray-900'
    };
    return variants[this.type];
  }

  dismiss(): void {
    this.show = false;
    this.dismissed.emit();
  }
}