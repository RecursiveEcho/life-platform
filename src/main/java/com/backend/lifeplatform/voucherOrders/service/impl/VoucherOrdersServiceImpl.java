package com.backend.lifeplatform.voucherOrders.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.common.utils.SecurityUserContext;
import com.backend.lifeplatform.voucherOrders.entity.VoucherOrders;
import com.backend.lifeplatform.voucherOrders.mapper.VoucherOrdersMapper;
import com.backend.lifeplatform.voucherOrders.mq.VoucherOrderMessage;
import com.backend.lifeplatform.voucherOrders.mq.VoucherProducer;
import com.backend.lifeplatform.voucherOrders.service.SeckillRedisService;
import com.backend.lifeplatform.voucherOrders.service.VoucherOrdersService;
import com.backend.lifeplatform.voucherOrders.vo.VoucherOrdersVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


/**
 * 优惠券订单业务实现。
 *
 * <p>秒杀请求采用“Redis 原子预扣 -> RabbitMQ 投递 -> 消费者事务建单”的异步流程。
 * MySQL 建单阶段通过事务、条件扣库存和订单唯一索引保证最终一致性。</p>
 */
@Service
@RequiredArgsConstructor
public class VoucherOrdersServiceImpl implements VoucherOrdersService {

    private final VoucherOrdersMapper voucherOrdersMapper;
    private final VoucherProducer voucherProducer;
    private final SeckillRedisService seckillRedisService;

    /**
     * 订单详情缓存 key 前缀（预留）
     */
    private static final String CACHE_ORDERS_DETAIL = "cache:orders:detail";
    /**
     * 订单列表缓存 key 前缀（预留）
     */
    private static final String CACHE_ORDERS_LIST = "cache:orders:list";
    /**
     * 订单列表缓存版本 key（预留）
     */
    private static final String CACHE_ORDERS_LIST_VER = "cache:orders:list:ver";

    private static final TypeReference<PageResult<VoucherOrdersVO>> ORDER_LIST_TYPE = new TypeReference<>() {
    };

    /**
     * 提交异步秒杀请求：只做身份获取、订单号生成和消息投递，不在 HTTP 线程建单。
     */
    @Override
    public Long submitSeckill(Long voucherId) {
        Long userId = SecurityUserContext.requireUserId();
        Long orderId = IdWorker.getId();

        // 先在 Redis 中原子预扣，快速挡住售罄和重复用户；此时数据库还没有订单。
        seckillRedisService.preDeduct(voucherId, userId);

        try {
            // 消息确认成功后才把预扣结果交给异步消费者落 MySQL。
            boolean accepted = voucherProducer.sendAndConfirm(
                    new VoucherOrderMessage(userId, voucherId, orderId));

            if (!accepted) {
                throw new IllegalStateException("RabbitMQ 未确认消息");
            }
            return orderId;
        } catch (Exception e) {
            // broker 未确认或发送过程异常，当前请求没有可靠的消费入口，必须撤销 Redis 预扣。
            seckillRedisService.compensatePreDeduct(voucherId, userId);
            throw new BusinessException(ErrorCode.SYSTEM_BUSY,
                    "系统繁忙，请稍后重试");
        }
    }

    /**
     * 分页查询当前登录用户的订单；orderStatus 为空表示查询全部状态。
     */
    @Override
    public PageResult<VoucherOrdersVO> getOrders(Integer orderStatus
            , Integer current
            , Integer size) {
        Long userId = SecurityUserContext.requireUserId();

        // 只查当前用户的订单，可按状态过滤，并按下单时间倒序
        LambdaQueryWrapper<VoucherOrders> wrapper =
                new LambdaQueryWrapper<VoucherOrders>()
                        .eq(VoucherOrders::getUserId, userId)
                        .eq(orderStatus != null,
                                VoucherOrders::getOrderStatus,
                                orderStatus)
                        .orderByDesc(VoucherOrders::getCreateTime)
                        .orderByDesc(VoucherOrders::getId);

        IPage<VoucherOrders> page =
                voucherOrdersMapper.selectPage(
                        new Page<>(current, size),
                        wrapper
                );
        return PageResult.of(page.getRecords().stream().map(this::toVO).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 将订单实体拷贝为接口返回对象。
     */
    private VoucherOrdersVO toVO(VoucherOrders orders) {
        VoucherOrdersVO voucherOrdersVO = new VoucherOrdersVO();
        BeanUtils.copyProperties(orders, voucherOrdersVO);
        return voucherOrdersVO;
    }
}
