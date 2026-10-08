-- ============================================================
-- badfisher_temu 数据库初始化脚本
-- 部署时执行此文件，创建所有表结构
-- ============================================================

CREATE DATABASE IF NOT EXISTS `badfisher_temu` DEFAULT CHARACTER SET utf8mb4;
USE `badfisher_temu`;

-- ============================================================
-- 1. 基础设施
-- ============================================================

-- Temu 授权Token表（含店铺信息、渠道、站点）
CREATE TABLE IF NOT EXISTS `tb_temu_access_token` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_id`                  VARCHAR(64)  NOT NULL COMMENT '公司主体简写',
    `channel_id`              INT          DEFAULT NULL COMMENT '中台渠道编号',
    `app_key`                 VARCHAR(128) DEFAULT NULL COMMENT 'Temu App Key（用于API签名）',
    `app_secret`              VARCHAR(256) DEFAULT NULL COMMENT 'Temu App Secret（用于MD5签名）',
    `seller_id`               VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID',
    `shop_name`               VARCHAR(128) DEFAULT NULL COMMENT '店铺名称',
    `site`                    VARCHAR(8)   DEFAULT NULL COMMENT '站点区域(US/AU/CN)',
    `auth_region`             VARCHAR(16)  NOT NULL COMMENT '授权/API路由区域(US/GLOBAL/EU/CN；type=0固定CN)',
    `type`                    TINYINT      NOT NULL DEFAULT 1 COMMENT '类型(0=商品, 1=订单)',
    `access_token`            VARCHAR(512) NOT NULL COMMENT 'access_token',
    `expire_days`             INT          NOT NULL DEFAULT 320 COMMENT 'Token有效天数',
    `del_flag`                TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`             DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `expire_time`             DATETIME     DEFAULT NULL COMMENT 'Token过期日期',
    `create_user_id`          VARCHAR(32)  DEFAULT NULL COMMENT '创建人ID',
    `update_time`             DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user_id`          VARCHAR(32)  DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    KEY `idx_seller_id` (`seller_id`),
    KEY `idx_auth_identity` (`app_id`, `seller_id`, `auth_region`, `type`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu授权Token表（含店铺、渠道、站点信息）';


-- API 日志表
CREATE TABLE IF NOT EXISTS `tb_temu_api_log` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_id`          VARCHAR(64)  DEFAULT NULL COMMENT 'Temu App ID',
    `api_name`        VARCHAR(128) DEFAULT NULL COMMENT 'API名称',
    `api_url`         VARCHAR(512) DEFAULT NULL COMMENT '请求URL',
    `http_method`     VARCHAR(16)  DEFAULT NULL COMMENT '请求方法',
    `status_code`     INT          DEFAULT NULL COMMENT 'HTTP状态码',
    `error_code`      VARCHAR(64)  DEFAULT NULL COMMENT '平台错误码',
    `error_message`   TEXT         DEFAULT NULL COMMENT '错误信息',
    `request_body`    MEDIUMTEXT   DEFAULT NULL COMMENT '请求体JSON',
    `response_body`   MEDIUMTEXT   DEFAULT NULL COMMENT '响应体JSON',
    `cost_time_ms`    BIGINT       DEFAULT NULL COMMENT '耗时(ms)',
    `seller_id`       VARCHAR(32)  DEFAULT NULL COMMENT '卖家ID',
    `success`         TINYINT      DEFAULT 1 COMMENT '是否成功(1=成功,0=失败)',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_app_id` (`app_id`),
    KEY `idx_api_name` (`api_name`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API日志表';

-- ============================================================
-- 2. 订单模块（Order）
-- ============================================================

-- Temu父订单表
CREATE TABLE IF NOT EXISTS `tb_temu_parent_order` (
    `id`                                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_order_sn`                           VARCHAR(64)  NOT NULL COMMENT '父单号',
    `parent_order_status`                       VARCHAR(32)  DEFAULT NULL COMMENT '父单状态',
    `parent_order_time`                         BIGINT       DEFAULT NULL COMMENT '父单创建时间(epoch秒)',
    `parent_shipping_time`                      BIGINT       DEFAULT NULL COMMENT '父单发货时间(epoch秒)',
    `expect_ship_latest_time`                   BIGINT       DEFAULT NULL COMMENT '期望最晚发货时间(epoch秒)',
    `latest_delivery_time`                      BIGINT       DEFAULT NULL COMMENT '最晚送达时间(epoch秒)',
    `parent_confirm_time`                       BIGINT       DEFAULT NULL COMMENT '父单确认时间(epoch秒)',
    `parent_order_pending_finish_time`          BIGINT       DEFAULT NULL COMMENT '父单待完成时间(epoch秒)',
    `update_time_api`                           BIGINT       DEFAULT NULL COMMENT 'API返回的父单更新时间(epoch秒)',
    `shipping_method`                           INT          DEFAULT NULL COMMENT '发货方式',
    `order_payment_type`                        VARCHAR(16)  DEFAULT NULL COMMENT '订单支付类型(PPD等)',
    `is_shipment_consolidated_by_main_mall`     INT      DEFAULT NULL COMMENT '是否主仓合并发货(0=否,1=是)',
    `has_shipping_fee`                          INT      DEFAULT NULL COMMENT '是否含运费(0=否,1=是)',
    `region_id`                                 INT          DEFAULT NULL COMMENT '区域ID(美国=211)',
    `site_id`                                   INT          DEFAULT NULL COMMENT '站点ID',
    `seller_id`                                 VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID',
    `channel_id`                                INT          DEFAULT NULL COMMENT '中台渠道编号(ERP店铺唯一标识)',
    `site`                                      VARCHAR(8)   DEFAULT NULL COMMENT '站点(US/AU/CA等)',
    `buyer_name`                                VARCHAR(128) DEFAULT NULL COMMENT '买家名称',
    `del_flag`                                  TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`                               DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_user_id`                            VARCHAR(32)  DEFAULT NULL COMMENT '创建人ID',
    `update_time`                               DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user_id`                            VARCHAR(32)  DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_site_parent` (`channel_id`, `site`, `parent_order_sn`),
    KEY `idx_seller_site` (`seller_id`, `site`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu父订单表';

-- Temu数据拉取任务表（通用：订单/售后/库存等拉取任务的时间窗口与状态管理）
CREATE TABLE IF NOT EXISTS `tb_temu_order_request_task` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_id`          VARCHAR(64)  DEFAULT NULL COMMENT 'Temu App ID',
    `auth_region`     VARCHAR(16)  DEFAULT NULL COMMENT '授权/API路由区域(US/GLOBAL/EU/CN)',
    `site`            VARCHAR(8)   DEFAULT NULL COMMENT '站点(US/CN)',
    `channel_id`      INT          DEFAULT NULL COMMENT '中台渠道编号(ERP店铺唯一标识)',
    `account_code`    VARCHAR(64)  NOT NULL COMMENT '店铺编码',
    `pull_type`       VARCHAR(32)  NOT NULL DEFAULT 'ORDER' COMMENT '拉取类型: ORDER=订单, AFTERSALES=售后, STOCK=库存',
    `pull_status`     VARCHAR(8)   DEFAULT '0' COMMENT '拉取状态过滤: 与pullType联动, 0=全部(默认), 参见TemuOrderStatus枚举',
    `time_type`       VARCHAR(32)  DEFAULT 'lastmodifieddate' COMMENT '时间类型',
    `begin_time`      DATETIME     NOT NULL COMMENT '拉取开始时间',
    `end_time`        DATETIME     NOT NULL COMMENT '拉取结束时间',
    `state`           TINYINT      DEFAULT 0 COMMENT '任务状态: 0=未请求, 1=请求中, 2=完成, 3=失败',
    `execution_id`    VARCHAR(36)  DEFAULT NULL COMMENT '每次领取生成的新执行身份',
    `lease_expires_at` DATETIME(3)  DEFAULT NULL COMMENT '数据库时钟维护的执行租约截止时间',
    `error_message`   TEXT         DEFAULT NULL COMMENT '错误信息',
    `del_flag`        TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_channel_site_type_state` (`channel_id`, `site`, `pull_type`, `state`),
    KEY `idx_auth_task_state` (`app_id`, `account_code`, `auth_region`, `pull_type`, `state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu数据拉取任务表';

-- Temu子订单表
CREATE TABLE IF NOT EXISTS `tb_temu_children_order` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_order_sn`  VARCHAR(64)  NOT NULL COMMENT '父单号',
    `seller_id`        VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID/店铺编码',
    `channel_id`       INT          DEFAULT NULL COMMENT '中台渠道编号(ERP店铺唯一标识)',
    `site`             VARCHAR(8)   DEFAULT NULL COMMENT '站点(US/AU/CA等)',
    `order_sn`         VARCHAR(64)  NOT NULL COMMENT '子订单SN',
    `goods_id`         BIGINT       DEFAULT NULL COMMENT '商品SKU ID',
    `goods_name`       VARCHAR(512) DEFAULT NULL COMMENT '商品名称',
    `order_status`     INT          DEFAULT NULL COMMENT '订单状态(3=已取消)',
    `quantity`         INT          DEFAULT NULL COMMENT '商品数量',
    `sku_id`           BIGINT       DEFAULT NULL COMMENT '货品SKU ID',
    `spec`             VARCHAR(512) DEFAULT NULL COMMENT '商品规格描述',
    `thumb_url`        VARCHAR(512) DEFAULT NULL COMMENT '商品缩略图',
    `del_flag`         TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_site_parent_order` (`channel_id`, `site`, `parent_order_sn`, `order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu子订单表';

-- Temu订单地址表
CREATE TABLE IF NOT EXISTS `tb_temu_order_address` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_order_sn` VARCHAR(64)  NOT NULL COMMENT '父单号',
    `seller_id`       VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID/店铺编码',
    `channel_id`      INT          DEFAULT NULL COMMENT '中台渠道编号(ERP店铺唯一标识)',
    `site`            VARCHAR(8)   DEFAULT NULL COMMENT '站点(US/AU/CA等)',
    `receipt_name`    VARCHAR(128) DEFAULT NULL COMMENT '收货人姓名',
    `mobile`          VARCHAR(32)  DEFAULT NULL COMMENT '收货人手机号',
    `regionname1`     VARCHAR(128) DEFAULT NULL COMMENT '区域地址1',
    `regionname2`     VARCHAR(128) DEFAULT NULL COMMENT '区域地址2',
    `regionname3`     VARCHAR(128) DEFAULT NULL COMMENT '区域地址3',
    `addressline1`    VARCHAR(512) DEFAULT NULL COMMENT '详细地址行1',
    `addressline2`    VARCHAR(512) DEFAULT NULL COMMENT '详细地址行2',
    `post_code`       VARCHAR(32)  DEFAULT NULL COMMENT '邮编',
    `mail`            VARCHAR(128) DEFAULT NULL COMMENT 'Temu匿名邮箱',
    `country_code`    VARCHAR(8)   DEFAULT NULL COMMENT '国家标识',
    `del_flag`        TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_site_parent` (`channel_id`, `site`, `parent_order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu订单地址表';

-- Temu订单货品表（子订单关联的货品明细）
CREATE TABLE IF NOT EXISTS `tb_temu_order_product` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_order_sn`  VARCHAR(64)  NOT NULL COMMENT '父单号',
    `seller_id`        VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID/店铺编码',
    `channel_id`       INT          DEFAULT NULL COMMENT '中台渠道编号(ERP店铺唯一标识)',
    `site`             VARCHAR(8)   DEFAULT NULL COMMENT '站点(US/AU/CA等)',
    `children_order_no` VARCHAR(64) DEFAULT NULL COMMENT '子订单号',
    `ext_code`         VARCHAR(128) DEFAULT NULL COMMENT '货品编码',
    `product_id`       BIGINT       DEFAULT NULL COMMENT '货品ID',
    `product_sku_id`   BIGINT       DEFAULT NULL COMMENT '货品SKU ID',
    `sold_factor`      VARCHAR(32)  DEFAULT NULL COMMENT '商品与货品数量转换系数',
    `del_flag`         TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_site_parent_child_sku`
        (`channel_id`, `site`, `parent_order_sn`, `children_order_no`, `product_sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu订单货品表';

-- ============================================================
-- 3. 售后模块（Aftersales）
-- ============================================================

-- 3.1 父售后单表（bg.aftersales.parentaftersales.list.get 返回的父售后单记录）
CREATE TABLE IF NOT EXISTS `tb_temu_parent_aftersales` (
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_aftersales_sn`     VARCHAR(64)  NOT NULL COMMENT '父售后单号',
    `seller_id`                VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID/店铺编码',
    `channel_id`               INT          DEFAULT NULL COMMENT '中台渠道编号(ERP店铺唯一标识)',
    `site`                     VARCHAR(8)   DEFAULT NULL COMMENT '站点(US/AU/CA等)',
    `parent_order_sn`          VARCHAR(64)  DEFAULT NULL COMMENT '父订单号',
    `aftersales_status_group`  INT          DEFAULT NULL COMMENT '售后状态组',
    `parent_aftersales_status` INT          DEFAULT NULL COMMENT '父售后状态',
    `aftersales_type`          INT          DEFAULT NULL COMMENT '售后类型(1=仅退款, 2=退货退款)',
    `return_delivery_type`     INT          DEFAULT NULL COMMENT '退货配送类型',
    `create_time_api`          BIGINT       DEFAULT NULL COMMENT 'API返回的创建时间(epoch秒)',
    `update_time_api`          BIGINT       DEFAULT NULL COMMENT 'API返回的更新时间(epoch秒)',
    `del_flag`                 TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`              DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`              DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_site_parent_aftersales` (`channel_id`, `site`, `parent_aftersales_sn`),
    KEY `idx_channel_site_status` (`channel_id`, `site`, `parent_aftersales_status`),
    KEY `idx_parent_order_sn` (`parent_order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu父售后单表';

-- 3.2 子售后单表（bg.aftersales.aftersales.list.get 返回的子售后单记录）
CREATE TABLE IF NOT EXISTS `tb_temu_children_aftersales` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_aftersales_sn`  VARCHAR(64)  NOT NULL COMMENT '父售后单号',
    `aftersales_sn`         VARCHAR(64)  DEFAULT NULL COMMENT '子售后单号',
    `seller_id`             VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID/店铺编码',
    `channel_id`            INT          DEFAULT NULL COMMENT '中台渠道编号(ERP店铺唯一标识)',
    `site`                  VARCHAR(8)   DEFAULT NULL COMMENT '站点(US/AU/CA等)',
    `order_sn`              VARCHAR(64)  DEFAULT NULL COMMENT '子订单号',
    `aftersales_status`     INT          DEFAULT NULL COMMENT 'Temu子售后状态原始码',
    `aftersales_type`       INT          DEFAULT NULL COMMENT '售后类型(1=仅退款, 2=退货退款)',
    `aftersales_reason_code` INT         DEFAULT NULL COMMENT '售后原因编码',
    `aftersales_reason_desc` VARCHAR(512) DEFAULT NULL COMMENT '售后原因描述',
    `buyer_comment`         TEXT         COMMENT '买家售后说明',
    `goods_id`              BIGINT       DEFAULT NULL COMMENT '商品ID',
    `sku_id`                BIGINT       DEFAULT NULL COMMENT '货品SKU ID',
    `product_sku_id`        BIGINT       DEFAULT NULL COMMENT '商品SKU ID',
    `apply_goods_number`    INT          DEFAULT NULL COMMENT '申请售后商品数量',
    `del_flag`              TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_site_aftersales` (`channel_id`, `site`, `aftersales_sn`),
    KEY `idx_parent_aftersales_sn` (`parent_aftersales_sn`),
    KEY `idx_channel_site_status` (`channel_id`, `site`, `aftersales_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu子售后单表';

-- 3.3 售后产品表（子售后单的 productList 明细）
CREATE TABLE IF NOT EXISTS `tb_temu_aftersales_product` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_aftersales_sn`  VARCHAR(64)  NOT NULL COMMENT '父售后单号',
    `aftersales_sn`         VARCHAR(64)  NOT NULL COMMENT '子售后单号',
    `seller_id`             VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID/店铺编码',
    `channel_id`            INT          DEFAULT NULL COMMENT '中台渠道编号(ERP店铺唯一标识)',
    `site`                  VARCHAR(8)   DEFAULT NULL COMMENT '站点(US/AU/CA等)',
    `product_sku_id`        BIGINT       DEFAULT NULL COMMENT '货品SKU ID',
    `ext_code`              VARCHAR(128) DEFAULT NULL COMMENT '货品编码',
    `del_flag`              TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_site_aftersales_sku` (`channel_id`, `site`, `aftersales_sn`, `product_sku_id`),
    KEY `idx_parent_aftersales_sn` (`parent_aftersales_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu售后产品表';

-- 3.4 售后退货物流表（bg.aftersales.parentreturnorder.get）
CREATE TABLE IF NOT EXISTS `tb_temu_aftersales_return_logistics` (
    `id`                            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_aftersales_sn`          VARCHAR(64)  NOT NULL COMMENT '父售后单号',
    `seller_id`                     VARCHAR(64)  NOT NULL COMMENT '卖家ID/店铺编码',
    `channel_id`                    INT          DEFAULT NULL COMMENT '中台渠道编号(ERP店铺唯一标识)',
    `site`                          VARCHAR(8)   DEFAULT NULL COMMENT '站点(US/AU/CA等)',
    `carrier_name`                  VARCHAR(128) DEFAULT NULL COMMENT '物流承运商名称',
    `tracking_number`               VARCHAR(128) NOT NULL COMMENT '退货运单号',
    `return_warehouse_type`         INT          DEFAULT NULL COMMENT 'Temu退货仓类型',
    `return_warehouse_region1_name` VARCHAR(128) DEFAULT NULL COMMENT '退货仓一级国家/地区名称',
    `del_flag`                      TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`                   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_site_parent_tracking`
        (`channel_id`, `site`, `parent_aftersales_sn`, `tracking_number`),
    KEY `idx_parent_aftersales_sn` (`parent_aftersales_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu售后退货物流表';

-- 4. 订单金额表（bg.order.amount.query  temu暂时不支持，此表暂时不建立）

-- 4.1 父订单金额表
CREATE TABLE IF NOT EXISTS `tb_temu_parent_order_amount` (
    `id`                          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_order_sn`             VARCHAR(64)  NOT NULL COMMENT '父单号',
    `seller_id`                   VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID/店铺编码',
    `currency`                    VARCHAR(8)   DEFAULT NULL COMMENT '货币',
    `customer_paid`               BIGINT       DEFAULT NULL COMMENT '客户实付',
    `tax_total`                   BIGINT       DEFAULT NULL COMMENT '税费总额',
    `refunds_total`               BIGINT       DEFAULT NULL COMMENT '退款总额',
    `estimated_revenue`           BIGINT       DEFAULT NULL COMMENT '预估收入',
    `estimated_revenue_deduction` BIGINT       DEFAULT NULL COMMENT '预估收入扣减',
    `discount_from_temu`          BIGINT       DEFAULT NULL COMMENT '平台折扣',
    `discount_from_seller`        BIGINT       DEFAULT NULL COMMENT '卖家折扣',
    `total_discount`              BIGINT       DEFAULT NULL COMMENT '总折扣',
    `shipping_amount_total`       BIGINT       DEFAULT NULL COMMENT '运费总额',
    `base_price_total`            BIGINT       DEFAULT NULL COMMENT '基础价总额',
    `retail_price_total`          BIGINT       DEFAULT NULL COMMENT '零售价总额',
    `del_flag`                    TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`                 DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`                 DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_seller` (`parent_order_sn`, `seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu父订单金额表';

-- 4.2 子订单金额表
CREATE TABLE IF NOT EXISTS `tb_temu_children_order_amount` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_order_sn`       VARCHAR(64)  NOT NULL COMMENT '父单号',
    `order_sn`              VARCHAR(64)  NOT NULL COMMENT '子单号',
    `seller_id`             VARCHAR(64)  DEFAULT NULL COMMENT '卖家ID/店铺编码',
    `currency`              VARCHAR(8)   DEFAULT NULL COMMENT '货币',
    `quantity`              INT          DEFAULT NULL COMMENT '数量',
    `discount_from_temu`    BIGINT       DEFAULT NULL COMMENT '平台折扣',
    `discount_from_seller`  BIGINT       DEFAULT NULL COMMENT '卖家折扣',
    `ship_amount_total`     BIGINT       DEFAULT NULL COMMENT '运费',
    `unit_base_price`       BIGINT       DEFAULT NULL COMMENT '单件基础价',
    `unit_retail_price`     BIGINT       DEFAULT NULL COMMENT '单件零售价',
    `base_price`            BIGINT       DEFAULT NULL COMMENT '基础价(单价×数量)',
    `del_flag`              TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_seller` (`order_sn`, `seller_id`),
    KEY `idx_parent_order_sn` (`parent_order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu子订单金额表';

-- ============================================================
-- 5. 物流模块（Logistics）
-- ============================================================

-- 5.1 仓库表（bg.logistics.warehouse.list.get 返回的发货仓库列表）
CREATE TABLE IF NOT EXISTS `tb_temu_warehouse` (
    `id`                            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `seller_id`                     VARCHAR(64)  NOT NULL COMMENT '卖家ID/店铺编码',
    `warehouse_id`                  VARCHAR(64)  NOT NULL COMMENT '仓库ID',
    `warehouse_name`                VARCHAR(128) DEFAULT NULL COMMENT '仓库名称',
    `region_id1`                    INT          DEFAULT NULL COMMENT '区域ID(美国=211)',
    `warehouse_management_type`     INT          DEFAULT NULL COMMENT '仓库管理类型',
    `default_warehouse`             TINYINT      DEFAULT 0 COMMENT '是否默认仓库(0=否, 1=是)',
    `push_order_to_open_platform`   TINYINT      DEFAULT 0 COMMENT '是否推送订单到开放平台(0=否, 1=是)',
    `enable_buy_shipping_label`     TINYINT      DEFAULT 0 COMMENT '是否启用购买面单(0=否, 1=是)',
    `del_flag`                      TINYINT      DEFAULT 0 COMMENT '软删除(0=正常, 1=删除)',
    `create_time`                   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_user_id`                VARCHAR(32)  DEFAULT NULL COMMENT '创建人ID',
    `update_time`                   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user_id`                VARCHAR(32)  DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_seller_warehouse` (`seller_id`, `warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu仓库表';

-- ============================================================
-- 6. 商品档案模块（Goods）
-- ============================================================

-- 6.1 商品主表（bg.glo.goods.list.get 返回的 data 数组）
CREATE TABLE IF NOT EXISTS `tb_temu_product` (
    `id`                      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_id`                  VARCHAR(64)   NOT NULL COMMENT 'Temu应用主体简写昵称',
    `seller_id`               VARCHAR(64)   NOT NULL COMMENT '卖家ID/店铺编码',
    `shop_name`               VARCHAR(128)  DEFAULT NULL COMMENT '店铺名称（冗余自授权表，方便查询）',
    `product_id`              BIGINT        NOT NULL COMMENT 'Temu商品ID',
    `product_skc_id`          BIGINT        DEFAULT NULL COMMENT 'Temu SKC ID',
    `product_name`            VARCHAR(512)  DEFAULT NULL COMMENT '商品名称',
    `ext_code`                VARCHAR(128)  DEFAULT NULL COMMENT '商品外部编码',
    `main_image_url`          VARCHAR(1024) DEFAULT NULL COMMENT '商品主图地址',
    `leaf_cat_id`             BIGINT        DEFAULT NULL COMMENT '末级类目ID',
    `leaf_cat_name`           VARCHAR(255)  DEFAULT NULL COMMENT '末级类目名称',
    `skc_site_status`         INT           DEFAULT NULL COMMENT 'SKC站点状态',
    `support_personalization` TINYINT       DEFAULT NULL COMMENT '是否支持个性化(0=否,1=是)',
    `match_skc_jit_mode`      TINYINT       DEFAULT NULL COMMENT '是否匹配SKC JIT模式(0=否,1=是)',
    `created_at_api`          BIGINT        DEFAULT NULL COMMENT 'API商品创建时间(epoch毫秒)',
    `properties_json`         MEDIUMTEXT    DEFAULT NULL COMMENT '商品属性(JSON数组)',
    `extension_json`          MEDIUMTEXT    DEFAULT NULL COMMENT '绑定站点、类目、JIT及半托管扩展信息(JSON对象)',
    `sync_task_id`            BIGINT        DEFAULT NULL COMMENT '最近一次成功同步任务ID',
    `del_flag`                TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除(0=正常,1=删除)',
    `create_time`             DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`             DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_seller_product` (`app_id`, `seller_id`, `product_id`),
    KEY `idx_app_seller_skc` (`app_id`, `seller_id`, `product_skc_id`),
    KEY `idx_app_seller_ext_code` (`app_id`, `seller_id`, `ext_code`),
    KEY `idx_app_seller_sync_task` (`app_id`, `seller_id`, `sync_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu商品档案表';

-- 6.2 商品SKU表（productSkuSummaries 数组）
CREATE TABLE IF NOT EXISTS `tb_temu_product_sku` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_id`              VARCHAR(64)  NOT NULL COMMENT 'Temu应用主体简写昵称',
    `seller_id`           VARCHAR(64)  NOT NULL COMMENT '卖家ID/店铺编码',
    `shop_name`           VARCHAR(128) DEFAULT NULL COMMENT '店铺名称（冗余自授权表，方便查询）',
    `product_id`          BIGINT       NOT NULL COMMENT '所属Temu商品ID',
    `product_skc_id`      BIGINT       DEFAULT NULL COMMENT '所属Temu SKC ID',
    `product_sku_id`      BIGINT       NOT NULL COMMENT 'Temu商品SKU ID',
    `ext_code`            VARCHAR(128) DEFAULT NULL COMMENT 'SKU外部编码',
    `virtual_stock`       INT          DEFAULT NULL COMMENT '虚拟库存',
    `shipping_mode`       INT          DEFAULT NULL COMMENT 'SKU发货模式',
    `weight`              BIGINT       DEFAULT NULL COMMENT 'SKU重量(Temu原始计量值)',
    `package_length`      INT          DEFAULT NULL COMMENT '包裹长度(Temu原始计量值)',
    `package_width`       INT          DEFAULT NULL COMMENT '包裹宽度(Temu原始计量值)',
    `package_height`      INT          DEFAULT NULL COMMENT '包裹高度(Temu原始计量值)',
    `sensitive_flag`      TINYINT      DEFAULT NULL COMMENT '是否敏感品(0=否,1=是)',
    `extension_json`      MEDIUMTEXT   DEFAULT NULL COMMENT '规格、条码及敏感品扩展信息(JSON对象)',
    `sync_task_id`        BIGINT       DEFAULT NULL COMMENT '最近一次成功同步任务ID',
    `del_flag`            TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除(0=正常,1=删除)',
    `create_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_seller_sku` (`app_id`, `seller_id`, `product_sku_id`),
    KEY `idx_app_seller_product` (`app_id`, `seller_id`, `product_id`),
    KEY `idx_app_seller_ext_code` (`app_id`, `seller_id`, `ext_code`),
    KEY `idx_app_seller_sync_task` (`app_id`, `seller_id`, `sync_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu商品SKU表';

-- 6.3 商品库存当前快照表（bg.btg.goods.stock.quantity.get）
CREATE TABLE IF NOT EXISTS `tb_temu_product_stock_snapshot` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_id`             VARCHAR(64)  NOT NULL COMMENT 'Temu应用主体简写昵称',
    `seller_id`          VARCHAR(64)  NOT NULL COMMENT '卖家ID/店铺编码',
    `shop_name`          VARCHAR(128) DEFAULT NULL COMMENT '店铺名称（冗余自授权表，方便查询）',
    `product_skc_id`     BIGINT       NOT NULL COMMENT 'Temu SKC ID',
    `product_sku_id`     BIGINT       NOT NULL COMMENT 'Temu商品SKU ID',
    `warehouse_id`       VARCHAR(64)  NOT NULL COMMENT '仓库ID',
    `warehouse_name`     VARCHAR(255) DEFAULT NULL COMMENT '仓库名称',
    `stock_quantity`     INT          DEFAULT NULL COMMENT 'SKU可用库存数量',
    `temp_lock_quantity` INT          DEFAULT NULL COMMENT '临时锁定数量',
    `enable_pre_sale`    TINYINT      DEFAULT NULL COMMENT '是否启用预售(0=否,1=是)',
    `shipping_mode`      INT          DEFAULT NULL COMMENT '发货模式',
    `snapshot_version`   VARCHAR(32)  NOT NULL COMMENT '当前库存快照版本号',
    `snapshot_time`      DATETIME     NOT NULL COMMENT '当前库存快照生成时间',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_seller_sku_warehouse`
        (`app_id`, `seller_id`, `product_sku_id`, `warehouse_id`),
    KEY `idx_app_seller_skc` (`app_id`, `seller_id`, `product_skc_id`),
    KEY `idx_warehouse_id` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu商品库存当前快照表';

-- 6.4 商品库存历史快照表（默认保留7天）
CREATE TABLE IF NOT EXISTS `tb_temu_product_stock_snapshot_history` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_id`             VARCHAR(64)  NOT NULL COMMENT 'Temu应用主体ID',
    `seller_id`          VARCHAR(64)  NOT NULL COMMENT '卖家ID/店铺编码',
    `shop_name`          VARCHAR(128) DEFAULT NULL COMMENT '店铺名称（冗余自授权表，保留快照时名称）',
    `product_skc_id`     BIGINT       NOT NULL COMMENT 'Temu SKC ID',
    `product_sku_id`     BIGINT       NOT NULL COMMENT 'Temu商品SKU ID',
    `warehouse_id`       VARCHAR(64)  NOT NULL COMMENT '仓库ID',
    `warehouse_name`     VARCHAR(255) DEFAULT NULL COMMENT '仓库名称',
    `stock_quantity`     INT          DEFAULT NULL COMMENT 'SKU可用库存数量',
    `temp_lock_quantity` INT          DEFAULT NULL COMMENT '临时锁定数量',
    `enable_pre_sale`    TINYINT      DEFAULT NULL COMMENT '是否启用预售(0=否,1=是)',
    `shipping_mode`      INT          DEFAULT NULL COMMENT '发货模式',
    `snapshot_version`   VARCHAR(32)  NOT NULL COMMENT '库存快照版本号',
    `snapshot_time`      DATETIME     NOT NULL COMMENT '库存快照生成时间',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_seller_version_sku_warehouse`
        (`app_id`, `seller_id`, `snapshot_version`, `product_sku_id`, `warehouse_id`),
    KEY `idx_app_seller_snapshot` (`app_id`, `seller_id`, `snapshot_time`),
    KEY `idx_snapshot_version` (`snapshot_version`),
    KEY `idx_snapshot_time` (`snapshot_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Temu商品库存历史快照表';
