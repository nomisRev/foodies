-- V3: Populate menu with Italian restaurant dishes
-- Date: 2026-01-18
-- Description: Add 24 authentic Italian menu items across 5 categories (Antipasti, Primi, Secondi, Dolci, Bevande)

-- Antipasti (Appetizers) - 5 items
INSERT INTO menu_items (name, description, image_url, price, stock) VALUES
('Bruschetta al Pomodoro',
 'Grilled rustic bread topped with fresh tomatoes, garlic, basil, and extra virgin olive oil. A classic Italian appetizer that celebrates simple, quality ingredients.',
 'https://images.unsplash.com/photo-1572695157366-5e585ab2b69f',
 8.50,
 30),

('Caprese Salad',
 'Fresh mozzarella di bufala, ripe tomatoes, and basil leaves drizzled with balsamic reduction and olive oil. A timeless celebration of Italian flavors.',
 'https://images.unsplash.com/photo-1608897013039-887f21d8c804',
 10.00,
 35),

('Arancini Siciliani',
 'Golden-fried Sicilian rice balls filled with ragù, peas, and mozzarella. Crispy on the outside, creamy on the inside.',
 'https://images.unsplash.com/photo-1591189863430-ab87e120f312',
 9.00,
 25),

('Prosciutto e Melone',
 'Thinly sliced Prosciutto di Parma wrapped around sweet cantaloupe melon. A perfect balance of savory and sweet.',
 'https://images.unsplash.com/photo-1599599810769-bcde5a160d32',
 12.00,
 20),

('Calamari Fritti',
 'Tender calamari rings lightly breaded and fried until golden, served with lemon wedges and marinara sauce.',
 'https://images.unsplash.com/photo-1599487488170-d11ec9c172f0',
 11.50,
 30);

-- Primi Piatti (First Courses) - 6 items
INSERT INTO menu_items (name, description, image_url, price, stock) VALUES
('Spaghetti Carbonara',
 'Traditional Roman pasta with crispy guanciale, Pecorino Romano, farm-fresh eggs, and cracked black pepper. Creamy without cream, as it should be.',
 'https://images.unsplash.com/photo-1612874742237-6526221588e3',
 14.00,
 45),

('Fettuccine Alfredo',
 'Silky fettuccine tossed in a rich sauce of butter and Parmigiano-Reggiano. Simple elegance from Rome.',
 'https://images.unsplash.com/photo-1645112411341-6c4fd023714a',
 13.50,
 40),

('Penne Arrabbiata',
 'Penne pasta in a fiery tomato sauce with garlic, red chili peppers, and fresh parsley. "Angry" pasta that packs a punch.',
 'https://images.unsplash.com/photo-1621996346565-e3dbc646d9a9',
 12.00,
 35),

('Risotto ai Funghi',
 'Creamy Arborio rice cooked with porcini and mixed mushrooms, white wine, and Parmigiano. Stirred to perfection.',
 'https://images.unsplash.com/photo-1476124369491-c4f1b0d6fdcc',
 15.00,
 30),

('Lasagne alla Bolognese',
 'Layers of fresh pasta, slow-cooked beef ragù, béchamel sauce, and Parmigiano, baked until golden and bubbling.',
 'https://images.unsplash.com/photo-1574894709920-11b28e7367e3',
 14.50,
 35),

('Gnocchi al Pesto',
 'Pillowy potato gnocchi tossed with fresh Genovese basil pesto, pine nuts, garlic, and Parmigiano.',
 'https://images.unsplash.com/photo-1604908815937-b8e05e83d3f1',
 13.00,
 30);

-- Secondi Piatti (Main Courses) - 5 items
INSERT INTO menu_items (name, description, image_url, price, stock) VALUES
('Pizza Margherita',
 'The queen of pizzas with San Marzano tomato sauce, fresh mozzarella fior di latte, basil, and extra virgin olive oil on wood-fired crust.',
 'https://images.unsplash.com/photo-1574071318508-1cdbab80d002',
 11.00,
 50),

('Pizza Quattro Stagioni',
 'Four seasons pizza divided into artichokes, mushrooms, prosciutto cotto, and black olives. A journey through Italy in every slice.',
 'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38',
 16.00,
 40),

('Osso Buco alla Milanese',
 'Braised veal shanks cooked slowly in white wine, vegetables, and broth until fork-tender. Served with gremolata and saffron risotto.',
 'https://images.unsplash.com/photo-1600891964092-4316c288032e',
 24.00,
 15),

('Pollo alla Parmigiana',
 'Breaded chicken breast topped with marinara sauce and melted mozzarella, served with a side of spaghetti al pomodoro.',
 'https://images.unsplash.com/photo-1603360946369-dc9bb6258143',
 18.00,
 25),

('Saltimbocca alla Romana',
 'Tender veal scaloppine topped with prosciutto and sage, cooked in white wine and butter. "Jumps in your mouth" with flavor.',
 'https://images.unsplash.com/photo-1619895092538-128341789043',
 20.00,
 20);

-- Dolci (Desserts) - 4 items
INSERT INTO menu_items (name, description, image_url, price, stock) VALUES
('Tiramisù',
 'Classic Italian dessert with layers of espresso-soaked ladyfingers, mascarpone cream, and a dusting of cocoa. The pick-me-up you deserve.',
 'https://images.unsplash.com/photo-1571877227200-a0d98ea607e9',
 7.50,
 40),

('Panna Cotta',
 'Silky smooth cooked cream dessert with vanilla, served with berry compote. A delicate end to your meal.',
 'https://images.unsplash.com/photo-1587314168485-3236d6710814',
 6.50,
 35),

('Cannoli Siciliani',
 'Crispy fried pastry shells filled with sweet ricotta cream, chocolate chips, and candied orange peel. Straight from Sicily.',
 'https://images.unsplash.com/photo-1600367721727-97ecf2ea28d1',
 8.00,
 30),

('Gelato Assortito',
 'Three scoops of authentic Italian gelato. Choose from pistachio, stracciatella, hazelnut, lemon, or dark chocolate.',
 'https://images.unsplash.com/photo-1563805042-7684c019e1cb',
 6.00,
 50);

-- Bevande (Beverages) - 4 items
INSERT INTO menu_items (name, description, image_url, price, stock) VALUES
('Espresso',
 'A perfect shot of dark Italian espresso. Bold, rich, and the foundation of Italian coffee culture.',
 'https://images.unsplash.com/photo-1610889556528-9a770e32642f',
 2.50,
 100),

('Cappuccino',
 'Classic morning coffee with espresso, steamed milk, and a thick layer of milk foam. Dusted with cocoa.',
 'https://images.unsplash.com/photo-1572442388796-11668a67e53d',
 3.50,
 80),

('Limoncello',
 'Traditional Italian lemon liqueur from the Amalfi Coast. Served chilled as a digestif.',
 'https://images.unsplash.com/photo-1514361892635-6b07e31e75f9',
 5.00,
 20),

('Chianti Classico',
 'Medium-bodied red wine from Tuscany with notes of cherry, violet, and herbs. Perfect with pasta and meat dishes. Glass.',
 'https://images.unsplash.com/photo-1586370434639-0fe43b2d32d6',
 8.00,
 25);
