-- 只有成功移除用户标记，才恢复一次库存；重复补偿返回 0，不会重复加库存。
if redis.call('SREM', KEYS[2], ARGV[1]) == 1 then
	redis.call('INCR', KEYS[1])
	return 1
end
return 0;
