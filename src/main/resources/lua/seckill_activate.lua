-- 1 激活成功或已经激活，0 激活失败

if redis.call('EXISTS',KEYS[1]) == 0 then
	return 0
end

local status = redis.call('HGET',KEYS[1],'status')

if status == 'PUBLISHED' then
	return 1
end

if status ~= 'PUBLISHING' then
	return 0
end

redis.call('HSET', KEYS[1], 'status', 'PUBLISHED')
return 1
