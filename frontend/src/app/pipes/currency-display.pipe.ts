import { Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { getLocaleForLanguage } from '../shared/market';

@Pipe({
  name: 'currencyDisplay',
  standalone: false,
  pure: false
})
export class CurrencyDisplayPipe implements PipeTransform {
  constructor(private translateService: TranslateService) {}

  transform(value: number | null | undefined, currencyCode: string | null | undefined): string {
    if (value == null) {
      return '';
    }

    const resolvedCurrency = (currencyCode ?? 'EUR').trim().toUpperCase() || 'EUR';
    const locale = getLocaleForLanguage(this.translateService.currentLang || this.translateService.getDefaultLang());

    try {
      return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: resolvedCurrency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }).format(value);
    } catch {
      return `${value.toFixed(2)} ${resolvedCurrency}`;
    }
  }
}
