import { Component, OnInit } from '@angular/core';
import { Dispute, DisputeService } from '../../service/dispute.service';

/** Admins settle disputes: refund the buyer or pay the seller. */
@Component({
  selector: 'app-admin-disputes',
  templateUrl: './admin-disputes.component.html',
  styleUrl: '../admin-listings/admin-listings.component.scss',
  standalone: false
})
export class AdminDisputesComponent implements OnInit {
  disputes: Dispute[] = [];
  selectedStatus: 'ACTIVE' | 'ALL' = 'ACTIVE';
  isLoading = false;
  processingId: number | null = null;
  errorMessage = '';
  notes: Record<number, string> = {};

  constructor(private disputeService: DisputeService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.disputeService.adminList(this.selectedStatus).subscribe({
      next: disputes => {
        this.disputes = disputes;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'ADMIN_DISPUTES.ERROR_LOAD';
        this.isLoading = false;
      }
    });
  }

  onStatusChange(status: 'ACTIVE' | 'ALL'): void {
    this.selectedStatus = status;
    this.errorMessage = '';
    this.load();
  }

  resolve(dispute: Dispute, outcome: 'REFUND' | 'RELEASE'): void {
    this.processingId = dispute.id;
    this.disputeService.resolve(dispute.id, outcome, (this.notes[dispute.id] ?? '').trim()).subscribe({
      next: () => {
        this.processingId = null;
        this.errorMessage = '';
        this.load();
      },
      error: () => {
        this.processingId = null;
        this.errorMessage = 'ADMIN_DISPUTES.ERROR_ACTION';
      }
    });
  }
}
