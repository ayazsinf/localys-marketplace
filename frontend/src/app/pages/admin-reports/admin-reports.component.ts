import { Component, OnInit } from '@angular/core';
import { AdminReport, ReportService, ReportStatus } from '../../service/report.service';

@Component({
  selector: 'app-admin-reports',
  templateUrl: './admin-reports.component.html',
  styleUrl: '../admin-listings/admin-listings.component.scss',
  standalone: false
})
export class AdminReportsComponent implements OnInit {
  reports: AdminReport[] = [];
  selectedStatus: ReportStatus | 'ALL' = 'OPEN';
  statusOptions: Array<ReportStatus | 'ALL'> = ['OPEN', 'DISMISSED', 'ACTIONED', 'ALL'];
  isLoading = false;
  processingId: number | null = null;
  errorMessage = '';

  constructor(private reportService: ReportService) {}

  ngOnInit(): void {
    this.loadReports();
  }

  loadReports(): void {
    this.isLoading = true;
    this.reportService.list(this.selectedStatus).subscribe({
      next: reports => {
        this.reports = reports;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'ADMIN_REPORTS.ERROR_LOAD';
        this.isLoading = false;
      }
    });
  }

  onStatusChange(status: ReportStatus | 'ALL'): void {
    this.selectedStatus = status;
    this.errorMessage = '';
    this.loadReports();
  }

  dismiss(report: AdminReport): void {
    this.processingId = report.id;
    this.reportService.dismiss(report.id).subscribe({
      next: () => this.afterAction(),
      error: () => this.actionFailed()
    });
  }

  removeListing(report: AdminReport): void {
    this.processingId = report.id;
    this.reportService.removeListing(report.id).subscribe({
      next: () => this.afterAction(),
      error: () => this.actionFailed()
    });
  }

  /** Removing a listing closes its other reports too, so reload instead of patching the list. */
  private afterAction(): void {
    this.processingId = null;
    this.errorMessage = '';
    this.loadReports();
  }

  private actionFailed(): void {
    this.processingId = null;
    this.errorMessage = 'ADMIN_REPORTS.ERROR_ACTION';
    this.loadReports();
  }
}
