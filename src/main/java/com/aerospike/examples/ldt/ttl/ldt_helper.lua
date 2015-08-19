local llist = require('ldt/lib_llist');
local LDT_KEY = "key"
local LDT_TTL = "TTL"

function expire(rec, binName)
  local currentTime = os.time()
  
  local items = llist.scan(rec, binName)
  
  for element in list.iterator(l) do
    local ttl = element[LDT_TTL];
    if ttl < currentTime then    
       llist.remove(element)
    end   
  end
end