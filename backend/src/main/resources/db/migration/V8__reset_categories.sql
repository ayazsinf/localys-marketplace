-- Reset categories to main + sub categories

-- Detach existing products from categories to avoid FK issues
UPDATE products
SET category_id = NULL
WHERE category_id IS NOT NULL;

-- Remove existing categories (children first via cascade by FK)
DELETE FROM categories;

-- Root categories
INSERT INTO categories (name, parent_id, created_at)
VALUES
  ('Electronics', NULL, NOW()),
  ('Home & Living', NULL, NOW()),
  ('Fashion', NULL, NOW()),
  ('Vehicles', NULL, NOW()),
  ('Sports & Outdoors', NULL, NOW())
ON CONFLICT DO NOTHING;

-- Electronics subcategories
INSERT INTO categories (name, parent_id, created_at)
VALUES
  ('Laptops', (SELECT id FROM categories WHERE name = 'Electronics' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Desktops', (SELECT id FROM categories WHERE name = 'Electronics' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Gaming Consoles', (SELECT id FROM categories WHERE name = 'Electronics' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Phones', (SELECT id FROM categories WHERE name = 'Electronics' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Tablets', (SELECT id FROM categories WHERE name = 'Electronics' AND parent_id IS NULL LIMIT 1), NOW())
ON CONFLICT DO NOTHING;

-- Home & Living subcategories
INSERT INTO categories (name, parent_id, created_at)
VALUES
  ('Furniture', (SELECT id FROM categories WHERE name = 'Home & Living' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Appliances', (SELECT id FROM categories WHERE name = 'Home & Living' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Decor', (SELECT id FROM categories WHERE name = 'Home & Living' AND parent_id IS NULL LIMIT 1), NOW())
ON CONFLICT DO NOTHING;

-- Fashion subcategories
INSERT INTO categories (name, parent_id, created_at)
VALUES
  ('Women', (SELECT id FROM categories WHERE name = 'Fashion' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Men', (SELECT id FROM categories WHERE name = 'Fashion' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Kids', (SELECT id FROM categories WHERE name = 'Fashion' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Accessories', (SELECT id FROM categories WHERE name = 'Fashion' AND parent_id IS NULL LIMIT 1), NOW())
ON CONFLICT DO NOTHING;

-- Vehicles subcategories
INSERT INTO categories (name, parent_id, created_at)
VALUES
  ('Cars', (SELECT id FROM categories WHERE name = 'Vehicles' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Motorcycles', (SELECT id FROM categories WHERE name = 'Vehicles' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Bicycles', (SELECT id FROM categories WHERE name = 'Vehicles' AND parent_id IS NULL LIMIT 1), NOW())
ON CONFLICT DO NOTHING;

-- Sports & Outdoors subcategories
INSERT INTO categories (name, parent_id, created_at)
VALUES
  ('Fitness', (SELECT id FROM categories WHERE name = 'Sports & Outdoors' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Camping', (SELECT id FROM categories WHERE name = 'Sports & Outdoors' AND parent_id IS NULL LIMIT 1), NOW()),
  ('Cycling', (SELECT id FROM categories WHERE name = 'Sports & Outdoors' AND parent_id IS NULL LIMIT 1), NOW())
ON CONFLICT DO NOTHING;
