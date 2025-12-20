export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  currency: string;
  imageUrls: string[];
  categoryName: string;
  inStock: boolean;

  // UI-only opsiyonel:
  rating?: number;
}
