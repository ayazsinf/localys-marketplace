import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { SavedSearch, SavedSearchService } from '../../service/saved-search.service';

@Component({
  selector: 'app-saved-searches',
  templateUrl: './saved-searches.component.html',
  styleUrl: './saved-searches.component.scss',
  standalone: false
})
export class SavedSearchesComponent implements OnInit {
  searches: SavedSearch[] = [];
  isLoading = true;
  errorMessage = '';
  processingId: number | null = null;

  constructor(
    private savedSearchService: SavedSearchService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.savedSearchService.list().subscribe({
      next: searches => {
        this.searches = searches;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'SAVED_SEARCHES.ERROR_LOAD';
        this.cdr.detectChanges();
      }
    });
  }

  open(search: SavedSearch): void {
    this.router.navigate(['/'], { queryParams: this.savedSearchService.toQueryParams(search) });
  }

  toggleAlerts(search: SavedSearch): void {
    this.processingId = search.id;
    this.errorMessage = '';
    this.savedSearchService.setAlerts(search.id, !search.alertsEnabled).subscribe({
      next: updated => {
        this.searches = this.searches.map(item => item.id === updated.id ? updated : item);
        this.processingId = null;
        this.cdr.detectChanges();
      },
      error: () => this.actionFailed()
    });
  }

  remove(search: SavedSearch): void {
    this.processingId = search.id;
    this.errorMessage = '';
    this.savedSearchService.delete(search.id).subscribe({
      next: () => {
        this.searches = this.searches.filter(item => item.id !== search.id);
        this.processingId = null;
        this.cdr.detectChanges();
      },
      error: () => this.actionFailed()
    });
  }

  private actionFailed(): void {
    this.processingId = null;
    this.errorMessage = 'SAVED_SEARCHES.ERROR_ACTION';
    this.cdr.detectChanges();
  }
}
