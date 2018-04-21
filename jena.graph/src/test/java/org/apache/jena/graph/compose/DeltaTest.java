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
@ContractImpl(Delta.class)
public class DeltaTest  {

	protected IProducer<Delta> graphProducer;
	
	public DeltaTest() {
		super();
		graphProducer = new AbstractGraphProducer<Delta>() {
			private Map<Graph, Graph> map = new HashMap<>();

			@Override
			protected Delta createNewGraph() {
				Graph g = GraphHelper.memGraph();
				Delta d = new Delta(g);
				map.put(d, g);
				return d;
			}

			@Override
			public Graph[] getDependsOn(Graph d) {
//				Delta dl = (Delta)d;
//				Graph g = map.get(d);
//				if (g == null) {
//					throw new IllegalStateException("graph missing from map");
//				}
//				return new Graph[] { g,  (Graph)dl.getL(), (Graph)dl.getR() };
				return null;
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
	public final IProducer<Delta> getDeltaTestProducer() {
		return graphProducer;
	}

	@ContractTest
	public void testDelta() {
		Graph x = GraphHelper.graphWith(getDeltaTestProducer().newInstance(), "x R y");
		GraphHelper.assertContains("x", "x R y", x);
		x.delete(GraphHelper.triple("x R y"));
		GraphHelper.assertOmits("x", x, "x R y");
		/* */
		Graph base = GraphHelper.graphWith("x R y; p S q; I like cheese; pins pop balloons");
		Delta delta = new Delta(base);
		GraphHelper.assertContainsAll("Delta", delta,
				"x R y; p S q; I like cheese; pins pop balloons");
		GraphHelper.assertContainsAll("Base", base,
				"x R y; p S q; I like cheese; pins pop balloons");
		/* */
		delta.add(GraphHelper.triple("pigs fly winglessly"));
		delta.delete(GraphHelper.triple("I like cheese"));
		/* */
		GraphHelper.assertContainsAll("changed Delta", delta,
				"x R y; p S q; pins pop balloons; pigs fly winglessly");
		GraphHelper.assertOmits("changed delta", delta, "I like cheese");
		GraphHelper.assertContains("delta additions", "pigs fly winglessly",
				delta.getAdditions());
		GraphHelper.assertOmits("delta additions", delta.getAdditions(), "I like cheese");
		GraphHelper.assertContains("delta deletions", "I like cheese", delta.getDeletions());
		GraphHelper.assertOmits("delta deletions", delta.getDeletions(),
				"pigs fly winglessly");
	}

}
