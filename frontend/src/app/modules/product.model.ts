export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  country?: string;
  currency: string;
  imageUrls: string[];
  categoryId?: number | null;
  categoryName: string;
  categoryPathIds?: number[];
  categoryPathNames?: string[];
  inStock: boolean;
  stockQty?: number;
  vendorUserId?: number | null;
}
