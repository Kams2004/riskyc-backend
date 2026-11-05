-- Each POS screen now has its own permission (POS_SELL, POS_VIEW_SALES, POS_RECONCILE). Give every existing role
-- the ones matching what it could already do, so nobody loses access: ringing up a sale used to need MANAGE_POS;
-- Sales History and Reconciliation used to be open to anyone with VIEW_POS. Re-runnable.

INSERT INTO public.role_permissions (role_id, permission)
SELECT DISTINCT rp.role_id, 'POS_SELL' FROM public.role_permissions rp
WHERE rp.permission = 'MANAGE_POS'
  AND NOT EXISTS (SELECT 1 FROM public.role_permissions x WHERE x.role_id = rp.role_id AND x.permission = 'POS_SELL');

INSERT INTO public.role_permissions (role_id, permission)
SELECT DISTINCT rp.role_id, 'POS_VIEW_SALES' FROM public.role_permissions rp
WHERE rp.permission = 'VIEW_POS'
  AND NOT EXISTS (SELECT 1 FROM public.role_permissions x WHERE x.role_id = rp.role_id AND x.permission = 'POS_VIEW_SALES');

INSERT INTO public.role_permissions (role_id, permission)
SELECT DISTINCT rp.role_id, 'POS_RECONCILE' FROM public.role_permissions rp
WHERE rp.permission = 'VIEW_POS'
  AND NOT EXISTS (SELECT 1 FROM public.role_permissions x WHERE x.role_id = rp.role_id AND x.permission = 'POS_RECONCILE');
