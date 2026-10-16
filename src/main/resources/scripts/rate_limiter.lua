local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]

local clearBefore = now - window

-- Remove entries outside current sliding window
redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)

-- Count current requests within the sliding window
local currentRequests = redis.call('ZCARD', key)

if currentRequests < limit then
    -- Request accepted: record timestamp member and refresh expiry
    redis.call('ZADD', key, now, member)
    redis.call('PEXPIRE', key, window)
    local remaining = limit - currentRequests - 1
    local resetSeconds = math.ceil(window / 1000)
    return {1, limit, remaining, resetSeconds, 0}
else
    -- Request denied: calculate retry-after from oldest entry in current window
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retryAfter = 1
    if oldest and #oldest >= 2 then
        local oldestTime = tonumber(oldest[2])
        local timeUntilExpire = (oldestTime + window) - now
        retryAfter = math.ceil(timeUntilExpire / 1000)
        if retryAfter < 1 then
            retryAfter = 1
        end
    end
    return {0, limit, 0, retryAfter, retryAfter}
end
