import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NgModule } from '@angular/core';
import { HomeComponent } from './pages/home/home.component';
import { ContactComponent } from './pages/contact/contact.component';
import { CategoriesComponent } from './pages/categories/categories.component';
import { CommonModule, NgOptimizedImage } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AboutComponent } from './pages/about/about.component';
import { NavbarComponent } from './components/navbar/navbar.component';
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { ProductCardComponent } from './components/product-card/product-card.component';
import { MatDialogModule } from '@angular/material/dialog';
import { ProductQuickViewComponent } from './components/product-quick-view/product-quick-view.component';
import { ProfileComponent } from './pages/profile/profile.component';
import { ListingsComponent } from './pages/listings/listings.component';
import { FavoritesComponent } from './pages/favorites/favorites.component';
import { MessagesComponent } from './pages/messages/messages.component';
import { ProductDetailComponent } from './pages/product-detail/product-detail.component';
import { TranslateModule } from '@ngx-translate/core';
import { CartComponent } from './pages/cart/cart.component';
import { CheckoutComponent } from './pages/checkout/checkout.component';
import { NotificationsComponent } from './pages/notifications/notifications.component';
import { CurrencyDisplayPipe } from './pipes/currency-display.pipe';
import { CategoryDisplayPipe } from './pipes/category-display.pipe';
import { AdminListingsComponent } from './pages/admin-listings/admin-listings.component';
import { AuthDialogComponent } from './components/auth-dialog/auth-dialog.component';

@NgModule({
  declarations: [
    NavbarComponent,
    SidebarComponent,
    ProductCardComponent,
    HomeComponent,
    AboutComponent,
    ContactComponent,
    CategoriesComponent,
    ProductQuickViewComponent,
    ProfileComponent,
    ListingsComponent,
    FavoritesComponent,
    MessagesComponent,
    ProductDetailComponent,
    CartComponent,
    CheckoutComponent,
    NotificationsComponent,
    AdminListingsComponent,
    AuthDialogComponent,
    CurrencyDisplayPipe,
    CategoryDisplayPipe
  ],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    NgOptimizedImage,
    TranslateModule,
    MatDialogModule,
    ReactiveFormsModule
  ],
  exports: [
    NavbarComponent,
    SidebarComponent,
    ProductCardComponent,
    HomeComponent,
    AboutComponent,
    ContactComponent,
    CategoriesComponent,
    ProductQuickViewComponent,
    ProfileComponent,
    ListingsComponent,
    FavoritesComponent,
    MessagesComponent,
    ProductDetailComponent,
    CartComponent,
    CheckoutComponent,
    NotificationsComponent,
    AdminListingsComponent,
    CurrencyDisplayPipe,
    CategoryDisplayPipe,
    TranslateModule
  ]
})
export class AppModule {}
