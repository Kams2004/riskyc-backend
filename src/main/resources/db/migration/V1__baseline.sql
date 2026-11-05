-- Baseline: the schema of the deployed backend (riskyc-backend main @ e0a8da9) when migrations were introduced,
-- generated from what that code's Hibernate creates. Existing databases are marked as already at this version
-- (spring.flyway.baseline-on-migrate) and never run it; a brand-new database is created from it.

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

CREATE TABLE public.admin_users (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    first_name character varying(255) NOT NULL,
    last_login timestamp(6) with time zone,
    last_name character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    role_id uuid NOT NULL,
    CONSTRAINT admin_users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);

CREATE TABLE public.categories (
    id uuid NOT NULL,
    created_by_name character varying(255),
    icon character varying(255),
    image_storage_key character varying(255),
    name character varying(255) NOT NULL,
    name_fr character varying(255),
    slug character varying(255) NOT NULL
);

CREATE TABLE public.chat_message_delivery_contacts (
    chat_message_id uuid NOT NULL,
    name character varying(255),
    phone character varying(255),
    "position" integer NOT NULL,
    CONSTRAINT chat_message_delivery_contacts_position_check CHECK (("position" >= 0))
);

CREATE TABLE public.chat_messages (
    id uuid NOT NULL,
    admin_sender_name character varying(255),
    image_storage_key character varying(255),
    packaging_confirmation boolean DEFAULT false NOT NULL,
    sender character varying(255) NOT NULL,
    text text NOT NULL,
    "timestamp" timestamp(6) with time zone NOT NULL,
    voice_duration_seconds integer,
    voice_storage_key character varying(255),
    conversation_id uuid NOT NULL,
    CONSTRAINT chat_messages_sender_check CHECK (((sender)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'ADMIN'::character varying])::text[])))
);

CREATE TABLE public.conversations (
    id uuid NOT NULL,
    admin_read_at timestamp(6) with time zone,
    created_at timestamp(6) with time zone NOT NULL,
    customer_name character varying(255) NOT NULL,
    customer_read_at timestamp(6) with time zone,
    last_message_at timestamp(6) with time zone,
    unread integer NOT NULL,
    customer_id uuid,
    order_id uuid
);

CREATE TABLE public.customers (
    id uuid NOT NULL,
    acronym character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    first_name character varying(255) NOT NULL,
    google_id character varying(255),
    last_login timestamp(6) with time zone,
    last_name character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    phone character varying(255),
    referral_code character varying(255),
    status character varying(255) NOT NULL,
    referred_by_id uuid,
    CONSTRAINT customers_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'BLOCKED'::character varying])::text[])))
);

CREATE TABLE public.delivery_contacts (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    name character varying(255) NOT NULL,
    phone character varying(255) NOT NULL,
    "position" integer NOT NULL
);

CREATE TABLE public.expo_push_tokens (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    language character varying(5),
    order_id uuid NOT NULL,
    token character varying(255) NOT NULL
);

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    message character varying(255) NOT NULL,
    read boolean NOT NULL,
    recipient_type character varying(255) NOT NULL,
    reference_id character varying(255),
    type character varying(255) NOT NULL,
    customer_id uuid,
    CONSTRAINT notifications_recipient_type_check CHECK (((recipient_type)::text = ANY ((ARRAY['ADMIN'::character varying, 'CUSTOMER'::character varying])::text[]))),
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY ((ARRAY['NEW_ORDER'::character varying, 'NEW_MESSAGE'::character varying, 'PAYMENT_PROOF_UPLOADED'::character varying, 'ORDER_STATUS_CHANGED'::character varying])::text[])))
);

CREATE TABLE public.order_items (
    id uuid NOT NULL,
    product_name character varying(255) NOT NULL,
    quantity integer NOT NULL,
    selected_color character varying(255),
    selected_image_index integer,
    selected_size character varying(255),
    unit_price numeric(12,2) NOT NULL,
    order_id uuid NOT NULL,
    product_id uuid
);

