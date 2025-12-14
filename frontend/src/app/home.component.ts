// import { Component, inject } from '@angular/core';
// import { HttpClient } from '@angular/common/http';
// import { TranslateModule } from '@ngx-translate/core';
// import { keycloak, hasRole } from './keycloak.service';
// import { NgIf } from '@angular/common';
// import {environment} from "../environments/environment";
//
// @Component({
//   standalone: true,
//   selector: 'app-home',
//   imports: [TranslateModule, NgIf],
//   template: `
//   <div style="max-width:700px;margin:30px auto; font-family: system-ui;">
//     <p *ngIf="!isLoggedIn()">{{ 'NOT_LOGGED' | translate }}</p>
//
//     <div style="display:flex; gap:10px; flex-wrap:wrap;">
//       <button (click)="callSecured()" [disabled]="!isLoggedIn()">
//         {{ 'CALL_SECURED' | translate }}
//       </button>
//
//       <button *ngIf="isAdmin()" (click)="callAdmin()">
//         {{ 'CALL_ADMIN' | translate }}
//       </button>
//     </div>
//
//     <h3>{{ 'SECURED_RESULT' | translate }}</h3>
//     <pre>{{ securedMsg }}</pre>
//
//     <h3 *ngIf="isAdmin()">{{ 'ADMIN_RESULT' | translate }}</h3>
//     <pre *ngIf="isAdmin()">{{ adminMsg }}</pre>
//   </div>
//   `
// })
// export class HomeComponent {
//   private http = inject(HttpClient);
//   securedMsg = '';
//   adminMsg = '';
//
//   isLoggedIn() { return true; }
//   isAdmin() { return true; }
//
//   callSecured(){
//     this.http.get(`${environment.apiUrl}/api/hello`, { responseType: 'text' })
//         .subscribe(txt => this.securedMsg = txt);
//   }
//
//   callAdmin(){
//     this.http.get(`${environment.apiUrl}/api/admin/hello`, { responseType: 'text' })
//         .subscribe(txt => this.adminMsg = txt);
//   }
// }
