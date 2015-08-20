local llist = require('ldt/lib_llist');
local LDT_KEY = "key"
local LDT_TTL = "TTL"

function expire(rec, binName)
  local currentTime = os.time()
  local items = llist.scan(rec, binName)
  local expiredCount = 0;
  for element in list.iterator(items) do
   
    local ttl = element[LDT_TTL];
    --info(tostring(ttl).." vs "..tostring(currentTime))
    if ttl < currentTime then    
      --info(tostring(element).."..Removed")
      llist.remove(rec, binName, element)
      expiredCount = expiredCount + 1
    end   
  end
  --info("Size: "..tostring(llist.size(rec, binName)))
  --info("Expired "..tostring(expiredCount).." element")
end