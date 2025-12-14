import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {NgModule} from '@angular/core';
import {HomeComponent} from './pages/home/home.component';
import { ContactComponent } from './pages/contact/contact.component';
import { ProductsComponent } from './pages/products/products.component';
import { CategoriesComponent } from './pages/categories/categories.component';
import {CommonModule, NgOptimizedImage} from '@angular/common';
import {RouterModule} from '@angular/router';
import {LoginComponent} from './login/login.component';
import {AboutComponent} from "./pages/about/about.component";
import {NavbarComponent} from "./components/navbar/navbar.component";
import {SidebarComponent} from "./components/sidebar/sidebar.component";
import {ProductCardComponent} from "./components/product-card/product-card.component";
import {MatDialogModule} from "@angular/material/dialog";
import {MatFormFieldModule} from "@angular/material/form-field";
import {MatInputModule} from "@angular/material/input";
import {MatButtonModule} from "@angular/material/button";
import {RegisterComponent} from "./register-user/register";
import {ProductQuickViewComponent} from "./components/product-quick-view/product-quick-view.component";

@NgModule({
  declarations: [
    NavbarComponent,
    LoginComponent,
    RegisterComponent,
    SidebarComponent,
    ProductCardComponent,
    HomeComponent,
    AboutComponent,
    ContactComponent,
    ProductsComponent,
    CategoriesComponent,
    ProductQuickViewComponent

  ],
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    NgOptimizedImage,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    ReactiveFormsModule,

  ],
  exports: [
    NavbarComponent,
    LoginComponent,
    RegisterComponent,
    SidebarComponent,
    ProductCardComponent,
    HomeComponent,
    AboutComponent,
    ContactComponent,
    ProductsComponent,
    CategoriesComponent,
    ProductQuickViewComponent
  ]
})
export class AppModule { }
