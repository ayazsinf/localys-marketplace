import { Component } from '@angular/core';
import {MatDialog} from "@angular/material/dialog";
import {LoginComponent} from "../../login/login.component";
import {RegisterComponent} from "../../register-user/register";
import {AuthService} from "../../service/auth.service";
import {CartService} from "../../service/cart.service";
import {SearchService} from "../../service/search.service";

@Component({
  selector: 'app-navbar',
  standalone: false,
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss',
})
export class NavbarComponent   {
  isMenuOpen = false;
  searchTerm = '';
  cartCount$ = this.cartService.count$;

  constructor(
      public authService: AuthService,
      private cartService: CartService,
      private dialog: MatDialog,
      private searchService: SearchService
  ) {}

  openLoginDialog() {
    this.dialog.open(LoginComponent, {
      width: '400px',
      disableClose: true
    });
  }

  openRegisterDialog() {
    this.dialog.open(RegisterComponent, {
      width: '400px',
      disableClose: true
    });
  }


  toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
    console.log('Menu toggled:', this.isMenuOpen); // Debug için
  }

  openCart(): void {
    console.log('Cart tıklandı – burada sepet dialogu veya sayfası açılacak.');
    // İleride: this.router.navigate(['/cart']); veya MatDialog ile cart açarsın
  }
  onSearch(): void {
    const term = this.searchTerm.trim();
    this.searchService.setSearchTerm(term);
    // istersen boşsa filtreyi resetlemiş olursun
  }
}
