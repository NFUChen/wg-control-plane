import { Component, Input, Output, EventEmitter, TemplateRef, ContentChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LoadingSpinnerComponent } from '../loading-spinner/loading-spinner.component';
import { TableColumn } from '../../../models/wireguard.interface';

export interface TableAction {
  label: string;
  icon?: string;
  action: string;
  variant?: 'primary' | 'secondary' | 'danger';
}

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [CommonModule, FormsModule, LoadingSpinnerComponent],
  template: `
    <div class="bg-white dark:bg-gray-900 shadow-sm rounded-lg border border-gray-200 dark:border-gray-700">
      <!-- Header with search and actions -->
      <div class="px-6 py-4 border-b border-gray-200 dark:border-gray-700">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">{{ title }}</h2>
            <p *ngIf="subtitle" class="text-sm text-gray-500 dark:text-gray-400 mt-1">{{ subtitle }}</p>
          </div>
          <div class="flex flex-col sm:flex-row gap-3">
            <!-- Search -->
            <div *ngIf="searchable" class="relative">
              <input
                type="text"
                [(ngModel)]="searchQuery"
                (ngModelChange)="onSearchChange($event)"
                placeholder="Search..."
                class="pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-md bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
              <svg class="absolute left-3 top-2.5 h-5 w-5 text-gray-400 dark:text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m21 21-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </div>

            <!-- Primary Action -->
            <button
              *ngIf="primaryAction"
              (click)="onPrimaryAction()"
              class="inline-flex items-center px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
            >
              <svg *ngIf="primaryAction.icon" class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" [attr.d]="primaryAction.icon" />
              </svg>
              {{ primaryAction.label }}
            </button>
          </div>
        </div>
      </div>

      <!-- Table -->
      <div class="overflow-x-auto">
        <table class="w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead class="bg-gray-50 dark:bg-gray-800/80">
            <tr>
              <th
                *ngFor="let column of columns"
                class="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800"
                [class.w-20]="column.type === 'action'"
                (click)="onSort(column)"
              >
                <div class="flex items-center gap-2">
                  {{ column.label }}
                  <svg
                    *ngIf="column.sortable && sortColumn === column.key"
                    class="w-4 h-4 text-gray-400 dark:text-gray-500"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      [attr.d]="sortDirection === 'asc' ? 'M5 15l7-7 7 7' : 'M19 9l-7 7-7-7'"
                    />
                  </svg>
                </div>
              </th>
            </tr>
          </thead>
          <tbody class="bg-white dark:bg-gray-900 divide-y divide-gray-200 dark:divide-gray-700">
            <!-- Loading State -->
            <tr *ngIf="loading">
              <td [attr.colspan]="columns.length" class="px-6 py-12">
                <app-loading-spinner [showText]="true" loadingText="Loading data..." />
              </td>
            </tr>

            <!-- Empty State -->
            <tr *ngIf="!loading && data.length === 0">
              <td [attr.colspan]="columns.length" class="px-6 py-12 text-center">
                <div class="text-gray-500 dark:text-gray-400">
                  <svg class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2M4 13h2m12 0a1 1 0 100-2 1 1 0 000 2z" />
                  </svg>
                  <p class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-1">{{ emptyMessage || 'No data found' }}</p>
                  <p class="text-sm text-gray-500 dark:text-gray-400">{{ emptySubMessage || 'Get started by creating your first item.' }}</p>
                </div>
              </td>
            </tr>

            <!-- Data Rows -->
            <tr *ngFor="let item of data; trackBy: trackByFn" class="hover:bg-gray-50 dark:hover:bg-gray-800/50">
              <td
                *ngFor="let column of columns"
                class="px-6 py-4 whitespace-nowrap"
                [class]="getCellClass(column)"
              >
                <ng-container [ngSwitch]="column.type">
                  <!-- Text -->
                  <span *ngSwitchCase="'text'" class="text-sm text-gray-900 dark:text-gray-100">
                    {{ getColumnValue(item, column.key) }}
                  </span>

                  <!-- Number -->
                  <span *ngSwitchCase="'number'" class="text-sm text-gray-900 dark:text-gray-100 font-mono">
                    {{ getColumnValue(item, column.key) | number }}
                  </span>

                  <!-- Boolean -->
                  <span *ngSwitchCase="'boolean'">
                    <span
                      class="inline-flex px-2 py-1 text-xs font-semibold rounded-full"
                      [class]="getColumnValue(item, column.key) ? 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300' : 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300'"
                    >
                      {{ getColumnValue(item, column.key) ? 'Yes' : 'No' }}
                    </span>
                  </span>

                  <!-- Date -->
                  <span *ngSwitchCase="'date'" class="text-sm text-gray-900 dark:text-gray-100">
                    {{ getColumnValue(item, column.key) | date:'short' }}
                  </span>

                  <!-- Actions -->
                  <div *ngSwitchCase="'action'" class="flex items-center gap-2">
                    <button
                      *ngFor="let action of rowActions"
                      (click)="onRowAction(action.action, item)"
                      class="text-sm font-medium rounded px-2 py-1 hover:bg-gray-100 dark:hover:bg-gray-800"
                      [class]="getActionClass(action.variant)"
                      [title]="action.label"
                    >
                      <svg *ngIf="action.icon" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" [attr.d]="action.icon" />
                      </svg>
                      <span *ngIf="!action.icon">{{ action.label }}</span>
                    </button>
                  </div>

                  <!-- Custom template -->
                  <div *ngSwitchDefault>
                    <ng-container *ngIf="customTemplate; then customTpl; else defaultTpl">
                    </ng-container>
                    <ng-template #customTpl>
                      <ng-container *ngTemplateOutlet="customTemplate; context: { $implicit: item, column: column }">
                      </ng-container>
                    </ng-template>
                    <ng-template #defaultTpl>
                      <span class="text-sm text-gray-900 dark:text-gray-100">{{ getColumnValue(item, column.key) }}</span>
                    </ng-template>
                  </div>
                </ng-container>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div *ngIf="showPagination" class="bg-gray-50 dark:bg-gray-800/80 px-6 py-3 flex items-center justify-between border-t border-gray-200 dark:border-gray-700">
        <div class="text-sm text-gray-700 dark:text-gray-300">
          Showing {{ (currentPage - 1) * pageSize + 1 }} to {{ Math.min(currentPage * pageSize, totalItems) }} of {{ totalItems }} results
        </div>
        <div class="flex items-center gap-2">
          <button
            (click)="onPageChange(currentPage - 1)"
            [disabled]="currentPage === 1"
            class="px-3 py-1 text-sm bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 text-gray-900 dark:text-gray-100 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Previous
          </button>
          <span class="px-3 py-1 text-sm text-gray-700 dark:text-gray-300">{{ currentPage }} of {{ totalPages }}</span>
          <button
            (click)="onPageChange(currentPage + 1)"
            [disabled]="currentPage === totalPages"
            class="px-3 py-1 text-sm bg-white dark:bg-gray-800 border border-gray-300 dark:border-gray-600 text-gray-900 dark:text-gray-100 rounded-md hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Next
          </button>
        </div>
      </div>
    </div>
  `
})
export class DataTableComponent<T = any> {
  @Input() title: string = '';
  @Input() subtitle: string = '';
  @Input() columns: TableColumn[] = [];
  @Input() data: T[] = [];
  @Input() loading: boolean = false;
  @Input() searchable: boolean = true;
  @Input() searchQuery: string = '';
  @Input() emptyMessage: string = '';
  @Input() emptySubMessage: string = '';

