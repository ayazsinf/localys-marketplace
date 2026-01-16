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
    ProductDetailComponent
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
    TranslateModule
  ]
})
export class AppModule {}
