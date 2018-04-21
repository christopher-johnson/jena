/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.graph.compose;

import static org.apache.jena.testing_framework.GraphHelper.*;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.runner.RunWith;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractImpl;
import org.xenei.junit.contract.ContractSuite;
import org.xenei.junit.contract.ContractTest;
import org.apache.jena.graph.Graph;
import org.apache.jena.testing_framework.AbstractGraphProducer;
import org.xenei.junit.contract.IProducer;

@RunWith(ContractSuite.class)
@ContractImpl(Difference.class)
public class DifferenceTest {

	protected IProducer<Difference> graphProducer;
	
	public DifferenceTest() {
		graphProducer = new AbstractGraphProducer<Difference>() {
			private Map<Graph, Graph[]> map = new HashMap<>();

			@Override
			protected Difference createNewGraph() {
				Graph g1 = GraphHelper.memGraph();
				Graph g2 = GraphHelper.memGraph();
				Difference d = new Difference(g1, g2);
				map.put(d, new Graph[] { g1, g2 });
				return d;
			}

			@Override
			public Graph[] getDependsOn(Graph d) {
				Graph[] dg = map.get(d);
				if (dg == null) {
					throw new IllegalStateException("graph not in map");
				}
				return dg;
			}

			@Override
			public Graph[] getNotDependsOn(Graph g) {
				return new Graph[] { GraphHelper.memGraph() };
			}

			@Override
			protected void afterClose(Graph g) {
				map.remove(g);
			}
		};
	}

	@Contract.Inject
	public final IProducer<Difference> getDifferenceTestProducer() {
		return graphProducer;
	}

	@ContractTest
	public void testDifference() {
		Graph g1 = GraphHelper.graphWith("x R y; p R q");
		Graph g2 = GraphHelper.graphWith("r Foo s; x R y");
		Graph d = new Difference(g1, g2);
		GraphHelper.assertOmits("Difference", d, "x R y");
		GraphHelper.assertContains("Difference", "p R q", d);
		GraphHelper.assertOmits("Difference", d, "r Foo s");
		if (d.size() != 1)
			fail("oops: size of difference is not 1");
		d.add(GraphHelper.triple("cats eat cheese"));
		GraphHelper.assertContains("Difference.L", "cats eat cheese", g1);
		GraphHelper.assertOmits("Difference.R", g2, "cats eat cheese");
	}

}
