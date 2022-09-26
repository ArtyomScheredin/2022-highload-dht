math.randomseed(os.time())
request = function() 
   
   url_path = "/v0/entity?id=" .. math.random(0,1000000)
   return wrk.format("PUT", url_path, nil, "test")
end
