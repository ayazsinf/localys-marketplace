export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  country?: string;
  currency: string;
  imageUrls: string[];
  categoryName: string;
  inStock: boolean;
  stockQty?: number;
  vendorUserId?: number | null;
}
