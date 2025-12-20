import {Component, OnInit} from '@angular/core';
import {Product} from '../../modules/product.model';
import {ProductService} from "../../service/product.service";
import {Subscription} from "rxjs";
import {SearchService} from "../../service/search.service";

type SortOption =
    | 'priceLowHigh'
    | 'priceHighLow'
    | 'ratingHighLow'
    | 'nameAZ';

@Component({
  selector: 'app-home',
  standalone: false,
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  filteredProducts: Product[] = [];
  allProducts: Product[] = [];
  sortOption: SortOption = 'priceLowHigh';
  selectedRating: number | null = null;
  selectedMaxPrice: number | null = null;
  searchTerm: string = '';
  private searchSub?: Subscription;

  constructor(private productService: ProductService,
              private searchService: SearchService) {}

  ngOnInit() {
    this.productService.getProductsAll().subscribe();
    this.allProducts = this.productService.products();
    if (this.allProducts.length > 0) {
      const max = Math.ceil(Math.max(...this.allProducts.map(p => p.price)));
      this.selectedMaxPrice = max;
    }

    // 🔍 Navbar’dan arama kelimesini dinle
    this.searchSub = this.searchService.searchTerm$.subscribe(term => {
      this.searchTerm = term.toLowerCase();
      this.applyFiltersAndSort();
    });

    this.filteredProducts = this.allProducts;
    this.applyFiltersAndSort();
  }

  onCategorySelected(category: string) {
    if (category === 'all') {
      this.filteredProducts = this.allProducts;
    } else {
      this.filteredProducts = this.productService.getProductsByCategory(category);
    }
  }

  onRatingSelected(rating: number) {
    if (rating === 5) {
      this.filteredProducts = this.allProducts;
    } else {
      this.filteredProducts = this.productService.getProductsByRating(rating);
    }
  }

  onPriceChange(maxPrice: number): void {
    this.selectedMaxPrice = maxPrice;
    this.applyFiltersAndSort();
  }



  onSortChange(option: SortOption): void {
    this.sortOption = option;
    this.applyFiltersAndSort();
  }

  private applyFiltersAndSort(): void {
    let result = [...this.allProducts];

    if (this.searchTerm) {
      const term = this.searchTerm;
      result = result.filter(p =>
          p.name.toLowerCase().includes(term) ||
          p.description?.toLowerCase().includes(term)
      );
      console.log("arama sonucu ",result)
    }

    if (this.selectedRating !== null) {
      // TODO: rate filter should be redo with signal and computed
      // result = result.filter(p => Math.floor(p.rating) >= this.selectedRating!);
    }

    const maxPrice = this.selectedMaxPrice;

    if (maxPrice != null) {
      result = result.filter(p => p.price <= maxPrice);
    }

    // 2) sort
    switch (this.sortOption) {
      case 'priceLowHigh':
        result.sort((a, b) => a.price - b.price);
        break;

      case 'priceHighLow':
        result.sort((a, b) => b.price - a.price);
        break;

        // case 'ratingHighLow':
        //   if(result)
        //   result.sort((a, b) => b?.rating - a.rating);
        //   break;

      case 'nameAZ':
        result.sort((a, b) => a.name.localeCompare(b.name));
        break;
    }

    this.filteredProducts = result;
  }
}
