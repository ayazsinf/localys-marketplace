import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { FavoritesService } from '../../service/favorites.service';
import { Product } from '../../modules/product.model';

@Component({
  selector: 'app-favorites',
  templateUrl: './favorites.component.html',
  styleUrl: './favorites.component.scss',
  standalone: false
})
export class FavoritesComponent implements OnInit, OnDestroy {
  favorites: Product[] = [];
  isLoading = false;
  errorMessage = '';
  private subscriptions = new Subscription();
  private favoritesLoadSub?: Subscription;

  constructor(
    private favoritesService: FavoritesService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.refreshFavorites();

    const favoritesSub = this.favoritesService.favoriteIds$.subscribe(() => {
      this.refreshFavorites();
    });
    this.subscriptions.add(favoritesSub);
    this.favoritesService.ensureFavoriteIds();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    if (this.favoritesLoadSub) {
      this.favoritesLoadSub.unsubscribe();
    }
  }

  private refreshFavorites(): void {
    this.isLoading = true;
    if (this.favoritesLoadSub) {
      this.favoritesLoadSub.unsubscribe();
    }
    this.favoritesLoadSub = this.favoritesService.loadFavorites().subscribe({
      next: products => {
        this.favorites = products;
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.isLoading = false;
        this.errorMessage = 'FAVORITES.ERROR_LOAD';
        this.cdr.detectChanges();
      }
    });
  }
}
