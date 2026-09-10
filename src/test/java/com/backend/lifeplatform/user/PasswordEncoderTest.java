package com.backend.lifeplatform.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/** BCrypt 密码编码器行为测试：验证每次加密随机盐、明文与密文可校验。 */
class PasswordEncoderTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void samePasswordCanProduceDifferentHashesButBothMatch() {
        String firstHash = passwordEncoder.encode("123456");
        String secondHash = passwordEncoder.encode("123456");

        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(passwordEncoder.matches("123456", firstHash)).isTrue();
        assertThat(passwordEncoder.matches("123456", secondHash)).isTrue();
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String hash = passwordEncoder.encode("123456");

        assertThat(passwordEncoder.matches("654321", hash)).isFalse();
    }
}
