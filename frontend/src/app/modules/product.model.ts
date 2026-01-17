export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  currency: string;
  imageUrls: string[];
  categoryName: string;
  inStock: boolean;
  stockQty?: number;
  vendorUserId?: number | null;

  // UI-only opsiyonel:
  rating?: number;
}
