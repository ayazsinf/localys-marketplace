ALTER TABLE categories ADD COLUMN IF NOT EXISTS slug VARCHAR(140);
ALTER TABLE categories ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;

UPDATE categories
SET slug = lower(regexp_replace(regexp_replace(name, '&', 'and', 'g'), '[^a-zA-Z0-9]+', '-', 'g'))
WHERE slug IS NULL OR slug = '';

UPDATE categories
SET slug = trim(both '-' from slug)
WHERE slug LIKE '-%' OR slug LIKE '%-';

UPDATE categories
SET sort_order = 0
WHERE sort_order IS NULL;

ALTER TABLE categories ALTER COLUMN slug SET NOT NULL;
ALTER TABLE categories ALTER COLUMN sort_order SET NOT NULL;

UPDATE categories SET sort_order = 10, slug = 'electronics' WHERE parent_id IS NULL AND name = 'Electronics';
UPDATE categories SET sort_order = 20, slug = 'home-living' WHERE parent_id IS NULL AND name = 'Home & Living';
UPDATE categories SET sort_order = 30, slug = 'fashion' WHERE parent_id IS NULL AND name = 'Fashion';
UPDATE categories SET sort_order = 40, slug = 'vehicles' WHERE parent_id IS NULL AND name = 'Vehicles';
UPDATE categories SET sort_order = 50, slug = 'sports-outdoors' WHERE parent_id IS NULL AND name = 'Sports & Outdoors';

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT 'Electronics', 'electronics', NULL, 10, NOW()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE parent_id IS NULL AND slug = 'electronics');

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT 'Home & Living', 'home-living', NULL, 20, NOW()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE parent_id IS NULL AND slug = 'home-living');

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT 'Fashion', 'fashion', NULL, 30, NOW()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE parent_id IS NULL AND slug = 'fashion');

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT 'Vehicles', 'vehicles', NULL, 40, NOW()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE parent_id IS NULL AND slug = 'vehicles');

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT 'Sports & Outdoors', 'sports-outdoors', NULL, 50, NOW()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE parent_id IS NULL AND slug = 'sports-outdoors');

