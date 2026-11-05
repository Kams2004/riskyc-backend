-- Marketplace (sellers), POS (till sales, ticket settings, desktop tills) and the per-(color,size) stock ledger:
-- everything the code needs on top of the V1 baseline. Every statement is re-runnable, so it is safe on a
-- production database at V1, a brand-new database, and a database that already has some of these objects.

-- ── New tables ──



CREATE TABLE IF NOT EXISTS public.enterprise_categories (
    enterprise_id uuid NOT NULL,
    category_id uuid NOT NULL
);

CREATE TABLE IF NOT EXISTS public.enterprises (
    id uuid NOT NULL,
    approved_at timestamp(6) with time zone,
    approved_by_name character varying(255),
    business_type character varying(255),
    contact_email character varying(255) NOT NULL,
    contact_first_name character varying(255),
    contact_last_name character varying(255),
    contact_phone character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    description text,
    estimated_catalog_size character varying(255),
    name character varying(255) NOT NULL,
    platform_default boolean DEFAULT false NOT NULL,
    status character varying(16) NOT NULL,
    what_do_you_sell text,
    whatsapp_number character varying(255),
    CONSTRAINT enterprises_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying, 'REJECTED'::character varying])::text[])))
);

CREATE TABLE IF NOT EXISTS public.pos_device_enrollments (
    id uuid NOT NULL,
    code_hash character varying(64) NOT NULL,
    created_by_name character varying(255),
    expires_at timestamp(6) with time zone NOT NULL,
    used_at timestamp(6) with time zone,
    enterprise_id uuid
);

CREATE TABLE IF NOT EXISTS public.pos_devices (
    id uuid NOT NULL,
    app_version character varying(255),
    enrolled_at timestamp(6) with time zone NOT NULL,
    enrolled_by_name character varying(255),
    last_seen_at timestamp(6) with time zone,
    name character varying(255) NOT NULL,
    pending_sales integer DEFAULT 0 NOT NULL,
    platform character varying(255),
    revoked_at timestamp(6) with time zone,
    revoked_by_name character varying(255),
    token_hash character varying(64) NOT NULL,
    enterprise_id uuid
);

CREATE TABLE IF NOT EXISTS public.pos_sale_items (
    id uuid NOT NULL,
    product_name character varying(255) NOT NULL,
    quantity integer NOT NULL,
    reconciled boolean NOT NULL,
    reconciled_at timestamp(6) with time zone,
    reconciled_by_name character varying(255),
    selected_color character varying(255) NOT NULL,
    selected_size character varying(255) NOT NULL,
    stock_before_sale integer,
    unit_price numeric(12,2) NOT NULL,
    product_id uuid,
    sale_id uuid NOT NULL
);

CREATE TABLE IF NOT EXISTS public.pos_sales (
    id uuid NOT NULL,
    cashier_id uuid,
    cashier_name character varying(255) NOT NULL,
    client_sale_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    payment_method character varying(255) NOT NULL,
    receipt_key character varying(255),
    sold_at timestamp(6) with time zone NOT NULL,
    stock_reconciliation_flag boolean NOT NULL,
    total numeric(12,2) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    voided boolean NOT NULL,
    voided_at timestamp(6) with time zone,
    voided_by_name character varying(255),
    enterprise_id uuid,
    CONSTRAINT pos_sales_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['CASH'::character varying, 'ORANGE_MONEY'::character varying, 'MOBILE_MONEY'::character varying])::text[])))
);

