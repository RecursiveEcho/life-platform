package com.backend.lifeplatform.voucher.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.common.utils.RedisJsonCacheTool;
import com.backend.lifeplatform.shop.entity.Shop;
import com.backend.lifeplatform.shop.mapper.ShopMapper;
import com.backend.lifeplatform.user.mapper.UserMapper;
import com.backend.lifeplatform.voucher.mapper.SeckillVouchersMapper;
import com.backend.lifeplatform.voucher.mapper.VouchersMapper;
import com.backend.lifeplatform.voucher.service.SeckillPublishCommandService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link VoucherServiceImpl} 的权限校验单元测试。
 *
 * <p>覆盖「优惠券写操作只能由管理员或该店铺的商家执行」这条规则：
 * 无角色 / 商家非店主都要抛 {@link ErrorCode#FORBIDDEN}，管理员与店主则放行。
 * 直接构造真实的 SecurityContext，不依赖 Spring 容器和 JWT 过滤器。</p>
 */
@ExtendWith(MockitoExtension.class)
class VoucherServiceImplPermissionTest {

    private static final Long SHOP_ID = 10L;
    private static final Long OWNER_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;

    @Mock
    private VouchersMapper vouchersMapper;

    @Mock
    private RedisJsonCacheTool redisJsonCacheTool;

    @Mock
    private SeckillVouchersMapper seckillVouchersMapper;

    @Mock
    private ShopMapper shopMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private SeckillPublishCommandService seckillPublishCommandService;

    private VoucherServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VoucherServiceImpl(
                vouchersMapper,
                redisJsonCacheTool,
                seckillVouchersMapper,
                shopMapper,
                userMapper,
                seckillPublishCommandService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 往 SecurityContext 里放一个带指定角色的已认证用户。 */
    private void authenticate(String... roles) {
        List<SimpleGrantedAuthority> authorities =
                Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("tester", null, authorities));
    }

    private Shop shopOwnedBy(Long ownerId) {
        Shop shop = new Shop();
        shop.setId(SHOP_ID);
        shop.setOwnerId(ownerId);
        return shop;
    }

    private Shop invokeRequireWritableShop(Long shopId, Long userId) {
        return ReflectionTestUtils.invokeMethod(service, "requireWritableShop", shopId, userId);
    }

    @Test
    void requireWritableShop_whenUserHasNeitherRole_throwsForbidden() {
        authenticate();
        when(shopMapper.selectById(SHOP_ID)).thenReturn(shopOwnedBy(OWNER_ID));

        assertThatThrownBy(() -> invokeRequireWritableShop(SHOP_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void requireWritableShop_whenMerchantIsNotShopOwner_throwsForbidden() {
        authenticate("MERCHANT");
        when(shopMapper.selectById(SHOP_ID)).thenReturn(shopOwnedBy(OWNER_ID));

        assertThatThrownBy(() -> invokeRequireWritableShop(SHOP_ID, OTHER_USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.FORBIDDEN.getCode());
    }

    @Test
    void requireWritableShop_whenMerchantOwnsShop_returnsShop() {
        authenticate("MERCHANT");
        when(shopMapper.selectById(SHOP_ID)).thenReturn(shopOwnedBy(OWNER_ID));

        Shop shop = invokeRequireWritableShop(SHOP_ID, OWNER_ID);

        assertThat(shop).isNotNull();
        assertThat(shop.getId()).isEqualTo(SHOP_ID);
    }

    @Test
    void requireWritableShop_whenUserIsAdmin_returnsShopEvenWhenNotOwner() {
        authenticate("ADMIN");
        when(shopMapper.selectById(SHOP_ID)).thenReturn(shopOwnedBy(OWNER_ID));

        Shop shop = invokeRequireWritableShop(SHOP_ID, OTHER_USER_ID);

        assertThat(shop).isNotNull();
        assertThat(shop.getOwnerId()).isEqualTo(OWNER_ID);
    }

    @Test
    void requireWritableShop_whenShopMissing_throwsShopNotFound() {
        authenticate("ADMIN");
        when(shopMapper.selectById(SHOP_ID)).thenReturn(null);

        assertThatThrownBy(() -> invokeRequireWritableShop(SHOP_ID, OWNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.SHOP_NOT_FOUND.getCode());
    }
}
