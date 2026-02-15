package com.vndev.sentinel.service;

import com.vndev.sentinel.dto.TransactionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SentinelService {
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_PREFIX = "sentinel:tx:";

    public boolean analyzeTransaction(TransactionDTO transaction) {
        String key = REDIS_PREFIX + transaction.getCardToken();

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == 1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }

        log.debug("Analisando cartão: {}. Tentativas no último minuto: {}", transaction.getCardToken(), count);

        if (count > 3) {
            log.warn("BLOQUEADO: Alta frequência detectada para o cartão {}", transaction.getCardToken());
            return false;
        }

        if (transaction.getAmount().doubleValue() > 10000) {
            log.warn("ALERTA: Valor atípico detectado: R$ {}", transaction.getAmount());
            // Aqui poderíamos retornar false ou enviar para uma fila de análise manual.
        }

        return true;
    }
}