  // Actions
  @Input() primaryAction?: { label: string; icon?: string };
  @Input() rowActions: TableAction[] = [];

  // Pagination
  @Input() showPagination: boolean = false;
  @Input() currentPage: number = 1;
  @Input() pageSize: number = 10;
  @Input() totalItems: number = 0;
  @Input() totalPages: number = 1;

  // Sorting
  @Input() sortColumn: string = '';
  @Input() sortDirection: 'asc' | 'desc' = 'asc';

  // Custom template
  @ContentChild('customTemplate') customTemplate: TemplateRef<any> | null = null;

  // Events
  @Output() searchChange = new EventEmitter<string>();
  @Output() sortChange = new EventEmitter<{ column: string; direction: 'asc' | 'desc' }>();
  @Output() pageChange = new EventEmitter<number>();
  @Output() primaryActionClick = new EventEmitter<void>();
  @Output() rowActionClick = new EventEmitter<{ action: string; item: T }>();

  // Math reference for template
  Math = Math;

  trackByFn(index: number, item: any): any {
    return item.id || index;
  }

  onSearchChange(query: string): void {
    this.searchChange.emit(query);
  }

  onSort(column: TableColumn): void {
    if (!column.sortable) return;

    const direction = this.sortColumn === column.key && this.sortDirection === 'asc' ? 'desc' : 'asc';
    this.sortChange.emit({ column: column.key, direction });
  }

  onPageChange(page: number): void {
    if (page >= 1 && page <= this.totalPages) {
      this.pageChange.emit(page);
    }
  }

  onPrimaryAction(): void {
    this.primaryActionClick.emit();
  }

  onRowAction(action: string, item: T): void {
    this.rowActionClick.emit({ action, item });
  }

  getColumnValue(item: any, key: string): any {
    return key.split('.').reduce((obj, prop) => obj?.[prop], item);
  }

  getCellClass(column: TableColumn): string {
    const baseClass = 'text-sm';
    switch (column.type) {
      case 'number':
        return `${baseClass} text-right`;
      case 'action':
        return `${baseClass} text-right`;
      default:
        return baseClass;
    }
  }

  getActionClass(variant?: string): string {
    switch (variant) {
      case 'danger':
        return 'text-red-600 hover:text-red-800 hover:bg-red-50 dark:text-red-400 dark:hover:text-red-300 dark:hover:bg-red-950/50';
      case 'primary':
        return 'text-blue-600 hover:text-blue-800 hover:bg-blue-50 dark:text-blue-400 dark:hover:text-blue-300 dark:hover:bg-blue-950/50';
      default:
        return 'text-gray-600 hover:text-gray-800 dark:text-gray-400 dark:hover:text-gray-200 dark:hover:bg-gray-800';
    }
  }
}