CREATE TABLE public.orders (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    info_delivery_type character varying(255),
    info_first_name character varying(255),
    info_last_name character varying(255),
    info_phone character varying(255),
    info_street character varying(255),
    info_town character varying(255),
    language character varying(5),
    packaging_completed_at timestamp(6) with time zone,
    packaging_completed_by_id uuid,
    packaging_completed_by_name character varying(255),
    packaging_started_at timestamp(6) with time zone,
    packaging_started_by_id uuid,
    packaging_started_by_name character varying(255),
    payment_account_name character varying(255),
    payment_code character varying(255),
    payment_method character varying(255),
    payment_screenshot_key character varying(255),
    rejection_reason text,
    status character varying(255) NOT NULL,
    status_changed_at timestamp(6) with time zone,
    status_changed_by_name character varying(255),
    total numeric(12,2) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    customer_id uuid,
    CONSTRAINT orders_info_delivery_type_check CHECK (((info_delivery_type)::text = ANY ((ARRAY['DELIVERY'::character varying, 'PICKUP'::character varying])::text[]))),
    CONSTRAINT orders_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['ORANGE_MONEY'::character varying, 'MOBILE_MONEY'::character varying])::text[]))),
    CONSTRAINT orders_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'AWAITING_PAYMENT'::character varying, 'REVIEWING'::character varying, 'VALIDATED'::character varying, 'PACKAGING'::character varying, 'PACKAGED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE public.product_audit_logs (
    id uuid NOT NULL,
    changed_at timestamp(6) with time zone NOT NULL,
    changed_by_id uuid,
    changed_by_name character varying(255),
    product_id uuid NOT NULL,
    product_name character varying(255) NOT NULL,
    section character varying(20) NOT NULL,
    summary text NOT NULL,
    CONSTRAINT product_audit_logs_section_check CHECK (((section)::text = ANY ((ARRAY['CREATED'::character varying, 'DELETED'::character varying, 'INFO'::character varying, 'PRICING'::character varying, 'IMAGES'::character varying, 'COLORS'::character varying, 'STOCK'::character varying, 'DISPLAY'::character varying, 'VISIBILITY'::character varying])::text[])))
);

CREATE TABLE public.product_bulk_prices (
    product_id uuid NOT NULL,
    price numeric(12,2) NOT NULL,
    quantity integer NOT NULL,
    "position" integer NOT NULL,
    CONSTRAINT product_bulk_prices_position_check CHECK (("position" >= 0))
);

CREATE TABLE public.product_colors (
    id uuid NOT NULL,
    hex character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    stock integer,
    product_id uuid NOT NULL
);

CREATE TABLE public.product_media (
    id uuid NOT NULL,
    content_type character varying(255),
    original_filename character varying(255),
    size_bytes bigint,
    storage_key character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    uploaded_at timestamp(6) with time zone NOT NULL,
    product_id uuid NOT NULL,
    CONSTRAINT product_media_type_check CHECK (((type)::text = ANY ((ARRAY['IMAGE'::character varying, 'VIDEO'::character varying])::text[])))
);

CREATE TABLE public.product_sizes (
    product_id uuid NOT NULL,
    size character varying(255),
    "position" integer NOT NULL,
    CONSTRAINT product_sizes_position_check CHECK (("position" >= 0))
);

CREATE TABLE public.product_tags (
    product_id uuid NOT NULL,
    tag character varying(255)
);

CREATE TABLE public.products (
    id uuid NOT NULL,
    badge character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    created_by_name character varying(255),
    description text,
    description_fr text,
    hidden boolean NOT NULL,
    name character varying(255) NOT NULL,
    name_fr character varying(255),
    original_price numeric(12,2),
    price numeric(12,2) NOT NULL,
    rating double precision NOT NULL,
    reviews integer NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    category_id uuid,
    subcategory_id uuid,
    CONSTRAINT products_badge_check CHECK (((badge)::text = ANY ((ARRAY['NEW'::character varying, 'SALE'::character varying, 'HOT'::character varying])::text[])))
);

CREATE TABLE public.publicity_items (
    id uuid NOT NULL,
    active boolean NOT NULL,
    caption text,
    caption_font character varying(255),
    caption_style character varying(255),
    content_type character varying(255),
    original_filename character varying(255),
    "position" integer NOT NULL,
    size_bytes bigint,
    storage_key character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    uploaded_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT publicity_items_type_check CHECK (((type)::text = ANY ((ARRAY['IMAGE'::character varying, 'VIDEO'::character varying])::text[])))
);

CREATE TABLE public.push_subscriptions (
    id uuid NOT NULL,
    auth character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    endpoint character varying(512) NOT NULL,
    language character varying(5),
    order_id uuid NOT NULL,
    p256dh character varying(255) NOT NULL
);

CREATE TABLE public.role_permissions (
    role_id uuid NOT NULL,
    permission character varying(64)
);

CREATE TABLE public.roles (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL
);

CREATE TABLE public.subcategories (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    name_fr character varying(255),
    slug character varying(255) NOT NULL,
    category_id uuid NOT NULL
);

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT admin_users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.chat_message_delivery_contacts
    ADD CONSTRAINT chat_message_delivery_contacts_pkey PRIMARY KEY (chat_message_id, "position");

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT chat_messages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT conversations_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.delivery_contacts
    ADD CONSTRAINT delivery_contacts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.expo_push_tokens
    ADD CONSTRAINT expo_push_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_audit_logs
    ADD CONSTRAINT product_audit_logs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_bulk_prices
    ADD CONSTRAINT product_bulk_prices_pkey PRIMARY KEY (product_id, "position");

