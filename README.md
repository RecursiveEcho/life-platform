# life-platform

一个基于 Spring Boot 的本地生活平台后端练习项目。

## 技术栈

- Java 17
- Spring Boot
- Spring Security + JWT
- MyBatis-Plus
- MySQL
- Redis
- Knife4j + SpringDoc

## 启动前准备

1. 启动 MySQL 和 Redis。
2. 执行 `sql/000_schema.sql` 初始化新数据库。
3. 已有旧数据库按 `001` 到 `006` 的编号顺序执行缺少的增量脚本；其中 `005` 是评价表，`006` 用于补齐优惠券、秒杀券和订单基础表。

默认数据库配置：

- MySQL：`localhost:3306/life_platform`
- MySQL 用户：`root`
- MySQL 密码：空
- Redis：`localhost:6379`

## 环境变量

完整清单和默认值见 [.env.example](.env.example)。项目没有集成自动读取 `.env` 的组件，所以需要手动导出，或写进 IDE 的运行配置：

```bash
cp .env.example .env   # 按需填写
set -a && source .env && set +a
./mvnw spring-boot:run
```

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `JWT_SECRET` | 无（**必填**） | JWT 签名密钥，未设置时应用启动失败。生成：`openssl rand -base64 48` |
| `JWT_EXPIRATION_MS` | `86400000` | token 有效期（毫秒） |
| `DB_URL` | `jdbc:mysql://localhost:3306/life_platform?...` | 数据库连接串 |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / 空 | 数据库账号 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 连接 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | RabbitMQ 连接 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | RabbitMQ 账号 |
| `CORS_ALLOWED_ORIGIN_PATTERNS` | `http://localhost,...` | 允许的跨域来源 |

> 仓库和代码中不保留任何 JWT 默认密钥，`JWT_SECRET` 必须通过环境变量注入。

## 启动

```bash
./mvnw spring-boot:run
```

默认端口为 `8080`，也可以通过 `server.port` 修改。

## 接口文档

启动后访问：

```text
http://localhost:8080/doc.html
```

## 代码导航

先从控制器进入，再到同名的 Service 实现类。Service 实现里的中文分组就是阅读顺序：

- 用户注册、登录：`user/controller/UserController` -> `user/service/impl/UserServiceImpl`
  - `注册`：检查手机号、加密密码、写入用户
  - `登录`：限流、查询账号、校验密码、签发 JWT
- 店铺列表、详情、创建、修改：`shop/controller/ShopController` -> `shop/service/impl/ShopServiceImpl`
  - `对外业务方法`：四个接口入口
  - `查询辅助`：数据库分页查询和实体转 VO
  - `写入和权限辅助`：修改参数校验、字段更新、店铺归属校验
  - `事务和缓存辅助`：提交回调、锁释放、缓存失效
- 商家入驻和审核：`merchant/controller/MerchantApplicationController` -> `merchant/service/impl/MerchantApplicationServiceImpl`
  - `提交和审批`：提交、通过、驳回的完整流程
  - `查询`：申请列表和详情
  - `内部查询和更新辅助`：只保留多处复用的查询和实体转换
- Redis 缓存公共逻辑：`common/utils/RedisJsonCacheTool`
  - `读缓存`、`写缓存`、`列表缓存版本`、`空值占位`、`参数校验`
- 优惠券详情和秒杀活动：`voucher/controller/VoucherController` -> `voucher/service/impl/VoucherServiceImpl`
  - 商家创建/修改普通优惠券；发布后的秒杀活动禁止直接修改基础券信息，避免活动快照与券信息不一致
  - 店铺优惠券列表：只展示已发布且未结束的秒杀券，支持关键词和分页
  - `getDetails`：读取普通券和秒杀场次，组装 `VouchersVO` 并缓存
  - `createSeckillVoucher`：校验角色、店铺归属、时间顺序和库存上限，创建 `DRAFT` 场次
- 秒杀下单和订单查询：`voucherOrders/controller/VoucherOrdersController` -> `voucherOrders/service/impl/VoucherOrdersServiceImpl`
  - 抢券请求：`SeckillRedisServiceImpl` 执行 Lua 原子预扣，再由 `VoucherProducer` 投递 RabbitMQ
  - 消费建单：`VoucherOrderConsumer` 调用 `VoucherOrderTransactionServiceImpl`，在 MySQL 事务中做幂等、时间校验、条件扣库存和插入订单
  - 异常处理：临时故障进入 TTL 重试队列，最终失败进入 `VoucherOrderDeadLetterConsumer` 做 Redis 预扣补偿
- 店铺评价：`review/controller/ShopReviewController` -> `review/service/impl/ShopReviewServiceImpl`
  - 创建评价必须使用当前用户自己的订单，服务端根据订单反查店铺，数据库唯一索引保证同一订单不能重复评价
  - 店铺评价分页查询只返回正常状态的公开评价

## 基础接口

- `POST /api/users/register`：注册
- `POST /api/users/login`：登录
- `GET /api/shops`：查询店铺列表
- `GET /api/shops/{id}`：查询店铺详情
- `POST /api/shops`：管理员创建店铺
- `PATCH /api/shops/{id}`：管理员修改店铺
- `GET /api/vouchers/shop/{shopId}`：分页查询店铺当前可见的秒杀优惠券
- `GET /api/vouchers/{id}`：查询优惠券详情
- `POST /api/vouchers`：管理员或商家创建普通优惠券
- `PATCH /api/vouchers/{id}`：管理员或商家修改未发布活动对应的优惠券
- `POST /api/reviews`：当前用户提交店铺评价
- `GET /api/reviews/shop/{shopId}`：分页查询店铺公开评价

店铺创建和修改需要在请求头携带：

```text
Authorization: Bearer <登录返回的 token>
```
