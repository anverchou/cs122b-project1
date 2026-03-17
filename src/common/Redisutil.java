package common;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisUtil {

    private static final String REDIS_HOST = System.getenv("REDIS_HOST") != null
            ? System.getenv("REDIS_HOST") : "redis";
    private static final int REDIS_PORT = 6379;
    private static final int SESSION_TTL_SECONDS = 3600; // 1 hour

    private static JedisPool jedisPool;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(20);
        config.setMaxIdle(10);
        jedisPool = new JedisPool(config, REDIS_HOST, REDIS_PORT);
    }

   static void setSessionAttribute(String sessionId, String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            String redisKey = "session:" + sessionId + ":" + key;
            jedis.setex(redisKey, SESSION_TTL_SECONDS, value);
        }
    }

    // Retrieve a session attribute from Redis
    public static String getSessionAttribute(String sessionId, String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String redisKey = "session:" + sessionId + ":" + key;
            return jedis.get(redisKey);
        }
    }

    // Remove a session attribute from Redis
    public static void removeSessionAttribute(String sessionId, String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String redisKey = "session:" + sessionId + ":" + key;
            jedis.del(redisKey);
        }
    }

    // Remove all session attributes for a given session ID
    public static void invalidateSession(String sessionId) {
        try (Jedis jedis = jedisPool.getResource()) {
            // Find all keys for this session and delete them
            var keys = jedis.keys("session:" + sessionId + ":*");
            if (keys != null && !keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
            }
        }
    }
}