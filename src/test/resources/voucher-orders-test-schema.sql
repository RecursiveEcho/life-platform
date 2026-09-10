DROP TABLE IF EXISTS voucher_orders;
DROP TABLE IF EXISTS seckill_vouchers;
DROP TABLE IF EXISTS vouchers;

CREATE TABLE vouchers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    shop_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    sub_title VARCHAR(255),
    discount_amount DECIMAL(10, 2) NOT NULL,
    pay_value DECIMAL(10, 2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    begin_time DATETIME,
    end_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE seckill_vouchers (
    voucher_id BIGINT NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    begin_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    PRIMARY KEY (voucher_id),
    CONSTRAINT chk_test_seckill_stock CHECK (stock >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE voucher_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    voucher_id BIGINT NOT NULL,
    order_status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pay_time DATETIME,
    PRIMARY KEY (id),
    UNIQUE KEY uk_test_user_voucher (user_id, voucher_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