CREATE TABLE IF NOT EXISTS public.pos_settings (
    id uuid NOT NULL,
    cash_enabled boolean NOT NULL,
    footer_message text,
    logo_key character varying(255),
    mobile_money_enabled boolean NOT NULL,
    orange_money_enabled boolean NOT NULL,
    paper_width character varying(255) NOT NULL,
    show_cashier_name boolean NOT NULL,
    show_logo boolean DEFAULT true NOT NULL,
    show_sale_id boolean NOT NULL,
    store_address character varying(255),
    store_name character varying(255) NOT NULL,
    store_phone character varying(255),
    updated_at timestamp(6) with time zone NOT NULL,
    enterprise_id uuid,
    CONSTRAINT pos_settings_paper_width_check CHECK (((paper_width)::text = ANY ((ARRAY['MM58'::character varying, 'MM80'::character varying, 'A4'::character varying])::text[])))
);

CREATE TABLE IF NOT EXISTS public.product_variants (
    id uuid NOT NULL,
    color_name character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    low_stock_threshold integer,
    quantity integer NOT NULL,
    size character varying(255) NOT NULL,
    untracked boolean NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    product_id uuid NOT NULL
);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'enterprise_categories_pkey') THEN
        ALTER TABLE ONLY public.enterprise_categories ADD CONSTRAINT enterprise_categories_pkey PRIMARY KEY (enterprise_id, category_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'enterprises_pkey') THEN
        ALTER TABLE ONLY public.enterprises ADD CONSTRAINT enterprises_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pos_device_enrollments_pkey') THEN
        ALTER TABLE ONLY public.pos_device_enrollments ADD CONSTRAINT pos_device_enrollments_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pos_devices_pkey') THEN
        ALTER TABLE ONLY public.pos_devices ADD CONSTRAINT pos_devices_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pos_sale_items_pkey') THEN
        ALTER TABLE ONLY public.pos_sale_items ADD CONSTRAINT pos_sale_items_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pos_sales_pkey') THEN
        ALTER TABLE ONLY public.pos_sales ADD CONSTRAINT pos_sales_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'pos_settings_pkey') THEN
        ALTER TABLE ONLY public.pos_settings ADD CONSTRAINT pos_settings_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'product_variants_pkey') THEN
        ALTER TABLE ONLY public.product_variants ADD CONSTRAINT product_variants_pkey PRIMARY KEY (id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ukeuscah5fjyq0jhr3u2aw50iab') THEN
        ALTER TABLE ONLY public.pos_sales ADD CONSTRAINT ukeuscah5fjyq0jhr3u2aw50iab UNIQUE (client_sale_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uknyf13rvtvk5alw0pltd704xdr') THEN
        ALTER TABLE ONLY public.product_variants ADD CONSTRAINT uknyf13rvtvk5alw0pltd704xdr UNIQUE (product_id, color_name, size);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uks0ubhj51odwdn9i848t49dm6s') THEN
        ALTER TABLE ONLY public.pos_settings ADD CONSTRAINT uks0ubhj51odwdn9i848t49dm6s UNIQUE (enterprise_id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ukswbu4cg8sqd1uqs1mfo0me4hv') THEN
        ALTER TABLE ONLY public.pos_device_enrollments ADD CONSTRAINT ukswbu4cg8sqd1uqs1mfo0me4hv UNIQUE (code_hash);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk140yc28rlpbpnq62qbxkecura') THEN
        ALTER TABLE ONLY public.pos_device_enrollments ADD CONSTRAINT fk140yc28rlpbpnq62qbxkecura FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk3vhclqgd5dr7eqyku1ye2hfex') THEN
        ALTER TABLE ONLY public.enterprise_categories ADD CONSTRAINT fk3vhclqgd5dr7eqyku1ye2hfex FOREIGN KEY (category_id) REFERENCES public.categories(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk6htfv2yoqx4no7tm4n05624kk') THEN
        ALTER TABLE ONLY public.pos_settings ADD CONSTRAINT fk6htfv2yoqx4no7tm4n05624kk FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkbsuiqg9jubiqfa04urdksp091') THEN
        ALTER TABLE ONLY public.pos_sale_items ADD CONSTRAINT fkbsuiqg9jubiqfa04urdksp091 FOREIGN KEY (product_id) REFERENCES public.products(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkkqprv156kh8tr77561ufwhdck') THEN
        ALTER TABLE ONLY public.enterprise_categories ADD CONSTRAINT fkkqprv156kh8tr77561ufwhdck FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fklac5qlwkt085wd3mgqlsyt0l0') THEN
        ALTER TABLE ONLY public.pos_sales ADD CONSTRAINT fklac5qlwkt085wd3mgqlsyt0l0 FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkomuej5hyhrph99fvh69b0j5cv') THEN
        ALTER TABLE ONLY public.pos_devices ADD CONSTRAINT fkomuej5hyhrph99fvh69b0j5cv FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkosqitn4s405cynmhb87lkvuau') THEN
        ALTER TABLE ONLY public.product_variants ADD CONSTRAINT fkosqitn4s405cynmhb87lkvuau FOREIGN KEY (product_id) REFERENCES public.products(id);
    END IF;
END $$;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkpodkiymphmr6a9ojjh32yqavd') THEN
        ALTER TABLE ONLY public.pos_sale_items ADD CONSTRAINT fkpodkiymphmr6a9ojjh32yqavd FOREIGN KEY (sale_id) REFERENCES public.pos_sales(id);
    END IF;
END $$;

-- ── New columns on existing tables (all nullable or with a default, so existing rows are fine) ──
ALTER TABLE public.admin_users ADD COLUMN IF NOT EXISTS enterprise_id uuid;
ALTER TABLE public.admin_users ADD COLUMN IF NOT EXISTS must_change_password boolean DEFAULT false NOT NULL;
ALTER TABLE public.delivery_contacts ADD COLUMN IF NOT EXISTS enterprise_id uuid;
ALTER TABLE public.notifications ADD COLUMN IF NOT EXISTS enterprise_id uuid;
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS checkout_group_id uuid;
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS enterprise_id uuid;
ALTER TABLE public.products ADD COLUMN IF NOT EXISTS enterprise_id uuid;
ALTER TABLE public.roles ADD COLUMN IF NOT EXISTS enterprise_id uuid;

-- ── Links from existing tables to their seller ──
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkofowk9jfj2p5y7wwijj1ne8or') THEN
        ALTER TABLE public.admin_users ADD CONSTRAINT fkofowk9jfj2p5y7wwijj1ne8or FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkfshj2lr1j75krd3u7a4ri2yut') THEN
        ALTER TABLE public.delivery_contacts ADD CONSTRAINT fkfshj2lr1j75krd3u7a4ri2yut FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkdtpj4d5nwunwupf2mkuftqk2i') THEN
        ALTER TABLE public.notifications ADD CONSTRAINT fkdtpj4d5nwunwupf2mkuftqk2i FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk8roqr7uk5ycr8kcie57hlv1r8') THEN
        ALTER TABLE public.orders ADD CONSTRAINT fk8roqr7uk5ycr8kcie57hlv1r8 FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fkfbvh43ybvuq7i57dnl45ct8wi') THEN
        ALTER TABLE public.products ADD CONSTRAINT fkfbvh43ybvuq7i57dnl45ct8wi FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk84lse9vlynp4jhxgu4o1sls1d') THEN
        ALTER TABLE public.roles ADD CONSTRAINT fk84lse9vlynp4jhxgu4o1sls1d FOREIGN KEY (enterprise_id) REFERENCES public.enterprises(id);
    END IF;
END $$;

-- ── Role names: unique per seller instead of platform-wide ──
-- Every approved seller gets its own "Owner" role, so a platform-wide unique name would block the second seller.
ALTER TABLE public.roles DROP CONSTRAINT IF EXISTS ukofx66keruapi6vyqpv6f2or37;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk9f3rcgnyxsr200k736e5adtu9') THEN
        ALTER TABLE public.roles ADD CONSTRAINT uk9f3rcgnyxsr200k736e5adtu9 UNIQUE (enterprise_id, name);
    END IF;
END $$;
