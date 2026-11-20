-- Reference data: the 19 wireframe categories ("All" is a UI filter) and flat shipping rates per region.

INSERT INTO categories (name) VALUES
 ('Romance'), ('Mystery'), ('Science Fiction'), ('Fantasy'), ('Historical'), ('Biography'),
 ('Self-help'), ('Memoir'), ('Travel'), ('Cooking'), ('Children''s'), ('Young Adult'),
 ('Comics & Graphic Novels'), ('Poetry'), ('Drama'), ('Science'), ('Philosophy'), ('Religion'),
 ('Language Learning');

-- charge in INR; waived above app.shipping.free-threshold
INSERT INTO shipping_rates (region, charge, delivery_days) VALUES
 ('DEFAULT', 60.00, 5),
 ('Karnataka', 40.00, 2),
 ('Maharashtra', 40.00, 3),
 ('Delhi', 40.00, 3),
 ('Tamil Nadu', 50.00, 3);
