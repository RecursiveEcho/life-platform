-- KEYS[1]：活动快照 Hash
-- KEYS[2]：秒杀库存 String
-- KEYS[3]：已抢用户 Set
-- ARGV[1]：库存
-- ARGV[2]：开始时间毫秒时间戳
-- ARGV[3]：结束时间毫秒时间戳

-- 1 新建成功，2 已存在且数据一致，0 数据冲突

local activityExists = redis.call('EXISTS',KEYS[1])
local stockExists = redis.call('EXISTS',KEYS[2])
local usersExists = redis.call('EXISTS',KEYS[3])

if activityExists == 0 and stockExists == 0 and usersExists == 0 then
	redis.call(
		'HSET',
		KEYS[1],
		'status', 'PUBLISHING',
		'beginTime', ARGV[2],
		'endTime', ARGV[3],
		'initialStock', ARGV[1]
	)
	redis.call('SET', KEYS[2], ARGV[1])
	return 1
end

if activityExists == 1 and stockExists == 1 then
	local status = redis.call('HGET', KEYS[1], 'status')
	local beginTime = redis.call('HGET', KEYS[1], 'beginTime')
	local endTime = redis.call('HGET', KEYS[1], 'endTime')
	local initialStock = redis.call('HGET', KEYS[1], 'initialStock')

	if (status == 'PUBLISHING' or status == 'PUBLISHED')
			and beginTime == ARGV[2]
			and endTime == ARGV[3]
			and initialStock == ARGV[1] then
		return 2
	end
end

return 0