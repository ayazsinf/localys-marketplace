-- Restore the catalog from the backup snapshot so the app shows the full product set.
TRUNCATE TABLE favorites RESTART IDENTITY CASCADE;
TRUNCATE TABLE product_images RESTART IDENTITY CASCADE;
TRUNCATE TABLE products RESTART IDENTITY CASCADE;

INSERT INTO products (vendor_id, category_id, sku, name, description, brand, price, currency, stock_qty, active, created_at, updated_at, location_text, latitude, longitude)
VALUES
    ((SELECT id FROM vendors WHERE shop_name = 'Vendor One Shop' LIMIT 1),
     (SELECT id FROM categories WHERE name = 'Laptops' LIMIT 1),
     'LCY-4E752FE9', 'MacbookAir', 'very good condition', 'Apple', 100.00, 'EUR', 1, TRUE,
     '2026-01-16 16:46:22.205477+01', '2026-01-16 22:17:59.006122+01', 'Saint-Lys', 43.512677, 1.19304),
    ((SELECT id FROM vendors WHERE shop_name = 'Vendor Two Atelier' LIMIT 1),
     (SELECT id FROM categories WHERE name = 'Desktops' LIMIT 1),
     'LCY-3FE67FD3', 'Mini Pc Gamer', 'gaming mini pc', 'ASUS', 450.00, 'EUR', 1, TRUE,
     '2026-01-16 16:48:00.491088+01', '2026-01-16 23:08:20.863363+01', 'Saint-Lys', 43.512677, 1.19304),
    ((SELECT id FROM vendors WHERE shop_name = 'Localys Shop' LIMIT 1),
     (SELECT id FROM categories WHERE name = 'Accessories' LIMIT 1),
     'LCY-AC3F4850', 'Keyboard Set', '', 'HP', 50.00, 'EUR', 1, TRUE,
     '2026-01-16 16:48:57.455521+01', '2026-01-16 16:48:57.455521+01', NULL, NULL, NULL),
    ((SELECT id FROM vendors WHERE shop_name = 'Vendor One Shop' LIMIT 1),
     (SELECT id FROM categories WHERE name = 'Cars' LIMIT 1),
     'LCY-0A574ED4', 'Peugeot 2008', '2014 Peugeot 2008 172000km', 'Peugeot', 5300.00, 'EUR', 1, TRUE,
     '2026-01-16 16:51:01.766818+01', '2026-01-19 19:37:30.920001+01', 'rue du 1er janvier 1949 31470 Saint-Lys', 43.523074, 1.188867),
    ((SELECT id FROM vendors WHERE shop_name = 'Vendor Two Atelier' LIMIT 1),
     (SELECT id FROM categories WHERE name = 'Cars' LIMIT 1),
     'LCY-990E131C', 'Peugeot Ion', 'Peugeot Ion Elektrik', 'Peugeot', 3900.00, 'EUR', 1, TRUE,
     '2026-01-16 16:53:21.586261+01', '2026-01-16 16:53:21.586261+01', NULL, NULL, NULL),
    ((SELECT id FROM vendors WHERE shop_name = 'Vendor One Shop' LIMIT 1),
     (SELECT id FROM categories WHERE name = 'Desktops' LIMIT 1),
     'LCY-DFA1E5A3', 'Imac', 'Apple IMac 21 inches', 'Apple', 140.00, 'EUR', 1, TRUE,
     '2026-01-16 16:54:17.109468+01', '2026-01-16 16:54:17.109468+01', NULL, NULL, NULL),
    ((SELECT id FROM vendors WHERE shop_name = 'Vendor Two Atelier' LIMIT 1),
     (SELECT id FROM categories WHERE name = 'Phones' LIMIT 1),
     'LCY-FB933886', 'IPhone SE', 'Apple Iphone SE 2024', 'Apple', 190.00, 'EUR', 3, TRUE,
     '2026-01-16 16:55:17.347271+01', '2026-01-17 01:01:05.040625+01', NULL, NULL, NULL),
    ((SELECT id FROM vendors WHERE shop_name = 'Localys Shop' LIMIT 1),
     (SELECT id FROM categories WHERE name = 'Accessories' LIMIT 1),
     'LCY-1977BFF8', 'earpod', 'earpod 4', 'apple', 120.00, 'EUR', 1, TRUE,
     '2026-01-16 20:41:20.192747+01', '2026-01-16 20:41:20.192747+01', NULL, NULL, NULL),
    ((SELECT id FROM vendors WHERE shop_name = 'Vendor One Shop' LIMIT 1),
     (SELECT id FROM categories WHERE name = 'Bicycles' LIMIT 1),
     'velo', 'velo btwin', 'very good condition', 'BTwin', 70.00, 'EUR', 1, TRUE,
     '2026-01-19 19:32:09.733726+01', '2026-01-19 19:32:09.733726+01', 'Saint-Lys', 43.512677, 1.19304);

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/1/9cebe99200614c2a993f85ba58ae1b43.png', 0
FROM products p
WHERE p.sku = 'LCY-4E752FE9';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/2/a968067990a6485d81ceb374a789285f.png', 0
FROM products p
WHERE p.sku = 'LCY-3FE67FD3';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/3/bd6abfdd691f4af78dc970a1ba74a1e7.png', 0
FROM products p
WHERE p.sku = 'LCY-AC3F4850';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/4/103e2411832f44c79097bc98bad6bf1a.jpg', 0
FROM products p
WHERE p.sku = 'LCY-0A574ED4';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/5/141c00eb374747da90cff623d9cf237a.jpg', 0
FROM products p
WHERE p.sku = 'LCY-990E131C';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/6/6bb50dcd434b4ad3b0ed77130a6596e4.png', 0
FROM products p
WHERE p.sku = 'LCY-DFA1E5A3';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/7/9169a8654f3542dea1e2450222af0a00.png', 0
FROM products p
WHERE p.sku = 'LCY-FB933886';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/8/2c826e8a94174ccdb40287b4d0e29877.png', 0
FROM products p
WHERE p.sku = 'LCY-1977BFF8';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/8/06fb668961564a71a247ca678333a714.png', 1
FROM products p
WHERE p.sku = 'LCY-1977BFF8';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/8/b6d5389f3d504ef99228a5f17fbcad52.png', 2
FROM products p
WHERE p.sku = 'LCY-1977BFF8';

INSERT INTO product_images (product_id, url, sort_order)
SELECT p.id, '/uploads/listings/9/a0521ccc0c344ad6a4292a7c4774a895.png', 0
FROM products p
WHERE p.sku = 'velo';