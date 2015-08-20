#LDT Time-to-live

## Problem
Large Data Types offer a way to store an ordered list wit essentiall an unlimited number elements. But unlike regular records, LDT subrecords do not have an automatic time-to-live or expirary mechanism. You use case requires you to "expire" elements or sub records in a LDT.

##Solution
The solution is to implement the elements of the LDT as a Map. One of the values of the Map contains an expitary epoc stotred in a Long as the number of seconds since 1 January 1970. As you write, or re-write, this element, you update the expirary epoch.

Using a "Scan UDF", periodically you scan throught the whole Namespace and Set looking for expired elements, when one is fount it is deleted from the LDT.

It sounds complicated but not really.


The source code for this solution is available on GitHub, and the README.md 
http://github.com/some place. 


##Discussion
