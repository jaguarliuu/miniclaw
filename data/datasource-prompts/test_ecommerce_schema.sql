-- ============================================================
-- MiniClaw 自然语言查询测试库 —— 电商运营分析平台
-- 目标数据库: PostgreSQL 14+
-- 用途: 测试 DataQuery Agent 的多表关联、聚合、筛选、方言等能力
-- ============================================================

-- 清理（幂等）
DROP SCHEMA IF EXISTS ecommerce CASCADE;
CREATE SCHEMA ecommerce;
SET search_path TO ecommerce;

-- ============================================================
-- 1. 基础枚举 / 字典
-- ============================================================

CREATE TYPE gender_enum AS ENUM ('M', 'F', 'OTHER');
CREATE TYPE order_status AS ENUM ('pending', 'paid', 'shipped', 'delivered', 'cancelled', 'refunded');
CREATE TYPE payment_method AS ENUM ('alipay', 'wechat', 'credit_card', 'bank_transfer', 'cash_on_delivery');
CREATE TYPE ticket_status AS ENUM ('open', 'in_progress', 'resolved', 'closed');
CREATE TYPE ticket_priority AS ENUM ('low', 'medium', 'high', 'urgent');

-- ============================================================
-- 2. 用户体系
-- ============================================================

-- 会员等级
CREATE TABLE member_levels (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(30)  NOT NULL UNIQUE,       -- 等级名称: 普通/银卡/金卡/钻石
    min_points  INT          NOT NULL DEFAULT 0,    -- 最低积分门槛
    discount    NUMERIC(3,2) NOT NULL DEFAULT 1.00, -- 折扣系数 0.80 = 8折
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  member_levels            IS '会员等级表';
COMMENT ON COLUMN member_levels.name       IS '等级名称';
COMMENT ON COLUMN member_levels.min_points IS '升级所需最低积分';
COMMENT ON COLUMN member_levels.discount   IS '折扣系数，1.00=无折扣，0.85=85折';

INSERT INTO member_levels (name, min_points, discount) VALUES
('普通会员', 0,     1.00),
('银卡会员', 1000,  0.95),
('金卡会员', 5000,  0.90),
('钻石会员', 20000, 0.85);

-- 用户表
CREATE TABLE users (
    id              BIGSERIAL    PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    email           VARCHAR(120) NOT NULL UNIQUE,
    phone           VARCHAR(20),
    gender          gender_enum,
    birth_date      DATE,
    avatar_url      TEXT,
    member_level_id INT          NOT NULL DEFAULT 1 REFERENCES member_levels(id),
    points          INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    registered_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMP,
    province        VARCHAR(30),
    city            VARCHAR(30)
);
COMMENT ON TABLE  users                  IS '用户表';
COMMENT ON COLUMN users.username         IS '用户名';
COMMENT ON COLUMN users.phone            IS '手机号';
COMMENT ON COLUMN users.member_level_id  IS '会员等级ID';
COMMENT ON COLUMN users.points           IS '当前积分';
COMMENT ON COLUMN users.province         IS '省份';
COMMENT ON COLUMN users.city             IS '城市';

CREATE INDEX idx_users_level     ON users(member_level_id);
CREATE INDEX idx_users_province  ON users(province);
CREATE INDEX idx_users_reg_date  ON users(registered_at);

-- 用户收货地址（1:N）
CREATE TABLE user_addresses (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label       VARCHAR(20) NOT NULL DEFAULT '家',  -- 家/公司/学校
    receiver    VARCHAR(50) NOT NULL,
    phone       VARCHAR(20) NOT NULL,
    province    VARCHAR(30) NOT NULL,
    city        VARCHAR(30) NOT NULL,
    district    VARCHAR(30) NOT NULL,
    street      TEXT        NOT NULL,
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  user_addresses       IS '用户收货地址表';
COMMENT ON COLUMN user_addresses.label IS '地址标签: 家/公司/学校等';

CREATE INDEX idx_addr_user ON user_addresses(user_id);

-- ============================================================
-- 3. 商品体系（三级分类 + SPU/SKU）
-- ============================================================

-- 商品分类（支持三级）
CREATE TABLE categories (
    id          SERIAL      PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,
    parent_id   INT         REFERENCES categories(id),
    level       SMALLINT    NOT NULL DEFAULT 1,  -- 1/2/3
    sort_order  INT         NOT NULL DEFAULT 0,
    icon_url    TEXT,
    is_visible  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  categories            IS '商品分类表（三级树形）';
COMMENT ON COLUMN categories.parent_id  IS '父分类ID，一级分类为NULL';
COMMENT ON COLUMN categories.level      IS '层级：1=一级 2=二级 3=三级';

CREATE INDEX idx_cat_parent ON categories(parent_id);

INSERT INTO categories (id, name, parent_id, level) VALUES
(1,  '数码电子',  NULL, 1),
(2,  '服饰鞋包',  NULL, 1),
(3,  '食品饮料',  NULL, 1),
(4,  '家居家装',  NULL, 1),
(10, '手机',      1,    2),
(11, '笔记本电脑', 1,   2),
(12, '耳机音箱',  1,    2),
(20, '男装',      2,    2),
(21, '女装',      2,    2),
(22, '运动鞋',    2,    2),
(30, '零食',      3,    2),
(31, '饮料',      3,    2),
(100, 'iPhone',    10,  3),
(101, 'Android旗舰', 10, 3),
(110, '游戏本',    11,  3),
(111, '轻薄本',    11,  3),
(120, '真无线耳机', 12,  3),
(200, 'T恤',       20,  3),
(210, '连衣裙',    21,  3),
(220, '跑步鞋',    22,  3);

-- 品牌
CREATE TABLE brands (
    id          SERIAL       PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL UNIQUE,
    name_en     VARCHAR(60),
    logo_url    TEXT,
    country     VARCHAR(30),
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  brands          IS '品牌表';
COMMENT ON COLUMN brands.country  IS '品牌所属国家';

INSERT INTO brands (id, name, name_en, country) VALUES
(1,  '苹果',   'Apple',    '美国'),
(2,  '华为',   'Huawei',   '中国'),
(3,  '小米',   'Xiaomi',   '中国'),
(4,  '联想',   'Lenovo',   '中国'),
(5,  '耐克',   'Nike',     '美国'),
(6,  '阿迪达斯', 'Adidas',  '德国'),
(7,  '优衣库', 'Uniqlo',   '日本'),
(8,  '三只松鼠', NULL,      '中国'),
(9,  '索尼',   'Sony',     '日本'),
(10, 'ZARA',   'ZARA',     '西班牙');

-- 商品 SPU
CREATE TABLE products (
    id              BIGSERIAL    PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    brand_id        INT          REFERENCES brands(id),
    category_id     INT          NOT NULL REFERENCES categories(id),
    description     TEXT,
    main_image_url  TEXT,
    is_on_sale      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  products              IS '商品SPU表';
COMMENT ON COLUMN products.name         IS '商品名称';
COMMENT ON COLUMN products.brand_id     IS '品牌ID';
COMMENT ON COLUMN products.category_id  IS '所属三级分类ID';
COMMENT ON COLUMN products.is_on_sale   IS '是否在售';

CREATE INDEX idx_prod_brand    ON products(brand_id);
CREATE INDEX idx_prod_category ON products(category_id);

-- 商品 SKU（规格维度）
CREATE TABLE product_skus (
    id              BIGSERIAL      PRIMARY KEY,
    product_id      BIGINT         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku_code        VARCHAR(50)    NOT NULL UNIQUE,
    spec_json       JSONB,                           -- {"颜色":"星光色","存储":"256GB"}
    price           NUMERIC(10,2)  NOT NULL,
    cost_price      NUMERIC(10,2),                   -- 成本价
    stock           INT            NOT NULL DEFAULT 0,
    sales_count     INT            NOT NULL DEFAULT 0,
    weight_kg       NUMERIC(6,3),
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  product_skus            IS '商品SKU表（最小售卖单元）';
COMMENT ON COLUMN product_skus.spec_json  IS '规格JSON，如 {"颜色":"黑色","尺码":"XL"}';
COMMENT ON COLUMN product_skus.price      IS '销售价（元）';
COMMENT ON COLUMN product_skus.cost_price IS '成本价（元）';
COMMENT ON COLUMN product_skus.stock      IS '当前库存';
COMMENT ON COLUMN product_skus.sales_count IS '累计销量';

CREATE INDEX idx_sku_product ON product_skus(product_id);
CREATE INDEX idx_sku_spec    ON product_skus USING GIN(spec_json);

-- ============================================================
-- 4. 促销 & 优惠券
-- ============================================================

CREATE TABLE coupons (
    id              BIGSERIAL      PRIMARY KEY,
    code            VARCHAR(30)    NOT NULL UNIQUE,
    name            VARCHAR(100)   NOT NULL,
    type            VARCHAR(20)    NOT NULL,          -- 'fixed'=满减, 'percent'=折扣, 'freebie'=赠品
    discount_value  NUMERIC(10,2)  NOT NULL,          -- 满减金额 or 折扣百分比(0-100)
    min_purchase    NUMERIC(10,2)  NOT NULL DEFAULT 0,-- 最低消费门槛
    max_discount    NUMERIC(10,2),                    -- 折扣券最大抵扣额
    total_quota     INT            NOT NULL DEFAULT 1000,
    used_count      INT            NOT NULL DEFAULT 0,
    start_time      TIMESTAMP      NOT NULL,
    end_time        TIMESTAMP      NOT NULL,
    category_id     INT            REFERENCES categories(id), -- NULL=全场通用
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  coupons                IS '优惠券模板表';
COMMENT ON COLUMN coupons.type           IS '类型: fixed=满减, percent=折扣, freebie=赠品';
COMMENT ON COLUMN coupons.discount_value IS 'fixed时为减免金额，percent时为折扣百分比';
COMMENT ON COLUMN coupons.min_purchase   IS '最低消费门槛（元）';
COMMENT ON COLUMN coupons.category_id    IS '限定分类，NULL表示全场通用';

-- 用户领取的优惠券（N:N + 使用状态）
CREATE TABLE user_coupons (
    id          BIGSERIAL  PRIMARY KEY,
    user_id     BIGINT     NOT NULL REFERENCES users(id),
    coupon_id   BIGINT     NOT NULL REFERENCES coupons(id),
    is_used     BOOLEAN    NOT NULL DEFAULT FALSE,
    used_at     TIMESTAMP,
    order_id    BIGINT,    -- 使用时关联的订单ID
    claimed_at  TIMESTAMP  NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE user_coupons IS '用户优惠券领取/使用记录';

CREATE INDEX idx_uc_user   ON user_coupons(user_id);
CREATE INDEX idx_uc_coupon ON user_coupons(coupon_id);

-- ============================================================
-- 5. 订单体系（主从表 + 支付 + 物流）
-- ============================================================

CREATE TABLE orders (
    id               BIGSERIAL       PRIMARY KEY,
    order_no         VARCHAR(30)     NOT NULL UNIQUE,
    user_id          BIGINT          NOT NULL REFERENCES users(id),
    status           order_status    NOT NULL DEFAULT 'pending',
    total_amount     NUMERIC(12,2)   NOT NULL,          -- 商品总价
    discount_amount  NUMERIC(12,2)   NOT NULL DEFAULT 0, -- 优惠金额
    shipping_fee     NUMERIC(8,2)    NOT NULL DEFAULT 0,
    pay_amount       NUMERIC(12,2)   NOT NULL,           -- 实付 = total - discount + shipping
    payment_method   payment_method,
    coupon_id        BIGINT          REFERENCES coupons(id),
    address_snapshot JSONB,                              -- 下单时快照
    remark           TEXT,
    paid_at          TIMESTAMP,
    shipped_at       TIMESTAMP,
    delivered_at     TIMESTAMP,
    cancelled_at     TIMESTAMP,
    cancel_reason    VARCHAR(200),
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  orders                  IS '订单主表';
COMMENT ON COLUMN orders.order_no         IS '订单编号，如 ORD20240101001';
COMMENT ON COLUMN orders.total_amount     IS '商品总价（元）';
COMMENT ON COLUMN orders.discount_amount  IS '优惠减免金额';
COMMENT ON COLUMN orders.pay_amount       IS '用户实际支付金额';
COMMENT ON COLUMN orders.payment_method   IS '支付方式';
COMMENT ON COLUMN orders.address_snapshot IS '下单时收货地址快照(JSON)';

CREATE INDEX idx_order_user     ON orders(user_id);
CREATE INDEX idx_order_status   ON orders(status);
CREATE INDEX idx_order_created  ON orders(created_at);
CREATE INDEX idx_order_paid     ON orders(paid_at);

-- 订单明细（子表）
CREATE TABLE order_items (
    id           BIGSERIAL      PRIMARY KEY,
    order_id     BIGINT         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   BIGINT         NOT NULL REFERENCES products(id),
    sku_id       BIGINT         NOT NULL REFERENCES product_skus(id),
    product_name VARCHAR(200)   NOT NULL,   -- 快照
    sku_spec     JSONB,                     -- 快照
    price        NUMERIC(10,2)  NOT NULL,   -- 下单时单价
    quantity     INT            NOT NULL DEFAULT 1,
    subtotal     NUMERIC(12,2)  NOT NULL    -- price * quantity
);
COMMENT ON TABLE  order_items              IS '订单明细表';
COMMENT ON COLUMN order_items.product_name IS '商品名称快照';
COMMENT ON COLUMN order_items.sku_spec     IS 'SKU规格快照';
COMMENT ON COLUMN order_items.subtotal     IS '小计金额 = 单价 × 数量';

CREATE INDEX idx_oi_order   ON order_items(order_id);
CREATE INDEX idx_oi_product ON order_items(product_id);
CREATE INDEX idx_oi_sku     ON order_items(sku_id);

-- 支付流水
CREATE TABLE payments (
    id               BIGSERIAL      PRIMARY KEY,
    order_id         BIGINT         NOT NULL REFERENCES orders(id),
    transaction_no   VARCHAR(64)    NOT NULL UNIQUE,   -- 第三方流水号
    payment_method   payment_method NOT NULL,
    amount           NUMERIC(12,2)  NOT NULL,
    status           VARCHAR(20)    NOT NULL DEFAULT 'success', -- success / failed / refunded
    paid_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    raw_response     JSONB                             -- 支付网关原始返回
);
COMMENT ON TABLE  payments                IS '支付流水表';
COMMENT ON COLUMN payments.transaction_no IS '第三方支付流水号';

CREATE INDEX idx_pay_order ON payments(order_id);

-- 物流信息
CREATE TABLE shipments (
    id              BIGSERIAL    PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES orders(id),
    carrier         VARCHAR(30)  NOT NULL,     -- 快递公司: 顺丰/圆通/中通 ...
    tracking_no     VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'in_transit', -- in_transit / delivered / returned
    shipped_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    delivered_at    TIMESTAMP,
    province_from   VARCHAR(30),
    province_to     VARCHAR(30)
);
COMMENT ON TABLE  shipments           IS '物流信息表';
COMMENT ON COLUMN shipments.carrier   IS '快递公司名称';
COMMENT ON COLUMN shipments.status    IS '物流状态: in_transit/delivered/returned';

CREATE INDEX idx_ship_order ON shipments(order_id);

-- 退款记录
CREATE TABLE refunds (
    id              BIGSERIAL      PRIMARY KEY,
    order_id        BIGINT         NOT NULL REFERENCES orders(id),
    user_id         BIGINT         NOT NULL REFERENCES users(id),
    refund_no       VARCHAR(30)    NOT NULL UNIQUE,
    amount          NUMERIC(12,2)  NOT NULL,
    reason          VARCHAR(200)   NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'pending',  -- pending / approved / rejected / completed
    approved_at     TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  refunds        IS '退款记录表';
COMMENT ON COLUMN refunds.status IS '退款状态: pending/approved/rejected/completed';

CREATE INDEX idx_refund_order ON refunds(order_id);
CREATE INDEX idx_refund_user  ON refunds(user_id);

-- ============================================================
-- 6. 评价体系
-- ============================================================

CREATE TABLE reviews (
    id           BIGSERIAL   PRIMARY KEY,
    order_id     BIGINT      NOT NULL REFERENCES orders(id),
    product_id   BIGINT      NOT NULL REFERENCES products(id),
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    rating       SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content      TEXT,
    image_urls   TEXT[],                   -- 晒图URL数组
    is_anonymous BOOLEAN     NOT NULL DEFAULT FALSE,
    likes_count  INT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  reviews            IS '商品评价表';
COMMENT ON COLUMN reviews.rating     IS '评分 1-5 星';
COMMENT ON COLUMN reviews.image_urls IS '晒图URL列表';

CREATE INDEX idx_review_product ON reviews(product_id);
CREATE INDEX idx_review_user    ON reviews(user_id);
CREATE INDEX idx_review_rating  ON reviews(rating);

-- ============================================================
-- 7. 客服工单
-- ============================================================

CREATE TABLE support_tickets (
    id           BIGSERIAL       PRIMARY KEY,
    ticket_no    VARCHAR(30)     NOT NULL UNIQUE,
    user_id      BIGINT          NOT NULL REFERENCES users(id),
    order_id     BIGINT          REFERENCES orders(id),
    subject      VARCHAR(200)    NOT NULL,
    description  TEXT            NOT NULL,
    status       ticket_status   NOT NULL DEFAULT 'open',
    priority     ticket_priority NOT NULL DEFAULT 'medium',
    assigned_to  VARCHAR(50),              -- 客服人员名
    resolved_at  TIMESTAMP,
    created_at   TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP       NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  support_tickets             IS '客服工单表';
COMMENT ON COLUMN support_tickets.assigned_to IS '处理客服名';
COMMENT ON COLUMN support_tickets.priority    IS '优先级: low/medium/high/urgent';

CREATE INDEX idx_ticket_user   ON support_tickets(user_id);
CREATE INDEX idx_ticket_status ON support_tickets(status);

-- ============================================================
-- 8. 运营数据 —— 页面访问日志（大表，适合测试聚合）
-- ============================================================

CREATE TABLE page_views (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       REFERENCES users(id),  -- NULL = 游客
    session_id   VARCHAR(64)  NOT NULL,
    page_type    VARCHAR(30)  NOT NULL,  -- home / category / product / cart / checkout / search
    page_url     TEXT         NOT NULL,
    referrer     TEXT,
    product_id   BIGINT       REFERENCES products(id),
    duration_sec INT,                    -- 页面停留秒数
    device_type  VARCHAR(20),            -- mobile / desktop / tablet
    os           VARCHAR(30),
    browser      VARCHAR(30),
    ip_address   INET,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  page_views              IS '页面访问日志（运营分析用）';
COMMENT ON COLUMN page_views.page_type    IS '页面类型: home/category/product/cart/checkout/search';
COMMENT ON COLUMN page_views.device_type  IS '设备类型: mobile/desktop/tablet';
COMMENT ON COLUMN page_views.duration_sec IS '页面停留时长（秒）';

CREATE INDEX idx_pv_user      ON page_views(user_id);
CREATE INDEX idx_pv_product   ON page_views(product_id);
CREATE INDEX idx_pv_created   ON page_views(created_at);
CREATE INDEX idx_pv_page_type ON page_views(page_type);

-- ============================================================
-- 9. 库存变动流水
-- ============================================================

CREATE TABLE inventory_logs (
    id          BIGSERIAL    PRIMARY KEY,
    sku_id      BIGINT       NOT NULL REFERENCES product_skus(id),
    change_qty  INT          NOT NULL,   -- 正=入库, 负=出库
    reason      VARCHAR(30)  NOT NULL,   -- purchase / sale / return / adjustment
    reference_no VARCHAR(50),            -- 关联单号
    operator    VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  inventory_logs             IS '库存变动流水表';
COMMENT ON COLUMN inventory_logs.change_qty  IS '变动数量，正数=入库，负数=出库';
COMMENT ON COLUMN inventory_logs.reason      IS '变动原因: purchase/sale/return/adjustment';

CREATE INDEX idx_inv_sku     ON inventory_logs(sku_id);
CREATE INDEX idx_inv_created ON inventory_logs(created_at);

-- ============================================================
-- 10. 供应商 & 采购
-- ============================================================

CREATE TABLE suppliers (
    id           SERIAL       PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    contact_name VARCHAR(50),
    phone        VARCHAR(20),
    email        VARCHAR(120),
    province     VARCHAR(30),
    city         VARCHAR(30),
    address      TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE suppliers IS '供应商表';

CREATE TABLE purchase_orders (
    id              BIGSERIAL      PRIMARY KEY,
    po_no           VARCHAR(30)    NOT NULL UNIQUE,
    supplier_id     INT            NOT NULL REFERENCES suppliers(id),
    total_amount    NUMERIC(12,2)  NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'draft', -- draft / submitted / received / cancelled
    expected_date   DATE,
    received_date   DATE,
    created_by      VARCHAR(50),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  purchase_orders        IS '采购订单表';
COMMENT ON COLUMN purchase_orders.status IS '状态: draft/submitted/received/cancelled';

CREATE TABLE purchase_order_items (
    id              BIGSERIAL      PRIMARY KEY,
    po_id           BIGINT         NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    sku_id          BIGINT         NOT NULL REFERENCES product_skus(id),
    quantity        INT            NOT NULL,
    unit_price      NUMERIC(10,2)  NOT NULL,
    subtotal        NUMERIC(12,2)  NOT NULL
);
COMMENT ON TABLE purchase_order_items IS '采购订单明细';

CREATE INDEX idx_poi_po  ON purchase_order_items(po_id);
CREATE INDEX idx_poi_sku ON purchase_order_items(sku_id);

-- ============================================================
-- 11. 数据统计汇总表（预聚合，测试简单查和跨表对比）
-- ============================================================

CREATE TABLE daily_sales_summary (
    id              BIGSERIAL      PRIMARY KEY,
    report_date     DATE           NOT NULL,
    category_id     INT            REFERENCES categories(id),
    order_count     INT            NOT NULL DEFAULT 0,
    total_revenue   NUMERIC(14,2)  NOT NULL DEFAULT 0,
    total_discount  NUMERIC(14,2)  NOT NULL DEFAULT 0,
    refund_count    INT            NOT NULL DEFAULT 0,
    refund_amount   NUMERIC(14,2)  NOT NULL DEFAULT 0,
    avg_order_value NUMERIC(10,2),
    new_users       INT            NOT NULL DEFAULT 0,
    UNIQUE(report_date, category_id)
);
COMMENT ON TABLE  daily_sales_summary               IS '每日销售汇总表';
COMMENT ON COLUMN daily_sales_summary.report_date    IS '统计日期';
COMMENT ON COLUMN daily_sales_summary.order_count    IS '订单数';
COMMENT ON COLUMN daily_sales_summary.total_revenue  IS '总营收（元）';
COMMENT ON COLUMN daily_sales_summary.avg_order_value IS '平均客单价';

CREATE INDEX idx_dss_date ON daily_sales_summary(report_date);

-- ============================================================
-- 12. 生成测试数据
-- ============================================================

-- ---------- 用户 (200条) ----------
INSERT INTO users (username, email, phone, gender, birth_date, member_level_id, points, province, city, registered_at, last_login_at)
SELECT
    'user_' || i,
    'user_' || i || '@test.com',
    '138' || LPAD((random() * 99999999)::INT::TEXT, 8, '0'),
    (ARRAY['M','F','OTHER']::gender_enum[])[1 + (random()*2)::INT],
    '1985-01-01'::DATE + (random() * 14000)::INT,
    1 + (random() * 3)::INT,
    (random() * 30000)::INT,
    (ARRAY['北京','上海','广东','浙江','江苏','四川','湖北','山东','河南','福建'])[1 + (random()*9)::INT],
    (ARRAY['北京','上海','广州','杭州','南京','成都','武汉','济南','郑州','福州',
           '深圳','苏州','宁波','青岛','厦门','天津','重庆','西安','长沙','昆明'])[1 + (random()*19)::INT],
    NOW() - (random() * 730 || ' days')::INTERVAL,
    NOW() - (random() * 30 || ' days')::INTERVAL
FROM generate_series(1, 200) AS i;

-- ---------- 收货地址 (约400条) ----------
INSERT INTO user_addresses (user_id, label, receiver, phone, province, city, district, street, is_default)
SELECT
    u.id,
    (ARRAY['家','公司','学校','其他'])[1 + (random()*3)::INT],
    u.username || '_收件人',
    u.phone,
    u.province,
    u.city,
    '某区',
    '测试街道' || (random()*100)::INT || '号',
    (n = 1)
FROM users u, generate_series(1, 2) AS n;

-- ---------- 商品SPU (80条) ----------
INSERT INTO products (name, brand_id, category_id, description, is_on_sale, created_at)
SELECT p.name, p.brand_id, p.category_id, '优质商品，正品保障', random() > 0.1,
       NOW() - (random() * 365 || ' days')::INTERVAL
FROM (VALUES
    ('iPhone 15 Pro Max',       1,  100),
    ('iPhone 15',               1,  100),
    ('华为Mate 60 Pro',         2,  101),
    ('华为P70',                 2,  101),
    ('小米14 Ultra',            3,  101),
    ('小米14',                  3,  101),
    ('Redmi K70',               3,  101),
    ('联想拯救者Y9000P',        4,  110),
    ('联想小新Pro16',           4,  111),
    ('ThinkPad X1 Carbon',      4,  111),
    ('AirPods Pro 3',           1,  120),
    ('华为FreeBuds Pro 3',      2,  120),
    ('小米Buds 4 Pro',          3,  120),
    ('索尼WH-1000XM5',          9,  120),
    ('索尼WF-1000XM5',          9,  120),
    ('耐克Air Max 90',          5,  220),
    ('耐克Zoom Fly 5',          5,  220),
    ('阿迪Ultraboost 23',       6,  220),
    ('阿迪Superstar',           6,  220),
    ('优衣库圆领T恤',           7,  200),
    ('优衣库速干POLO衫',        7,  200),
    ('ZARA碎花连衣裙',          10, 210),
    ('ZARA西装外套',            10, 210),
    ('三只松鼠坚果大礼包',      8,  30),
    ('三只松鼠夏威夷果',        8,  30),
    ('华为MateBook X Pro',      2,  111),
    ('小米笔记本Pro 16',        3,  111),
    ('联想YOGA Pro 14s',        4,  111),
    ('MacBook Air M3',          1,  111),
    ('MacBook Pro M3',          1,  111),
    ('耐克Air Force 1',         5,  220),
    ('阿迪Stan Smith',          6,  220),
    ('优衣库防晒衣',            7,  200),
    ('ZARA牛仔裤',              10, 200),
    ('三只松鼠每日坚果',        8,  30),
    ('华为MatePad Pro',         2,  101),
    ('小米平板6 Pro',           3,  101),
    ('iPad Air M2',             1,  100),
    ('AirPods Max',             1,  120),
    ('Beats Studio Pro',        1,  120),
    ('耐克Dunk Low',            5,  220),
    ('阿迪Samba',               6,  220),
    ('优衣库法兰绒衬衫',        7,  200),
    ('ZARA针织衫',              10, 210),
    ('三只松鼠手撕面包',        8,  30),
    ('华为Watch GT4',           2,  101),
    ('小米手环8',               3,  101),
    ('Apple Watch S9',          1,  100),
    ('索尼A7M4',                9,  120),
    ('索尼ZV-E10',              9,  120),
    ('耐克篮球鞋KD16',          5,  220),
    ('阿迪哈登8',               6,  220),
    ('优衣库摇粒绒',            7,  200),
    ('ZARA皮革包',              10, 210),
    ('三只松鼠辣条',            8,  30),
    ('华为Sound X',             2,  120),
    ('小米音箱Pro',             3,  120),
    ('HomePod mini',            1,  120),
    ('Bose QC45',               9,  120),
    ('JBL Flip 6',              9,  120),
    ('耐克跑步短裤',            5,  200),
    ('阿迪运动裤',              6,  200),
    ('优衣库牛仔裤',            7,  200),
    ('ZARA风衣',                10, 210),
    ('三只松鼠果干',            8,  30),
    ('华为路由器AX3',           2,  101),
    ('小米路由器BE7000',        3,  101),
    ('iPhone 15 Pro',           1,  100),
    ('Pixel 8 Pro',             1,  101),
    ('Galaxy S24 Ultra',        9,  101),
    ('耐克足球鞋',              5,  220),
    ('阿迪Predator',            6,  220),
    ('优衣库羽绒服',            7,  200),
    ('ZARA大衣',                10, 210),
    ('三只松鼠鸭脖',            8,  30),
    ('华为畅享70',              2,  101),
    ('小米Civi 4',              3,  101),
    ('耐克拖鞋Calm',            5,  220),
    ('阿迪三叶草卫衣',          6,  200),
    ('三只松鼠蟹黄锅巴',        8,  30)
) AS p(name, brand_id, category_id);

-- ---------- SKU (约240条，每个SPU 2-4个) ----------
INSERT INTO product_skus (product_id, sku_code, spec_json, price, cost_price, stock, sales_count, weight_kg)
SELECT
    p.id,
    'SKU-' || p.id || '-' || v.n,
    CASE
        WHEN p.category_id IN (100,101) THEN jsonb_build_object('颜色', (ARRAY['黑色','白色','星光色'])[v.n], '存储', (ARRAY['128GB','256GB','512GB'])[v.n])
        WHEN p.category_id IN (110,111) THEN jsonb_build_object('配置', (ARRAY['i5/16G/512G','i7/16G/1T','i9/32G/1T'])[v.n])
        WHEN p.category_id = 120 THEN jsonb_build_object('颜色', (ARRAY['黑色','白色','蓝色'])[v.n])
        WHEN p.category_id IN (200,210) THEN jsonb_build_object('尺码', (ARRAY['S','M','L','XL'])[v.n], '颜色', (ARRAY['黑','白','灰','蓝'])[v.n])
        WHEN p.category_id = 220 THEN jsonb_build_object('尺码', (ARRAY['39','41','43'])[v.n], '颜色', (ARRAY['黑白','纯白','灰蓝'])[v.n])
        ELSE jsonb_build_object('规格', (ARRAY['标准装','大份装','家庭装'])[v.n])
    END,
    CASE
        WHEN p.category_id IN (100,101) THEN 3999 + v.n * 1500 + (random()*500)::INT
        WHEN p.category_id IN (110,111) THEN 5999 + v.n * 2000 + (random()*800)::INT
        WHEN p.category_id = 120 THEN 299 + v.n * 200 + (random()*100)::INT
        WHEN p.category_id IN (200,210) THEN 99 + v.n * 50 + (random()*30)::INT
        WHEN p.category_id = 220 THEN 399 + v.n * 100 + (random()*80)::INT
        ELSE 19.9 + v.n * 10 + (random()*15)::INT
    END,
    CASE
        WHEN p.category_id IN (100,101) THEN 2800 + v.n * 1000
        WHEN p.category_id IN (110,111) THEN 4200 + v.n * 1400
        WHEN p.category_id = 120 THEN 150 + v.n * 80
        WHEN p.category_id IN (200,210) THEN 30 + v.n * 15
        WHEN p.category_id = 220 THEN 200 + v.n * 50
        ELSE 8 + v.n * 3
    END,
    (random() * 500)::INT + 10,
    (random() * 2000)::INT,
    round((random() * 3 + 0.1)::NUMERIC, 2)
FROM products p, generate_series(1, 3) AS v(n);

-- ---------- 优惠券 (15条) ----------
INSERT INTO coupons (code, name, type, discount_value, min_purchase, max_discount, total_quota, used_count, start_time, end_time, category_id) VALUES
('WELCOME50',   '新人满100减50',    'fixed',   50,    100, NULL, 10000, 3200, NOW() - INTERVAL '60 days', NOW() + INTERVAL '30 days', NULL),
('DIGITAL100',  '数码满2000减100',  'fixed',   100,   2000, NULL, 5000, 1800, NOW() - INTERVAL '30 days', NOW() + INTERVAL '60 days', 1),
('FASHION15',   '服饰85折',         'percent', 15,    200,  100,  8000, 2100, NOW() - INTERVAL '15 days', NOW() + INTERVAL '45 days', 2),
('FOOD20',      '食品满50减20',     'fixed',   20,    50,  NULL,  20000, 8900, NOW() - INTERVAL '90 days', NOW() + INTERVAL '10 days', 3),
('DOUBLE11_200','双11满1000减200',  'fixed',   200,   1000, NULL, 50000, 32000, '2024-11-01', '2024-11-12', NULL),
('SUMMER30',    '夏季8折券',        'percent', 20,    150,  80,   15000, 6700, NOW() - INTERVAL '45 days', NOW() + INTERVAL '15 days', 2),
('VIP_ONLY',    'VIP专享95折',      'percent', 5,     0,    500,  2000,  450, NOW() - INTERVAL '30 days', NOW() + INTERVAL '90 days', NULL),
('NEWYEAR88',   '新年满500减88',    'fixed',   88,    500, NULL,  30000, 15000, '2025-01-01', '2025-02-15', NULL),
('PHONE300',    '手机满5000减300',  'fixed',   300,   5000, NULL, 3000, 980, NOW() - INTERVAL '20 days', NOW() + INTERVAL '40 days', 10),
('SHOE50',      '运动鞋满300减50',  'fixed',   50,    300, NULL,  10000, 4200, NOW() - INTERVAL '10 days', NOW() + INTERVAL '50 days', 22),
('SNACK10',     '零食满30减10',     'fixed',   10,    30,  NULL,  50000, 28000, NOW() - INTERVAL '60 days', NOW() + INTERVAL '30 days', 30),
('LAPTOP500',   '笔记本满8000减500','fixed',   500,   8000, NULL, 2000,  600, NOW() - INTERVAL '15 days', NOW() + INTERVAL '45 days', 11),
('EARPHONE30',  '耳机满200减30',    'fixed',   30,    200, NULL,  8000, 3100, NOW() - INTERVAL '25 days', NOW() + INTERVAL '35 days', 12),
('WEEKEND10',   '周末全场9折',      'percent', 10,    100,  200,  100000, 45000, NOW() - INTERVAL '90 days', NOW() + INTERVAL '90 days', NULL),
('FREE_SHIP',   '免邮券',           'fixed',   15,    0,   NULL,  200000, 98000, NOW() - INTERVAL '180 days', NOW() + INTERVAL '180 days', NULL);

-- ---------- 用户领券 (约600条) ----------
INSERT INTO user_coupons (user_id, coupon_id, is_used, claimed_at)
SELECT
    (random() * 199 + 1)::BIGINT,
    (random() * 14 + 1)::BIGINT,
    random() > 0.6,
    NOW() - (random() * 60 || ' days')::INTERVAL
FROM generate_series(1, 600);

-- ---------- 订单 (1500条) ----------
INSERT INTO orders (order_no, user_id, status, total_amount, discount_amount, shipping_fee, pay_amount, payment_method, paid_at, shipped_at, delivered_at, cancelled_at, cancel_reason, created_at)
SELECT
    'ORD' || TO_CHAR(NOW() - (random()*365 || ' days')::INTERVAL, 'YYYYMMDD') || LPAD(i::TEXT, 5, '0'),
    (random() * 199 + 1)::BIGINT,
    (ARRAY['pending','paid','shipped','delivered','delivered','delivered','cancelled','refunded']::order_status[])[1 + (random()*7)::INT],
    round((random() * 5000 + 50)::NUMERIC, 2),
    round((random() * 200)::NUMERIC, 2),
    CASE WHEN random() > 0.7 THEN round((random() * 15)::NUMERIC, 2) ELSE 0 END,
    0,  -- 会在下面更新
    (ARRAY['alipay','wechat','credit_card','bank_transfer','cash_on_delivery']::payment_method[])[1 + (random()*4)::INT],
    CASE WHEN random() > 0.15 THEN NOW() - (random()*360 || ' days')::INTERVAL ELSE NULL END,
    CASE WHEN random() > 0.3 THEN NOW() - (random()*350 || ' days')::INTERVAL ELSE NULL END,
    CASE WHEN random() > 0.4 THEN NOW() - (random()*340 || ' days')::INTERVAL ELSE NULL END,
    CASE WHEN random() > 0.85 THEN NOW() - (random()*350 || ' days')::INTERVAL ELSE NULL END,
    CASE WHEN random() > 0.85 THEN (ARRAY['不想要了','买错了','价格太贵','发货太慢','商品缺货'])[1 + (random()*4)::INT] ELSE NULL END,
    NOW() - (random() * 365 || ' days')::INTERVAL
FROM generate_series(1, 1500) AS i;

-- 修正 pay_amount
UPDATE orders SET pay_amount = GREATEST(total_amount - discount_amount + shipping_fee, 0);

-- ---------- 订单明细 (约3500条，每个订单1-4个商品) ----------
INSERT INTO order_items (order_id, product_id, sku_id, product_name, sku_spec, price, quantity, subtotal)
SELECT
    o.id,
    s.product_id,
    s.id,
    p.name,
    s.spec_json,
    s.price,
    1 + (random() * 3)::INT,
    0  -- 先占位
FROM orders o
CROSS JOIN LATERAL (
    SELECT id, product_id, spec_json, price
    FROM product_skus
    ORDER BY random()
    LIMIT 1 + (random() * 2)::INT
) s
JOIN products p ON p.id = s.product_id;

-- 修正 subtotal
UPDATE order_items SET subtotal = price * quantity;

-- ---------- 支付流水 ----------
INSERT INTO payments (order_id, transaction_no, payment_method, amount, status, paid_at)
SELECT
    id,
    'TXN' || LPAD(id::TEXT, 10, '0') || (random()*999)::INT,
    payment_method,
    pay_amount,
    CASE status
        WHEN 'cancelled' THEN 'failed'
        WHEN 'refunded' THEN 'refunded'
        ELSE 'success'
    END,
    COALESCE(paid_at, created_at)
FROM orders
WHERE status != 'pending';

-- ---------- 物流 ----------
INSERT INTO shipments (order_id, carrier, tracking_no, status, shipped_at, delivered_at, province_from, province_to)
SELECT
    o.id,
    (ARRAY['顺丰','圆通','中通','韵达','申通','极兔','京东物流'])[1 + (random()*6)::INT],
    'SF' || LPAD(o.id::TEXT, 12, '0'),
    CASE o.status
        WHEN 'delivered' THEN 'delivered'
        WHEN 'refunded' THEN 'returned'
        ELSE 'in_transit'
    END,
    COALESCE(o.shipped_at, o.created_at + INTERVAL '1 day'),
    o.delivered_at,
    (ARRAY['广东','浙江','上海','江苏','北京'])[1 + (random()*4)::INT],
    (SELECT province FROM users WHERE users.id = o.user_id)
FROM orders o
WHERE o.status IN ('shipped', 'delivered', 'refunded');

-- ---------- 退款 ----------
INSERT INTO refunds (order_id, user_id, refund_no, amount, reason, status, created_at)
SELECT
    o.id,
    o.user_id,
    'RF' || LPAD(o.id::TEXT, 8, '0'),
    round(o.pay_amount * (0.5 + random() * 0.5), 2),
    (ARRAY['质量问题','尺码不合适','不喜欢','商品与描述不符','物流破损'])[1 + (random()*4)::INT],
    (ARRAY['pending','approved','completed','completed'])[1 + (random()*3)::INT],
    o.created_at + (random() * 7 || ' days')::INTERVAL
FROM orders o
WHERE o.status = 'refunded';

-- ---------- 评价 (约800条) ----------
INSERT INTO reviews (order_id, product_id, user_id, rating, content, is_anonymous, likes_count, created_at)
SELECT
    o.id,
    oi.product_id,
    o.user_id,
    1 + (random() * 4)::INT,
    (ARRAY[
        '非常好用，推荐购买！',
        '质量不错，物流也很快',
        '一般般，没有想象中那么好',
        '包装精美，送人很合适',
        '性价比很高，值得入手',
        '有点贵，但质量确实好',
        '第二次购买了，一如既往的好',
        '颜色和图片一样，很满意',
        '尺码偏大，建议买小一号',
        '用了一周感觉不太行',
        '客服态度很好，处理很快',
        '发货速度快，隔天就到了'
    ])[1 + (random()*11)::INT],
    random() > 0.8,
    (random() * 100)::INT,
    o.created_at + (random() * 14 || ' days')::INTERVAL
FROM orders o
JOIN LATERAL (
    SELECT product_id FROM order_items WHERE order_id = o.id LIMIT 1
) oi ON TRUE
WHERE o.status = 'delivered'
LIMIT 800;

-- ---------- 客服工单 (200条) ----------
INSERT INTO support_tickets (ticket_no, user_id, order_id, subject, description, status, priority, assigned_to, created_at)
SELECT
    'TK' || LPAD(i::TEXT, 8, '0'),
    (random() * 199 + 1)::BIGINT,
    CASE WHEN random() > 0.3 THEN (SELECT id FROM orders ORDER BY random() LIMIT 1) ELSE NULL END,
    (ARRAY['物流查询','退款申请','商品咨询','质量投诉','发票问题','账号异常','促销活动咨询','售后服务'])[1 + (random()*7)::INT],
    '用户遇到问题，需要客服协助处理',
    (ARRAY['open','in_progress','resolved','closed']::ticket_status[])[1 + (random()*3)::INT],
    (ARRAY['low','medium','medium','high','urgent']::ticket_priority[])[1 + (random()*4)::INT],
    (ARRAY['客服小王','客服小李','客服小张','客服小陈','客服小刘'])[1 + (random()*4)::INT],
    NOW() - (random() * 90 || ' days')::INTERVAL
FROM generate_series(1, 200) AS i;

-- ---------- 页面访问日志 (5000条) ----------
INSERT INTO page_views (user_id, session_id, page_type, page_url, product_id, duration_sec, device_type, os, browser, ip_address, created_at)
SELECT
    CASE WHEN random() > 0.2 THEN (random() * 199 + 1)::BIGINT ELSE NULL END,
    md5(random()::TEXT),
    (ARRAY['home','category','product','product','product','cart','checkout','search'])[1 + (random()*7)::INT],
    '/page/' || (random() * 1000)::INT,
    CASE WHEN random() > 0.4 THEN (random() * 79 + 1)::BIGINT ELSE NULL END,
    (random() * 300)::INT,
    (ARRAY['mobile','mobile','desktop','desktop','tablet'])[1 + (random()*4)::INT],
    (ARRAY['iOS','Android','Windows','macOS','Linux'])[1 + (random()*4)::INT],
    (ARRAY['Chrome','Safari','Firefox','Edge','微信内置'])[1 + (random()*4)::INT],
    ('192.168.' || (random()*255)::INT || '.' || (random()*255)::INT)::INET,
    NOW() - (random() * 30 || ' days')::INTERVAL
FROM generate_series(1, 5000);

-- ---------- 库存流水 (约700条) ----------
INSERT INTO inventory_logs (sku_id, change_qty, reason, reference_no, operator, created_at)
SELECT
    (random() * 239 + 1)::BIGINT,
    CASE WHEN random() > 0.4 THEN (random() * 100 + 1)::INT ELSE -(random() * 20 + 1)::INT END,
    (ARRAY['purchase','sale','sale','sale','return','adjustment'])[1 + (random()*5)::INT],
    'REF-' || (random() * 99999)::INT,
    (ARRAY['admin','warehouse_a','warehouse_b','system'])[1 + (random()*3)::INT],
    NOW() - (random() * 180 || ' days')::INTERVAL
FROM generate_series(1, 700);

-- ---------- 供应商 (10个) ----------
INSERT INTO suppliers (name, contact_name, phone, email, province, city) VALUES
('深圳华强电子供应链', '张经理', '13800001111', 'supplier1@test.com', '广东', '深圳'),
('杭州优品贸易', '王总', '13800002222', 'supplier2@test.com', '浙江', '杭州'),
('上海环球采购中心', '李经理', '13800003333', 'supplier3@test.com', '上海', '上海'),
('广州百货批发', '陈总', '13800004444', 'supplier4@test.com', '广东', '广州'),
('成都西部物资', '刘经理', '13800005555', 'supplier5@test.com', '四川', '成都'),
('北京中关村供应商', '赵经理', '13800006666', 'supplier6@test.com', '北京', '北京'),
('武汉光谷科技供应链', '周总', '13800007777', 'supplier7@test.com', '湖北', '武汉'),
('南京紫金贸易', '吴经理', '13800008888', 'supplier8@test.com', '江苏', '南京'),
('福州海峡进出口', '郑总', '13800009999', 'supplier9@test.com', '福建', '福州'),
('济南泉城百货', '孙经理', '13800000000', 'supplier10@test.com', '山东', '济南');

-- ---------- 采购单 (30条) ----------
INSERT INTO purchase_orders (po_no, supplier_id, total_amount, status, expected_date, created_by, created_at)
SELECT
    'PO' || TO_CHAR(NOW() - (random()*180 || ' days')::INTERVAL, 'YYYYMMDD') || LPAD(i::TEXT, 4, '0'),
    1 + (random() * 9)::INT,
    round((random() * 100000 + 5000)::NUMERIC, 2),
    (ARRAY['draft','submitted','submitted','received','received','received','cancelled'])[1 + (random()*6)::INT],
    (NOW() + (random() * 30 || ' days')::INTERVAL)::DATE,
    (ARRAY['采购员A','采购员B','采购员C'])[1 + (random()*2)::INT],
    NOW() - (random() * 180 || ' days')::INTERVAL
FROM generate_series(1, 30) AS i;

-- ---------- 采购明细 ----------
INSERT INTO purchase_order_items (po_id, sku_id, quantity, unit_price, subtotal)
SELECT
    po.id,
    (random() * 239 + 1)::BIGINT,
    (random() * 200 + 10)::INT,
    round((random() * 3000 + 10)::NUMERIC, 2),
    0
FROM purchase_orders po, generate_series(1, 3);

UPDATE purchase_order_items SET subtotal = unit_price * quantity;

-- ---------- 每日销售汇总 (最近90天 × 4个一级分类) ----------
INSERT INTO daily_sales_summary (report_date, category_id, order_count, total_revenue, total_discount, refund_count, refund_amount, avg_order_value, new_users)
SELECT
    d::DATE,
    c.id,
    (random() * 50 + 5)::INT,
    round((random() * 80000 + 5000)::NUMERIC, 2),
    round((random() * 8000 + 500)::NUMERIC, 2),
    (random() * 5)::INT,
    round((random() * 3000)::NUMERIC, 2),
    round((random() * 500 + 80)::NUMERIC, 2),
    (random() * 20)::INT
FROM generate_series(NOW() - INTERVAL '90 days', NOW(), INTERVAL '1 day') AS d
CROSS JOIN (SELECT id FROM categories WHERE level = 1) AS c;

-- ============================================================
-- 13. 创建几个实用视图（方便测试跨表查询能力）
-- ============================================================

-- 订单全貌视图
CREATE VIEW v_order_detail AS
SELECT
    o.id AS order_id,
    o.order_no,
    o.created_at AS order_date,
    o.status AS order_status,
    o.total_amount,
    o.discount_amount,
    o.pay_amount,
    o.payment_method,
    u.id AS user_id,
    u.username,
    u.province AS user_province,
    u.city AS user_city,
    ml.name AS member_level,
    oi.product_name,
    oi.sku_spec,
    oi.price AS item_price,
    oi.quantity,
    oi.subtotal AS item_subtotal,
    p.id AS product_id,
    b.name AS brand_name,
    c3.name AS category_l3,
    c2.name AS category_l2,
    c1.name AS category_l1
FROM orders o
JOIN users u ON u.id = o.user_id
JOIN member_levels ml ON ml.id = u.member_level_id
JOIN order_items oi ON oi.order_id = o.id
JOIN products p ON p.id = oi.product_id
LEFT JOIN brands b ON b.id = p.brand_id
JOIN categories c3 ON c3.id = p.category_id
LEFT JOIN categories c2 ON c2.id = c3.parent_id
LEFT JOIN categories c1 ON c1.id = c2.parent_id;

COMMENT ON VIEW v_order_detail IS '订单全貌视图：关联用户、商品、品牌、分类';

-- 用户消费统计视图
CREATE VIEW v_user_stats AS
SELECT
    u.id AS user_id,
    u.username,
    u.province,
    u.city,
    u.gender::TEXT AS gender,
    ml.name AS member_level,
    u.points,
    u.registered_at,
    COUNT(DISTINCT o.id) AS total_orders,
    COALESCE(SUM(o.pay_amount), 0) AS total_spent,
    COALESCE(AVG(o.pay_amount), 0) AS avg_order_value,
    MAX(o.created_at) AS last_order_date,
    COUNT(DISTINCT CASE WHEN o.status = 'refunded' THEN o.id END) AS refund_orders
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
JOIN member_levels ml ON ml.id = u.member_level_id
GROUP BY u.id, u.username, u.province, u.city, u.gender, ml.name, u.points, u.registered_at;

COMMENT ON VIEW v_user_stats IS '用户消费统计视图：订单数、总消费、退款数等';

-- ============================================================
-- 完成！
-- ============================================================

-- 统计摘要
DO $$
DECLARE
    r RECORD;
BEGIN
    RAISE NOTICE '====== 测试数据库创建完成 ======';
    FOR r IN
        SELECT schemaname, relname, n_live_tup
        FROM pg_stat_user_tables
        WHERE schemaname = 'ecommerce'
        ORDER BY relname
    LOOP
        RAISE NOTICE '  % : ~% rows', r.relname, r.n_live_tup;
    END LOOP;
END $$;
