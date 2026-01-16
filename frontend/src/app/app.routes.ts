import { Routes } from '@angular/router';
import {AboutComponent} from "./pages/about/about.component";
import {ContactComponent} from "./pages/contact/contact.component";
import {CategoriesComponent} from "./pages/categories/categories.component";
import {HomeComponent} from "./pages/home/home.component";
import {ProfileComponent} from "./pages/profile/profile.component";
import {ListingsComponent} from "./pages/listings/listings.component";
import {FavoritesComponent} from "./pages/favorites/favorites.component";
import {MessagesComponent} from "./pages/messages/messages.component";
import {authGuard} from "./guard/AuthGuard";
import {ProductDetailComponent} from "./pages/product-detail/product-detail.component";

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'about', component: AboutComponent },
  { path: 'contact', component: ContactComponent },
  { path: 'categories', component: CategoriesComponent },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
  { path: 'listings', component: ListingsComponent, canActivate: [authGuard] },
  { path: 'favorites', component: FavoritesComponent, canActivate: [authGuard] },
  { path: 'messages', component: MessagesComponent, canActivate: [authGuard] },
  { path: 'products/:id', component: ProductDetailComponent },
  { path: '**', redirectTo: '' }
];
