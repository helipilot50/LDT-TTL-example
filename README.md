#LDT Time-to-live

## Problem
Large Data Types offer a way to store an ordered list wit essentiall an unlimited number elements. But unlike regular records, LDT subrecords do not have an automatic time-to-live or expirary mechanism. You use case requires you to "expire" elements or sub records in a LDT.

##Solution
The solution is to implement the elements of the LDT as a Map. One of the values of the Map contains an expitary epoc stotred in a Long as the number of seconds since 1 January 1970. As you write, or re-write, this element, you update the expirary epoch.

Using a "Scan UDF", periodically you scan throught the whole Namespace and Set looking for expired elements, when one is fount it is deleted from the LDT.

It sounds complicated but not really. In this example you will see hot to construct the elements of the LDT, and how to expire them with a Scan UDF.

###How to build
The source code for this solution is available on GitHub, and the README.md 
https://github.com/aerospike/LDT-TTL-example 

This example requires a working Java development environment (Java 6 and above) including Maven (Maven 2). The Aerospike Java client will be downloaded from Maven Central as part of the build.

After cloning the repository, use maven to build the jar files. From the root directory of the project, issue the following command:
```bash
mvn clean package
```
A JAR file will be produced in the directory 'target': `ldt-ttl-example-1.0.0-full.jar`. It contains the example code, the User Defined Function and all the dependencies.

###Running the solution
This is a runnable jar complete with all the dependencies packaged.

To load data use this command:
```bash
java -jar target/ldt-ttl-example-1.0.0-full.jar -d
```
It will generate 500 records, each with a Large List. The Large List will contain 500 elements, each with synthetic TTL randomly within the 10 minutes after the data is written.

To expire records and remove them from the Large List run the following command:
```bash
java -jar target/ldt-ttl-example-1.0.0-full.jar 
```
This will submit a Scan UDF Job on each node in the cluster, executing the supplied UDF on each record.

###Options
```bash
-h,--host <arg>       Server hostname (default: 127.0.0.1)
-d,--data             Load data.
-n,--namespace <arg>  Namespace (default: test)
-p,--port <arg>       Server port (default: 3000)
-u,--usage            Print usage.
```

##Discussion
The Java code is quite simple, in the `main()` method `refisterUDF()` is called to register the UDF located on the the class path. This is the code to check if the UDF is registered and register it:
```java
	public void registerUDF() {
		Node[] nodes = this.client.getNodes();
		String moduleString = Info.request(nodes[0], "udf-list");
		if (moduleString.isEmpty()
				|| !moduleString.contains("ldt_helper.lua")){ // register the udf module

			this.client.register(null, this.getClass().getClassLoader(), 
					"com/aerospike/examples/ldt/ttl/ldt_helper.lua", 
					"ldt_helper.lua", Language.LUA);
			
		}
	}
```

Based on the option from the command line the code will either load data or run the expirary.

```java
	if ( cl.hasOption("d")){
		as.generateData();
	} else {
		as.expire();
	}

```
The `generateData()` method creates 500 "top" records each with a Large List containing 500 elements or sub-records. The large List is stored in the bin named "list-bin". Each element in the Large List is a Map contain, among other things, a TTL.
```java
	public void generateData() {
		deleteData();
		Random rand = new Random();
		Random colourRand = new Random();
		for (int recCount = 1; recCount <= TOTAL_RECORDS; recCount++){
			/*
			 * generate top record
			 */
			Key key = new Key(this.namespace, this.set, "LDT_TTL:"+recCount);
			Bin name = new Bin("name", "LDT_TTL:"+recCount);
			this.client.put(null, key, name);
			LargeList llist = this.client.getLargeList(null, key, "list-bin");
			long currentTime = System.currentTimeMillis();
			
			for (int subCount = 1; subCount <= SUBS_PER_TOP; subCount++){
				/*
				 * generate sub records in a Large List
				 */
				try {
				Map<String, Object> theValue = new HashMap<String, Object>();
				theValue.put("key", "LDT_TTL:"+recCount+":"+subCount);
				
				long randomOffset = rand.nextInt(36000); // number between 0 - 10 minutes in seconds
				long ttlValue = currentTime/1000 + randomOffset;
				theValue.put("TTL", ttlValue);
				theValue.put("colour", color[colourRand.nextInt(color.length)]);
				llist.add(Value.get(theValue));
				} catch (AerospikeException e){
					if (e.getResultCode() != 1402){ //duplicate LDT key
						throw e;
					}
				}
			}
		}
	}
```
You can see the calculation to generate the TTL is the current time plus a random offset within the next 10 minutes, expressed in *seconds*