ALTER TABLE ONLY public.product_colors
    ADD CONSTRAINT product_colors_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_media
    ADD CONSTRAINT product_media_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_sizes
    ADD CONSTRAINT product_sizes_pkey PRIMARY KEY (product_id, "position");

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.publicity_items
    ADD CONSTRAINT publicity_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.push_subscriptions
    ADD CONSTRAINT push_subscriptions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.subcategories
    ADD CONSTRAINT subcategories_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT uk1flmxjr60k5bd22yv9oid8kkf UNIQUE (google_id);

ALTER TABLE ONLY public.expo_push_tokens
    ADD CONSTRAINT uk3lxjwksisxe86em4u8grej72c UNIQUE (token);

ALTER TABLE ONLY public.push_subscriptions
    ADD CONSTRAINT uk6vabkyu7g789ehysoff7cfumf UNIQUE (endpoint);

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT ukcp8822350s9vtyww7xdbgeuvu UNIQUE (email);

ALTER TABLE ONLY public.publicity_items
    ADD CONSTRAINT ukd92m4m4nsuxh2xwte3573uod1 UNIQUE (storage_key);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT ukebe52bruv6ovf6b0wqc1hsuvy UNIQUE (role_id, permission);

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT uki151kmee2a0q4rfe4ee53w4bs UNIQUE (referral_code);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT ukofx66keruapi6vyqpv6f2or37 UNIQUE (name);

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT ukoul14ho7bctbefv8jywp5v3i2 UNIQUE (slug);

ALTER TABLE ONLY public.product_media
    ADD CONSTRAINT ukrddbmk8eee6urapki8lvebqm4 UNIQUE (storage_key);

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT ukrfbvkrffamfql7cjmen8v976v UNIQUE (email);

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk30dp6ycner3dgso3scgc9vghy FOREIGN KEY (customer_id) REFERENCES public.customers(id);

ALTER TABLE ONLY public.product_sizes
    ADD CONSTRAINT fk4isa0j51hpdn7cx04m831jic4 FOREIGN KEY (product_id) REFERENCES public.products(id);

ALTER TABLE ONLY public.product_tags
    ADD CONSTRAINT fk5rk6s19k3risy7q7wqdr41uss FOREIGN KEY (product_id) REFERENCES public.products(id);

ALTER TABLE ONLY public.product_media
    ADD CONSTRAINT fk6d0dleyyjhq9qg62vto0kn01f FOREIGN KEY (product_id) REFERENCES public.products(id);

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT fk6muuj1w416woh6dempwf8klh1 FOREIGN KEY (referred_by_id) REFERENCES public.customers(id);

ALTER TABLE ONLY public.product_bulk_prices
    ADD CONSTRAINT fk93j3dalfhmij4r5vpw5p3cmg5 FOREIGN KEY (product_id) REFERENCES public.products(id);

ALTER TABLE ONLY public.admin_users
    ADD CONSTRAINT fkabq98htiwain45verwhssi5yi FOREIGN KEY (role_id) REFERENCES public.roles(id);

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fkappm930ygdfv4qkkhc05pbr5s FOREIGN KEY (subcategory_id) REFERENCES public.subcategories(id);

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES public.orders(id);

ALTER TABLE ONLY public.chat_messages
    ADD CONSTRAINT fkc8ljv426x8fj9tcywei40stu9 FOREIGN KEY (conversation_id) REFERENCES public.conversations(id);

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT fkhrqycoqjka1jcpl4k2namguhb FOREIGN KEY (customer_id) REFERENCES public.customers(id);

ALTER TABLE ONLY public.subcategories
    ADD CONSTRAINT fkiborb6ptvy1t1n3v6klb56l5s FOREIGN KEY (category_id) REFERENCES public.categories(id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES public.roles(id);

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkocimc7dtr037rh4ls4l95nlfi FOREIGN KEY (product_id) REFERENCES public.products(id);

ALTER TABLE ONLY public.products
    ADD CONSTRAINT fkog2rp4qthbtt2lfyhfo32lsw9 FOREIGN KEY (category_id) REFERENCES public.categories(id);

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fkpxtb8awmi0dk6smoh2vp1litg FOREIGN KEY (customer_id) REFERENCES public.customers(id);

ALTER TABLE ONLY public.product_colors
    ADD CONSTRAINT fkqhu7cqni31911lmvx4fqmiw65 FOREIGN KEY (product_id) REFERENCES public.products(id);

ALTER TABLE ONLY public.chat_message_delivery_contacts
    ADD CONSTRAINT fks84afkp9pd9ui320uc7idknis FOREIGN KEY (chat_message_id) REFERENCES public.chat_messages(id);

ALTER TABLE ONLY public.conversations
    ADD CONSTRAINT fktdy4x7u71rhh9hg8qef6bfoh8 FOREIGN KEY (order_id) REFERENCES public.orders(id);
