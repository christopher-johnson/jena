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

package org.apache.jena.graph.impl;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractTest;

import static org.junit.Assert.*;

import static org.apache.jena.testing_framework.GraphHelper.*;
import org.apache.jena.graph.impl.TripleStore;

import org.xenei.junit.contract.IProducer;

/**
 * AbstractTestTripleStore - post-hoc tests for TripleStores.
 */

@Contract(TripleStore.class)
public class TripleStoreContractTest<T extends TripleStore> {

	protected TripleStore store;
	
	private IProducer<T> producer;

	public TripleStoreContractTest() {
	}

	/**
	 * Subclasses must over-ride to return a new empty TripleStore.
	 */
	@Contract.Inject
	public final void setTripleStoreContractTestProducer(IProducer<T> producer) {
		this.producer = producer;
	}

	@Before
	public final void beforeAbstractTripleStoreTest() {
		store = producer.newInstance();
	}

	@After
	public final void afterAbstractTripleStoreTest() {
		producer.cleanUp();
	}

	@ContractTest
	public void testEmpty() {
		testEmpty(store);
	}

	@ContractTest
	public void testAddOne() {
		store.add(GraphHelper.triple("x P y"));
		assertEquals(false, store.isEmpty());
		assertEquals(1, store.size());
		assertEquals(true, store.contains(GraphHelper.triple("x P y")));
		Assert.assertEquals(GraphHelper.nodeSet("x"), GraphHelper.iteratorToSet(store.listSubjects()));
		Assert.assertEquals(GraphHelper.nodeSet("y"), GraphHelper.iteratorToSet(store.listObjects()));
		Assert.assertEquals(
                        GraphHelper.tripleSet("x P y"),
				GraphHelper.iteratorToSet(store.find(GraphHelper.triple("?? ?? ??"))));
	}

	@ContractTest
	public void testListSubjects() {
		someStatements(store);
		Assert.assertEquals(GraphHelper.nodeSet("a x _z r q"), GraphHelper.iteratorToSet(store.listSubjects()));
	}

	@ContractTest
	public void testListObjects() {
		someStatements(store);
		Assert.assertEquals(
                        GraphHelper.nodeSet("b y i _j _t 17"),
				GraphHelper.iteratorToSet(store.listObjects()));
	}

	@ContractTest
	public void testContains() {
		someStatements(store);
		assertEquals(true, store.contains(GraphHelper.triple("a P b")));
		assertEquals(true, store.contains(GraphHelper.triple("x P y")));
		assertEquals(true, store.contains(GraphHelper.triple("a P i")));
		assertEquals(true, store.contains(GraphHelper.triple("_z Q _j")));
		assertEquals(true, store.contains(GraphHelper.triple("x R y")));
		assertEquals(true, store.contains(GraphHelper.triple("r S _t")));
		assertEquals(true, store.contains(GraphHelper.triple("q R 17")));
		/* */
		assertEquals(false, store.contains(GraphHelper.triple("a P x")));
		assertEquals(false, store.contains(GraphHelper.triple("a P _j")));
		assertEquals(false, store.contains(GraphHelper.triple("b Z r")));
		assertEquals(false, store.contains(GraphHelper.triple("_a P x")));
	}

	@ContractTest
	public void testFind() {
		someStatements(store);
		Assert.assertEquals(
                        GraphHelper.tripleSet(""),
				GraphHelper.iteratorToSet(store.find(GraphHelper.triple("no such thing"))));
		Assert.assertEquals(
                        GraphHelper.tripleSet("a P b; a P i"),
				GraphHelper.iteratorToSet(store.find(GraphHelper.triple("a P ??"))));
		Assert.assertEquals(
                        GraphHelper.tripleSet("a P b; x P y; a P i"),
				GraphHelper.iteratorToSet(store.find(GraphHelper.triple("?? P ??"))));
		Assert.assertEquals(
                        GraphHelper.tripleSet("x P y; x R y"),
				GraphHelper.iteratorToSet(store.find(GraphHelper.triple("x ?? y"))));
		Assert.assertEquals(
                        GraphHelper.tripleSet("_z Q _j"),
				GraphHelper.iteratorToSet(store.find(GraphHelper.triple("?? ?? _j"))));
		Assert.assertEquals(
                        GraphHelper.tripleSet("q R 17"),
				GraphHelper.iteratorToSet(store.find(GraphHelper.triple("?? ?? 17"))));
	}

	@ContractTest
	public void testRemove() {
		store.add(GraphHelper.triple("nothing before ace"));
		store.add(GraphHelper.triple("ace before king"));
		store.add(GraphHelper.triple("king before queen"));
		store.delete(GraphHelper.triple("ace before king"));
		Assert.assertEquals(
                        GraphHelper.tripleSet("king before queen; nothing before ace"),
				GraphHelper.iteratorToSet(store.find(GraphHelper.triple("?? ?? ??"))));
		store.delete(GraphHelper.triple("king before queen"));
		Assert.assertEquals(
                        GraphHelper.tripleSet("nothing before ace"),
				GraphHelper.iteratorToSet(store.find(GraphHelper.triple("?? ?? ??"))));
	}

	protected void someStatements(TripleStore ts) {
		ts.add(GraphHelper.triple("a P b"));
		ts.add(GraphHelper.triple("x P y"));
		ts.add(GraphHelper.triple("a P i"));
		ts.add(GraphHelper.triple("_z Q _j"));
		ts.add(GraphHelper.triple("x R y"));
		ts.add(GraphHelper.triple("r S _t"));
		ts.add(GraphHelper.triple("q R 17"));
	}

	protected void testEmpty(TripleStore ts) {
		assertEquals(true, ts.isEmpty());
		assertEquals(0, ts.size());
		assertEquals(false, ts.find(GraphHelper.triple("?? ?? ??")).hasNext());
		assertEquals(false, ts.listObjects().hasNext());
		assertEquals(false, ts.listSubjects().hasNext());
		assertFalse(ts.contains(GraphHelper.triple("x P y")));
	}
}
