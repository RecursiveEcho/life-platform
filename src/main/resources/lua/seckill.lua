-- KEYS[1]：活动快照 Hash
-- KEYS[2]：秒杀库存 String
-- KEYS[3]：已抢用户 Set
-- ARGV[1]：当前用户 id
--
-- 返回值：
-- 0 成功
-- 1 库存不足
-- 2 重复抢购
-- 3 活动快照不存在
-- 4 活动未发布或已下线
-- 5 活动未开始
-- 6 活动已结束

local status = redis.call('HGET',KEYS[1],'status')

if not status then
		return 3
end

if status ~= 'PUBLISHED' then
	return 4
end

local beginTime = tonumber(redis.call('HGET',KEYS[1],'beginTime'))
local endTime = tonumber(redis.call('HGET',KEYS[1],'endTime'))

if not beginTime or not endTime then
	return 3
end

-- 用 Redis 服务器时间，不依赖应用服务器的本地时钟。
local redisTime = redis.call('TIME')
local now = tonumber(redisTime[1]) * 1000
			+ math.floor(tonumber(redisTime[2])/1000)

if now < beginTime then
	return 5
end

if now > endTime then
	return 6
end

local stock = tonumber(redis.call('GET', KEYS[2]))
if not stock or stock <= 0 then
	return 1
end

if redis.call('SISMEMBER', KEYS[3], ARGV[1]) == 1 then
	return 2
end

redis.call('DECR', KEYS[2])
redis.call('SADD', KEYS[3], ARGV[1])

return 0