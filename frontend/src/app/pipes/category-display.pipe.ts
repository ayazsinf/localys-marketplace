import { Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

const CATEGORY_KEYS: Record<string, string> = {
  'Electronics': 'CATEGORIES.NAMES.ELECTRONICS',
  'Home & Living': 'CATEGORIES.NAMES.HOME_LIVING',
  'Fashion': 'CATEGORIES.NAMES.FASHION',
  'Vehicles': 'CATEGORIES.NAMES.VEHICLES',
  'Sports & Outdoors': 'CATEGORIES.NAMES.SPORTS_OUTDOORS',
  'Laptops': 'CATEGORIES.NAMES.LAPTOPS',
  'Desktops': 'CATEGORIES.NAMES.DESKTOPS',
  'Gaming Consoles': 'CATEGORIES.NAMES.GAMING_CONSOLES',
  'Phones': 'CATEGORIES.NAMES.PHONES',
  'Tablets': 'CATEGORIES.NAMES.TABLETS',
  'Furniture': 'CATEGORIES.NAMES.FURNITURE',
  'Appliances': 'CATEGORIES.NAMES.APPLIANCES',
  'Decor': 'CATEGORIES.NAMES.DECOR',
  'Women': 'CATEGORIES.NAMES.WOMEN',
  'Men': 'CATEGORIES.NAMES.MEN',
  'Kids': 'CATEGORIES.NAMES.KIDS',
  'Accessories': 'CATEGORIES.NAMES.ACCESSORIES',
  'Cars': 'CATEGORIES.NAMES.CARS',
  'Motorcycles': 'CATEGORIES.NAMES.MOTORCYCLES',
  'Bicycles': 'CATEGORIES.NAMES.BICYCLES',
  'Fitness': 'CATEGORIES.NAMES.FITNESS',
  'Camping': 'CATEGORIES.NAMES.CAMPING',
  'Cycling': 'CATEGORIES.NAMES.CYCLING'
};

@Pipe({
  name: 'categoryDisplay',
  standalone: false,
  pure: false
})
export class CategoryDisplayPipe implements PipeTransform {
  constructor(private translateService: TranslateService) {}

  transform(categoryName: string | null | undefined): string {
    if (!categoryName) {
      return '';
    }
    const key = CATEGORY_KEYS[categoryName];
    return key ? this.translateService.instant(key) : categoryName;
  }
}
