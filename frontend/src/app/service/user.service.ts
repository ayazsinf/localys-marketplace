import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface UserAddress {
  id: number;
  type: string | null;
  label: string | null;
  fullName: string | null;
  phone: string | null;
  line1: string | null;
  line2: string | null;
  city: string | null;
  postalCode: string | null;
  country: string | null;
  defaultShipping: boolean;
  defaultBilling: boolean;
}

export interface UserProfile {
  id: number;
  username: string | null;
  email: string | null;
  displayName: string | null;
  phone: string | null;
  role: string | null;
  createdAt: string | null;
  updatedAt: string | null;
  addresses: UserAddress[];
}

export interface UserProfileUpdate {
  displayName: string;
  email: string;
  phone: string;
}

export interface AddressRequest {
  type: string;
  label: string;
  fullName: string;
  phone: string;
  line1: string;
  line2: string;
  city: string;
  postalCode: string;
  country: string;
  defaultShipping: boolean;
  defaultBilling: boolean;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  getMe(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${environment.apiUrl}/users/me`);
  }

  updateMe(payload: UserProfileUpdate): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${environment.apiUrl}/users/me`, payload);
  }

  addAddress(payload: AddressRequest): Observable<UserAddress> {
    return this.http.post<UserAddress>(`${environment.apiUrl}/users/me/addresses`, payload);
  }

  updateAddress(id: number, payload: AddressRequest): Observable<UserAddress> {
    return this.http.put<UserAddress>(`${environment.apiUrl}/users/me/addresses/${id}`, payload);
  }

  deleteAddress(id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/users/me/addresses/${id}`);
  }
}
