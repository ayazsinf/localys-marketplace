import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../service/auth.service';
import { UserProfile, UserService } from '../../service/user.service';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
  standalone: false
})
export class ProfileComponent implements OnInit {
  displayName = 'Localys User';
  email = 'you@example.com';
  phone = '';
  shippingAddress = 'Karakoy, Istanbul';
  billingAddress = '';
  language = 'Turkish';
  currency = 'TRY';
  notificationsEnabled = true;
  passwordUpdated = '2 months ago';
  memberSince = 'Jan 2024';
  lastLogin = 'Today 10:45';
  twoFactorEnabled = false;

  profile: UserProfile | null = null;
  isEditingAll = false;
  isEditingBasic = false;
  isEditingPreferences = false;
  isEditingAddressBook = false;
  isSavingProfile = false;
  isSavingAddress = false;
  needsProfileCompletion = false;
  isAddingAddress = false;
  editingAddressId: number | null = null;
  basicForm = {
    displayName: '',
    email: '',
    phone: ''
  };
  preferencesForm = {
    language: '',
    currency: '',
    notificationsEnabled: true
  };
  addressForm = {
    type: 'SHIPPING',
    label: '',
    fullName: '',
    phone: '',
    line1: '',
    line2: '',
    city: '',
    postalCode: '',
    country: 'TR',
    defaultShipping: true,
    defaultBilling: false
  };

  constructor(
    public authService: AuthService,
    private userService: UserService
  ) {}

  ngOnInit(): void {
    this.userService.getMe().subscribe({
      next: (profile) => {
        this.profile = profile;
        this.displayName = profile.displayName || this.displayName;
        this.email = profile.email || this.email;
        this.phone = profile.phone || '';
        this.basicForm.displayName = this.displayName;
        this.basicForm.email = this.email;
        this.basicForm.phone = this.phone;

        const shipping = profile.addresses.find((address) => address.defaultShipping);
        const billing = profile.addresses.find((address) => address.defaultBilling);
        this.shippingAddress = shipping?.line1 || this.shippingAddress;
        this.billingAddress = billing?.line1 || this.billingAddress;

        if (profile.createdAt) {
          this.memberSince = this.formatMonthYear(profile.createdAt);
        }
        this.preferencesForm.language = this.language;
        this.preferencesForm.currency = this.currency;
        this.preferencesForm.notificationsEnabled = this.notificationsEnabled;
        this.needsProfileCompletion = this.isProfileIncomplete(profile);
      },
      error: () => {
        // Profile is optional; keep placeholders for unauthenticated state.
      }
    });
  }

  get username(): string {
    return this.authService.username ?? 'localys-user';
  }

  get initials(): string {
    const source = (this.displayName || this.username).trim();
    if (!source) {
      return 'LU';
    }
    const parts = source.split(/\s+/).slice(0, 2);
    return parts.map((part) => part.charAt(0).toUpperCase()).join('');
  }

