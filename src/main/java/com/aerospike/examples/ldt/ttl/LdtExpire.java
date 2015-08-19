/* 
 * Copyright 2012-2015 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.examples.ldt.ttl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Info;
import com.aerospike.client.Key;
import com.aerospike.client.Language;
import com.aerospike.client.ResultCode;
import com.aerospike.client.Value;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.large.LargeList;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.query.Statement;


/**
@author Peter Milne
*/
public class LdtExpire {
	private static final int TOTAL_RECORDS = 500;
	private static final int SUBS_PER_TOP = 500;
	private AerospikeClient client;
	private String seedHost;
	private int port;
	private String namespace;
	private String set;
	private WritePolicy writePolicy;
	private Policy policy;
	protected String[] color = new String[]{"red","blue","green","yellow", "orange","brown"}; 

	private static Logger log = Logger.getLogger(LdtExpire.class);
	public LdtExpire(String host, int port, String namespace, String set) throws AerospikeException {
		this.client = new AerospikeClient(host, port);
		this.seedHost = host;
		this.port = port;
		this.namespace = namespace;
		this.set = set;
		this.writePolicy = new WritePolicy();
		this.policy = new Policy();
	}
	public LdtExpire(AerospikeClient client, String namespace, String set) throws AerospikeException {
		this.client = client;
		this.namespace = namespace;
		this.set = set;
		this.writePolicy = new WritePolicy();
		this.policy = new Policy();
	}
	public static void main(String[] args) throws AerospikeException {
		try {
			Options options = new Options();
			options.addOption("h", "host", true, "Server hostname (default: 127.0.0.1)");
			options.addOption("p", "port", true, "Server port (default: 3000)");
			options.addOption("n", "namespace", true, "Namespace (default: test)");
			options.addOption("s", "set", true, "Set (default: demo)");
			options.addOption("u", "usage", false, "Print usage.");
			options.addOption("d", "data", false, "Generate data.");

			CommandLineParser parser = new PosixParser();
			CommandLine cl = parser.parse(options, args, false);


			String host = cl.getOptionValue("h", "127.0.0.1");
			String portString = cl.getOptionValue("p", "3000");
			int port = Integer.parseInt(portString);
			String namespace = cl.getOptionValue("n", "test");
			String set = cl.getOptionValue("s", "demo");
			log.debug("Host: " + host);
			log.debug("Port: " + port);
			log.debug("Namespace: " + namespace);
			log.debug("Set: " + set);

			@SuppressWarnings("unchecked")
			List<String> cmds = cl.getArgList();
			if (cmds.size() == 0 && cl.hasOption("u")) {
				logUsage(options);
				return;
			}
			
			
			
			LdtExpire as = new LdtExpire(host, port, namespace, set);
			
			as.regusterUDF();
			
			if ( cl.hasOption("d")){
				as.generateData();
			} else {
				as.expire();
			}

		} catch (Exception e) {
			log.error("Critical error", e);
		}
	}
	public void generateData() {
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
				long ttlValue = currentTime + randomOffset + 1000;
				theValue.put("TTL", ttlValue);
				theValue.put("colour", color[colourRand.nextInt(color.length)]);
				llist.add(Value.get(theValue));
				} catch (AerospikeException e){
					if (e.getResultCode() != 1402){
						throw e;
					}
				}
			}
		}
	}
	/**
	 * Write usage to console.
	 */
	private static void logUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		String syntax = LdtExpire.class.getName() + " [<options>]";
		formatter.printHelp(pw, 100, syntax, "options:", options, 0, 2, null);
		log.info(sw.toString());
	}

	public void expire() throws Exception {
		/*
		 * prepare a statement with namespace and set
		 * and execute the ldf_helper UDF on each record.
		 */
		Statement stmt = new Statement();
		stmt.setNamespace(this.namespace);
		stmt.setSetName(this.namespace);
		this.client.execute(null, stmt, "ldt_helper", "expire");
	}
	public void regusterUDF() {
		Node[] nodes = this.client.getNodes();
		String moduleString = Info.request(nodes[0], "udf-list");
		if (moduleString.isEmpty()
				|| !moduleString.contains("as_utility.lua")){ // register the udf module

			this.client.register(null, this.getClass().getClassLoader(), 
					"com/aerospike/examples/ldt/ttl/ldt_helper.lua", 
					"ldt_helper.lua", Language.LUA);
			
		}
	}

}