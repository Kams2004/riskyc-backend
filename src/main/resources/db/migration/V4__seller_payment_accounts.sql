-- Each seller has its own Orange Money / MTN numbers: customers pay the seller directly and the seller validates the
-- payment. The platform's own store is filled from the existing app.payment.* settings at startup (DataSeeder).
-- Re-runnable.

ALTER TABLE public.enterprises ADD COLUMN IF NOT EXISTS orange_money_code VARCHAR(255);
ALTER TABLE public.enterprises ADD COLUMN IF NOT EXISTS orange_money_name VARCHAR(255);
ALTER TABLE public.enterprises ADD COLUMN IF NOT EXISTS mobile_money_code VARCHAR(255);
ALTER TABLE public.enterprises ADD COLUMN IF NOT EXISTS mobile_money_name VARCHAR(255);

-- Whoever can manage a shop's users (each seller's owner role, the platform's Super Admin) can set its payment numbers.
INSERT INTO public.role_permissions (role_id, permission)
SELECT DISTINCT rp.role_id, 'MANAGE_PAYMENT_SETTINGS' FROM public.role_permissions rp
WHERE rp.permission = 'MANAGE_USERS'
  AND NOT EXISTS (SELECT 1 FROM public.role_permissions x WHERE x.role_id = rp.role_id AND x.permission = 'MANAGE_PAYMENT_SETTINGS');

-- Sellers also pack and ship their own orders with their own delivery team: every seller's owner role (a shop-scoped
-- role that can manage users) gets the delivery-team and packing-confirmation permissions.
INSERT INTO public.role_permissions (role_id, permission)
SELECT DISTINCT rp.role_id, p.permission
FROM public.role_permissions rp
JOIN public.roles r ON r.id = rp.role_id
CROSS JOIN (VALUES ('MANAGE_DELIVERY_AGENTS'), ('SEND_PACKAGING_MESSAGE')) AS p(permission)
WHERE rp.permission = 'MANAGE_USERS' AND r.enterprise_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM public.role_permissions x WHERE x.role_id = rp.role_id AND x.permission = p.permission);
