import { Component, OnInit } from '@angular/core';
import { AdminListing, AdminListingService } from '../../service/admin-listing.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admin-listings',
  templateUrl: './admin-listings.component.html',
  styleUrl: './admin-listings.component.scss',
  standalone: false
})
export class AdminListingsComponent implements OnInit {
  listings: AdminListing[] = [];
  rejectionReasons: Record<number, string> = {};
  selectedStatus = 'PENDING';
  statusOptions = ['PENDING', 'APPROVED', 'REJECTED', 'ALL'];
  isLoading = false;
  processingId: number | null = null;
  errorMessage = '';

  constructor(private adminListingService: AdminListingService) {}

  ngOnInit(): void {
    this.loadListings();
  }

  loadListings(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.adminListingService.list(this.selectedStatus).subscribe({
      next: listings => {
        this.listings = listings;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'ADMIN_LISTINGS.ERROR_LOAD';
        this.isLoading = false;
      }
    });
  }

  onStatusChange(status: string): void {
    this.selectedStatus = status;
    this.loadListings();
  }

  approve(listing: AdminListing): void {
    this.processingId = listing.id;
    this.adminListingService.approve(listing.id).subscribe({
      next: () => this.removeProcessed(listing.id),
      error: () => {
        this.errorMessage = 'ADMIN_LISTINGS.ERROR_ACTION';
        this.processingId = null;
      }
    });
  }

  reject(listing: AdminListing): void {
    const reason = this.rejectionReasons[listing.id]?.trim();
    if (!reason) {
      this.errorMessage = 'ADMIN_LISTINGS.REASON_REQUIRED';
      return;
    }
    this.processingId = listing.id;
    this.adminListingService.reject(listing.id, reason).subscribe({
      next: () => this.removeProcessed(listing.id),
      error: () => {
        this.errorMessage = 'ADMIN_LISTINGS.ERROR_ACTION';
        this.processingId = null;
      }
    });
  }

  deleteListing(listing: AdminListing): void {
    this.processingId = listing.id;
    this.adminListingService.delete(listing.id, 'Removed by admin').subscribe({
      next: removed => {
        this.listings = this.listings.map(item => item.id === removed.id ? removed : item);
        if (this.selectedStatus !== 'ALL') {
          this.listings = this.listings.filter(item => item.id !== removed.id);
        }
        this.processingId = null;
        this.errorMessage = '';
      },
      error: () => {
        this.errorMessage = 'ADMIN_LISTINGS.ERROR_DELETE';
        this.processingId = null;
      }
    });
  }

  getImageUrl(url?: string): string {
    if (!url) {
      return '';
    }
    if (url.startsWith('http://') || url.startsWith('https://')) {
      return url;
    }
    const normalized = url.startsWith('/') ? url : `/${url}`;
    return `${environment.apiUrl}${normalized}`;
  }

  private removeProcessed(id: number): void {
    this.listings = this.listings.filter(listing => listing.id !== id);
    delete this.rejectionReasons[id];
    this.processingId = null;
    this.errorMessage = '';
  }
}
