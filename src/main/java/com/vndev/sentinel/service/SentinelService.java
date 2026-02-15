package com.vndev.sentinel.service;

import com.vndev.sentinel.dto.TransactionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SentinelService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate; // Injeção para o WebSocket

    private static final String REDIS_PREFIX = "sentinel:tx:";
    private static final String GEO_PREFIX = "sentinel:geo:";

    public boolean analyzeTransaction(TransactionDTO transaction) {
        String cardToken = transaction.getCardToken();

        // Realiza as verificações
        boolean velocityOk = checkVelocity(cardToken);
        boolean locationOk = checkLocation(transaction);

        // Aprovado apenas se passar em ambos os testes
        boolean approved = velocityOk && locationOk;

        // Prepara o evento para o Dashboard em tempo real
        var event = Map.of(
                "transaction", transaction,
                "status", approved ? "APPROVED" : "DENIED",
                "reason", !velocityOk ? "Alta Frequência" : (!locationOk ? "Deslocamento Impossível" : "Nenhuma"),
                "timestamp", Instant.now().toString()
        );

        // Envia para o tópico do WebSocket (Dashboard)
        messagingTemplate.convertAndSend("/topic/transactions", (Object) event);

        return approved;
    }

    public boolean checkVelocity(String cardToken) {
        String key = REDIS_PREFIX + cardToken;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }

        if (count != null && count > 3) {
            log.warn("BLOQUEADO: Alta frequência detectada para o cartão {}", cardToken);
            return false;
        }

        return true;
    }

    private boolean checkLocation(TransactionDTO tx) {
        String geoKey = GEO_PREFIX + tx.getCardToken();
        String lastData = redisTemplate.opsForValue().get(geoKey);

        if (lastData != null) {
            String[] parts = lastData.split(";");
            double lastLat = Double.parseDouble(parts[0]);
            double lastLon = Double.parseDouble(parts[1]);
            long lastTs = Long.parseLong(parts[2]);

            double distance = calculateDistance(lastLat, lastLon, tx.getLatitude(), tx.getLongitude());
            long timeDiffSeconds = Instant.now().getEpochSecond() - lastTs;

            // Evita divisão por zero se as transações forem no mesmo segundo
            if (timeDiffSeconds <= 0) timeDiffSeconds = 1;

            // Velocidade em km/h
            double kmPerHours = (distance / 1000.0) / (timeDiffSeconds / 3600.0);

            log.debug("Análise Geo: {}m em {}s. Velocidade calculada: {} km/h", distance, timeDiffSeconds, kmPerHours);

            // Regra: Bloqueia se a velocidade média for superior a 500 km/h
            if (kmPerHours > 500 && timeDiffSeconds < 3600) {
                log.warn("BLOQUEADO: Deslocamento impossível detectado para o cartão {}", tx.getCardToken());
                return false;
            }
        }

        // Salva a localização atual para a próxima análise (expira em 24h)
        String currentData = tx.getLatitude() + ";" + tx.getLongitude() + ";" + Instant.now().getEpochSecond();
        redisTemplate.opsForValue().set(geoKey, currentData, 24, TimeUnit.HOURS);
        return true;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Raio da Terra em metros
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}