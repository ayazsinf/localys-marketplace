import { ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { ReviewService } from '../../service/review.service';

/**
 * Star rating + optional comment for one delivered order item. Used by the buyer to rate the
 * seller and by the seller to rate the buyer; once rated it shows the given stars.
 */
@Component({
  selector: 'app-rating-form',
  templateUrl: './rating-form.component.html',
  styleUrl: './rating-form.component.scss',
  standalone: false
})
export class RatingFormComponent implements OnDestroy {
  @Input({ required: true }) orderItemId!: number;
  /** Rating already given by the viewer, if any. */
  @Input() rating: number | null = null;
  /** Translation key of the prompt, e.g. "Rate the seller". */
  @Input() labelKey = 'REVIEWS.RATE_SELLER';
  @Output() rated = new EventEmitter<number>();

  readonly ratingValues = [1, 2, 3, 4, 5];
  draftRating = 0;
  draftComment = '';
  isSubmitting = false;
  hasError = false;
  private subscription?: Subscription;

  constructor(private reviewService: ReviewService, private cdr: ChangeDetectorRef) {}

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(5 - rating);
  }

  submit(): void {
    if (!this.draftRating || this.isSubmitting) {
      return;
    }
    this.isSubmitting = true;
    this.hasError = false;
    this.subscription = this.reviewService
      .createReview(this.orderItemId, this.draftRating, this.draftComment.trim())
      .subscribe({
        next: review => this.finish(review.rating),
        error: err => {
          this.isSubmitting = false;
          if (err?.error?.code === 'ALREADY_REVIEWED') {
            // Rated from another tab; show it as done.
            this.finish(this.draftRating);
            return;
          }
          this.hasError = true;
          this.cdr.detectChanges();
        }
      });
  }

  private finish(rating: number): void {
    this.isSubmitting = false;
    this.rating = rating;
    this.rated.emit(rating);
    this.cdr.detectChanges();
  }
}