  private formatMonthYear(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return this.memberSince;
    }
    return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  }

  startEditingBasic(): void {
    this.isEditingBasic = true;
    this.basicForm.displayName = this.displayName;
    this.basicForm.email = this.email;
    this.basicForm.phone = this.phone;
  }

  cancelEditingBasic(): void {
    this.isEditingBasic = false;
  }

  startEditingAll(): void {
    this.isEditingAll = true;
    this.startEditingBasic();
    this.startEditingPreferences();
    this.isEditingAddressBook = true;
  }

  stopEditingAll(): void {
    this.isEditingAll = false;
    this.isEditingBasic = false;
    this.isEditingPreferences = false;
    this.isEditingAddressBook = false;
    this.cancelAddressEdit();
    this.isAddingAddress = false;
  }

  saveBasicProfile(): void {
    if (this.isSavingProfile) {
      return;
    }
    this.isSavingProfile = true;
    this.userService.updateMe({
      displayName: this.basicForm.displayName,
      email: this.basicForm.email,
      phone: this.basicForm.phone
    }).subscribe({
      next: (profile) => {
        this.profile = profile;
        this.displayName = profile.displayName || this.displayName;
        this.email = profile.email || this.email;
        this.phone = profile.phone || '';
        this.isEditingBasic = false;
        this.needsProfileCompletion = this.isProfileIncomplete(profile);
      },
      error: () => {
        this.isSavingProfile = false;
      },
      complete: () => {
        this.isSavingProfile = false;
      }
    });
  }

  startEditingPreferences(): void {
    this.isEditingPreferences = true;
    this.preferencesForm.language = this.language;
    this.preferencesForm.currency = this.currency;
    this.preferencesForm.notificationsEnabled = this.notificationsEnabled;
  }

  cancelEditingPreferences(): void {
    this.isEditingPreferences = false;
  }

  savePreferences(): void {
    this.language = this.preferencesForm.language;
    this.currency = this.preferencesForm.currency;
    this.notificationsEnabled = this.preferencesForm.notificationsEnabled;
    this.isEditingPreferences = false;
  }

  startAddAddress(): void {
    this.isAddingAddress = true;
    this.editingAddressId = null;
    this.resetAddressForm();
  }

  startEditAddress(address: UserProfile['addresses'][number]): void {
    this.isAddingAddress = false;
    this.editingAddressId = address.id;
    this.addressForm = {
      type: address.type || 'SHIPPING',
      label: address.label || '',
      fullName: address.fullName || '',
      phone: address.phone || '',
      line1: address.line1 || '',
      line2: address.line2 || '',
      city: address.city || '',
      postalCode: address.postalCode || '',
      country: address.country || 'TR',
      defaultShipping: address.defaultShipping,
      defaultBilling: address.defaultBilling
    };
  }

  cancelAddressEdit(): void {
    this.isAddingAddress = false;
    this.editingAddressId = null;
  }

  saveAddress(): void {
    if (this.isSavingAddress) {
      return;
    }
    this.isSavingAddress = true;
    const request$ = this.editingAddressId
      ? this.userService.updateAddress(this.editingAddressId, { ...this.addressForm })
      : this.userService.addAddress({ ...this.addressForm });

    request$.subscribe({
      next: (address) => {
        const profile = this.profile;
        if (profile) {
          if (this.editingAddressId) {
            profile.addresses = profile.addresses.map((item) => item.id === address.id ? address : item);
          } else {
            profile.addresses = [...profile.addresses, address];
          }
          this.needsProfileCompletion = this.isProfileIncomplete(profile);
        }
        if (address.defaultShipping && address.line1) {
          this.shippingAddress = address.line1;
        }
        if (address.defaultBilling && address.line1) {
          this.billingAddress = address.line1;
        }
        this.cancelAddressEdit();
        this.resetAddressForm();
      },
      error: () => {
        this.isSavingAddress = false;
      },
      complete: () => {
        this.isSavingAddress = false;
      }
    });
  }

  removeAddress(id: number): void {
    this.userService.deleteAddress(id).subscribe({
      next: () => {
        if (this.profile) {
          this.profile.addresses = this.profile.addresses.filter((address) => address.id !== id);
          this.needsProfileCompletion = this.isProfileIncomplete(this.profile);
        }
      }
    });
  }

  private isProfileIncomplete(profile: UserProfile): boolean {
    const missingBasic = !profile.displayName || !profile.phone;
    const missingAddress = !profile.addresses || profile.addresses.length === 0;
    return missingBasic || missingAddress;
  }

  private resetAddressForm(): void {
    this.addressForm = {
      type: 'SHIPPING',
      label: '',
      fullName: '',
      phone: '',
      line1: '',
      line2: '',
      city: '',
      postalCode: '',
      country: 'TR',
      defaultShipping: true,
      defaultBilling: false
    };
  }
}
