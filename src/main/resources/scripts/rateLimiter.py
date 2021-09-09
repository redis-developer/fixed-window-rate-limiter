def rate_limit(key, max_requests, expiry):
  requests = execute('GET', key)
  requests = int(requests) if requests else -1
  max_requests = int(max_requests)
  expiry = int(expiry)
      
  if (requests == -1) or (requests < max_requests):
    execute('INCR', key)
    execute('EXPIRE', key, expiry)
    return False
  else:
    return True

# Function registration
gb = GB('CommandReader')
gb.map(lambda x: rate_limit(x[1], x[2], x[3]))
gb.register(trigger='RateLimiter')