UPDATE categories SET sort_order = 10, slug = 'phones' WHERE name = 'Phones' AND parent_id = (SELECT id FROM categories WHERE slug = 'electronics' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 20, slug = 'tablets' WHERE name = 'Tablets' AND parent_id = (SELECT id FROM categories WHERE slug = 'electronics' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 30, slug = 'laptops' WHERE name = 'Laptops' AND parent_id = (SELECT id FROM categories WHERE slug = 'electronics' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 40, slug = 'desktops' WHERE name = 'Desktops' AND parent_id = (SELECT id FROM categories WHERE slug = 'electronics' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 50, slug = 'gaming-consoles' WHERE name = 'Gaming Consoles' AND parent_id = (SELECT id FROM categories WHERE slug = 'electronics' AND parent_id IS NULL LIMIT 1);

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT 'TVs', 'tvs', p.id, 60, NOW() FROM categories p
WHERE p.slug = 'electronics' AND p.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id = p.id AND c.slug = 'tvs');

UPDATE categories SET sort_order = 10, slug = 'furniture' WHERE name = 'Furniture' AND parent_id = (SELECT id FROM categories WHERE slug = 'home-living' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 20, slug = 'appliances' WHERE name = 'Appliances' AND parent_id = (SELECT id FROM categories WHERE slug = 'home-living' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 30, slug = 'decor' WHERE name = 'Decor' AND parent_id = (SELECT id FROM categories WHERE slug = 'home-living' AND parent_id IS NULL LIMIT 1);

UPDATE categories SET sort_order = 10, slug = 'women' WHERE name = 'Women' AND parent_id = (SELECT id FROM categories WHERE slug = 'fashion' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 20, slug = 'men' WHERE name = 'Men' AND parent_id = (SELECT id FROM categories WHERE slug = 'fashion' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 30, slug = 'kids' WHERE name = 'Kids' AND parent_id = (SELECT id FROM categories WHERE slug = 'fashion' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 40, slug = 'accessories' WHERE name = 'Accessories' AND parent_id = (SELECT id FROM categories WHERE slug = 'fashion' AND parent_id IS NULL LIMIT 1);

UPDATE categories SET sort_order = 10, slug = 'cars' WHERE name = 'Cars' AND parent_id = (SELECT id FROM categories WHERE slug = 'vehicles' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 20, slug = 'motorcycles' WHERE name = 'Motorcycles' AND parent_id = (SELECT id FROM categories WHERE slug = 'vehicles' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 30, slug = 'bicycles' WHERE name = 'Bicycles' AND parent_id = (SELECT id FROM categories WHERE slug = 'vehicles' AND parent_id IS NULL LIMIT 1);

UPDATE categories SET sort_order = 10, slug = 'fitness' WHERE name = 'Fitness' AND parent_id = (SELECT id FROM categories WHERE slug = 'sports-outdoors' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 20, slug = 'camping' WHERE name = 'Camping' AND parent_id = (SELECT id FROM categories WHERE slug = 'sports-outdoors' AND parent_id IS NULL LIMIT 1);
UPDATE categories SET sort_order = 30, slug = 'cycling' WHERE name = 'Cycling' AND parent_id = (SELECT id FROM categories WHERE slug = 'sports-outdoors' AND parent_id IS NULL LIMIT 1);

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT child.name, child.slug, p.id, child.sort_order, NOW()
FROM categories p
JOIN (VALUES
  ('Smartphones', 'smartphones', 10),
  ('Feature Phones', 'feature-phones', 20),
  ('Phone Accessories', 'phone-accessories', 30)
) AS child(name, slug, sort_order) ON TRUE
WHERE p.slug = 'phones'
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id = p.id AND c.slug = child.slug);

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT child.name, child.slug, p.id, child.sort_order, NOW()
FROM categories p
JOIN (VALUES
  ('Gaming Laptops', 'gaming-laptops', 10),
  ('Ultrabooks', 'ultrabooks', 20),
  ('Business Laptops', 'business-laptops', 30)
) AS child(name, slug, sort_order) ON TRUE
WHERE p.slug = 'laptops'
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id = p.id AND c.slug = child.slug);

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT child.name, child.slug, p.id, child.sort_order, NOW()
FROM categories p
JOIN (VALUES
  ('LED TVs', 'led-tvs', 10),
  ('OLED TVs', 'oled-tvs', 20),
  ('QLED TVs', 'qled-tvs', 30)
) AS child(name, slug, sort_order) ON TRUE
WHERE p.slug = 'tvs'
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id = p.id AND c.slug = child.slug);

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT child.name, child.slug, p.id, child.sort_order, NOW()
FROM categories p
JOIN (VALUES
  ('Sofas', 'sofas', 10),
  ('Tables', 'tables', 20),
  ('Beds', 'beds', 30)
) AS child(name, slug, sort_order) ON TRUE
WHERE p.slug = 'furniture'
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id = p.id AND c.slug = child.slug);

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT child.name, child.slug, p.id, child.sort_order, NOW()
FROM categories p
JOIN (VALUES
  ('Refrigerators', 'refrigerators', 10),
  ('Washing Machines', 'washing-machines', 20),
  ('Vacuum Cleaners', 'vacuum-cleaners', 30)
) AS child(name, slug, sort_order) ON TRUE
WHERE p.slug = 'appliances'
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id = p.id AND c.slug = child.slug);

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT child.name, child.slug, p.id, child.sort_order, NOW()
FROM categories p
JOIN (VALUES
  ('Clothing', 'clothing', 10),
  ('Shoes', 'shoes', 20),
  ('Bags', 'bags', 30)
) AS child(name, slug, sort_order) ON TRUE
WHERE p.slug IN ('women', 'men', 'kids')
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id = p.id AND c.slug = child.slug);

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT child.name, child.slug, p.id, child.sort_order, NOW()
FROM categories p
JOIN (VALUES
  ('Electric Cars', 'electric-cars', 10),
  ('Hybrid Cars', 'hybrid-cars', 20),
  ('SUVs', 'suvs', 30)
) AS child(name, slug, sort_order) ON TRUE
WHERE p.slug = 'cars'
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id = p.id AND c.slug = child.slug);

INSERT INTO categories (name, slug, parent_id, sort_order, created_at)
SELECT child.name, child.slug, p.id, child.sort_order, NOW()
FROM categories p
JOIN (VALUES
  ('Cardio Equipment', 'cardio-equipment', 10),
  ('Weights', 'weights', 20),
  ('Yoga & Pilates', 'yoga-pilates', 30)
) AS child(name, slug, sort_order) ON TRUE
WHERE p.slug = 'fitness'
  AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.parent_id = p.id AND c.slug = child.slug);
