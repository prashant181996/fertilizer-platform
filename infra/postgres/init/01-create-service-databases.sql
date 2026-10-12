-- Local development only: separate logical databases establish clear write ownership.
-- Production should use separate service credentials and preferably independently managed DBs.
CREATE DATABASE fertilizer_orders;
CREATE DATABASE fertilizer_products;
CREATE DATABASE fertilizer_inventory;
