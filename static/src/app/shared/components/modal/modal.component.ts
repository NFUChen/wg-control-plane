import { Component, Input, Output, EventEmitter, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (isOpen) {
      <!-- Modal Backdrop -->
      <div
        class="fixed inset-0 bg-gray-600/50 dark:bg-black/60 overflow-y-auto h-full w-full z-50 flex items-center justify-center"
        (click)="onBackdropClick($event)"
        [@fadeIn]
      >
        <!-- Modal Content -->
        <div
          class="relative bg-white dark:bg-gray-900 rounded-lg shadow-lg mx-4 border border-gray-200 dark:border-gray-700"
          [class]="modalSizeClass"
          (click)="$event.stopPropagation()"
          [@slideIn]
        >
          <!-- Modal Header -->
          <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
            <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ title }}
            </h3>
            @if (showCloseButton) {
              <button
                (click)="close()"
                class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors duration-200"
              >
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            }
          </div>

          <!-- Modal Body -->
          <div class="p-6" [class.pb-0]="hasFooter">
            <ng-content></ng-content>
          </div>

          <!-- Modal Footer -->
          @if (hasFooter) {
            <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/80 rounded-b-lg">
              <ng-content select="[slot=footer]"></ng-content>

              @if (!hasCustomFooter) {
                @if (showCancelButton) {
                  <button
                    (click)="cancel()"
                    type="button"
                    class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    {{ cancelLabel }}
                  </button>
                }
                @if (showConfirmButton) {
                  <button
                    (click)="confirm()"
                    type="button"
                    [disabled]="confirmDisabled"
                    class="px-4 py-2 text-sm font-medium text-white rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                    [class]="confirmButtonClass"
                  >
                    {{ confirmLabel }}
                  </button>
                }
              }
            </div>
          }
        </div>
      </div>
    }
  `,
  animations: [
    // Simple fade in/out animation
  ]
})
export class ModalComponent implements OnInit, OnDestroy {
  @Input() isOpen: boolean = false;
  @Input() title: string = '';
  @Input() size: 'sm' | 'md' | 'lg' | 'xl' = 'md';
  @Input() showCloseButton: boolean = true;
  @Input() closeOnBackdrop: boolean = true;
  @Input() closeOnEscape: boolean = true;

  // Footer controls
  @Input() hasFooter: boolean = true;
  @Input() hasCustomFooter: boolean = false;
  @Input() showCancelButton: boolean = true;
  @Input() showConfirmButton: boolean = true;
  @Input() cancelLabel: string = 'Cancel';
  @Input() confirmLabel: string = 'Save';
  @Input() confirmDisabled: boolean = false;
  @Input() confirmVariant: 'primary' | 'danger' = 'primary';

  @Output() closeModal = new EventEmitter<void>();
  @Output() confirmAction = new EventEmitter<void>();
  @Output() cancelAction = new EventEmitter<void>();

  ngOnInit(): void {
    if (this.closeOnEscape) {
      document.addEventListener('keydown', this.handleEscapeKey.bind(this));
    }
  }

  ngOnDestroy(): void {
    document.removeEventListener('keydown', this.handleEscapeKey.bind(this));
  }

  get modalSizeClass(): string {
    switch (this.size) {
      case 'sm':
        return 'max-w-md w-full';
      case 'lg':
        return 'max-w-2xl w-full';
      case 'xl':
        return 'max-w-4xl w-full';
      default:
        return 'max-w-lg w-full';
    }
  }

  get confirmButtonClass(): string {
    const baseClass = 'disabled:opacity-50 disabled:cursor-not-allowed';
    switch (this.confirmVariant) {
      case 'danger':
        return `${baseClass} bg-red-600 hover:bg-red-700 focus:ring-red-500`;
      default:
        return `${baseClass} bg-blue-600 hover:bg-blue-700 focus:ring-blue-500`;
    }
  }

  onBackdropClick(event: Event): void {
    if (this.closeOnBackdrop && event.target === event.currentTarget) {
      this.close();
    }
  }

  handleEscapeKey(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.isOpen && this.closeOnEscape) {
      this.close();
    }
  }

  close(): void {
    this.isOpen = false;
    this.closeModal.emit();
  }

  confirm(): void {
    this.confirmAction.emit();
  }

  cancel(): void {
    this.cancelAction.emit();
  }
}