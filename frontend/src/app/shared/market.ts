export interface MarketOption {
  country: string;
  currency: string;
  labelKey: string;
}

export const MARKET_OPTIONS: MarketOption[] = [
  { country: 'FR', currency: 'EUR', labelKey: 'COUNTRIES.FR' },
  { country: 'DE', currency: 'EUR', labelKey: 'COUNTRIES.DE' },
  { country: 'ES', currency: 'EUR', labelKey: 'COUNTRIES.ES' },
  { country: 'IT', currency: 'EUR', labelKey: 'COUNTRIES.IT' },
  { country: 'BE', currency: 'EUR', labelKey: 'COUNTRIES.BE' },
  { country: 'NL', currency: 'EUR', labelKey: 'COUNTRIES.NL' },
  { country: 'PT', currency: 'EUR', labelKey: 'COUNTRIES.PT' },
  { country: 'IE', currency: 'EUR', labelKey: 'COUNTRIES.IE' },
  { country: 'AT', currency: 'EUR', labelKey: 'COUNTRIES.AT' },
  { country: 'TR', currency: 'TRY', labelKey: 'COUNTRIES.TR' },
  { country: 'GB', currency: 'GBP', labelKey: 'COUNTRIES.GB' },
  { country: 'US', currency: 'USD', labelKey: 'COUNTRIES.US' },
  { country: 'CA', currency: 'CAD', labelKey: 'COUNTRIES.CA' },
  { country: 'CH', currency: 'CHF', labelKey: 'COUNTRIES.CH' }
];

const COUNTRY_TO_CURRENCY = new Map(MARKET_OPTIONS.map(option => [option.country, option.currency]));

export function getCurrencyForCountry(country?: string | null): string {
  const normalizedCountry = (country ?? '').trim().toUpperCase();
  return COUNTRY_TO_CURRENCY.get(normalizedCountry) ?? 'EUR';
}

export function getDefaultCountryForLanguage(language?: string | null): string {
  switch ((language ?? '').trim().toLowerCase()) {
    case 'tr':
      return 'TR';
    case 'en':
      return 'US';
    case 'fr':
    default:
      return 'FR';
  }
}

export function getLocaleForLanguage(language?: string | null): string {
  switch ((language ?? '').trim().toLowerCase()) {
    case 'tr':
      return 'tr-TR';
    case 'fr':
      return 'fr-FR';
    case 'en':
    default:
      return 'en-US';
  }
}
