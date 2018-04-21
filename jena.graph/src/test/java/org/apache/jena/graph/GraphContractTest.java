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

package org.apache.jena.graph;

import static org.apache.jena.testing_framework.GraphHelper.assertContainsAll;
import static org.apache.jena.testing_framework.GraphHelper.assertIsomorphic;
import static org.apache.jena.testing_framework.GraphHelper.assertOmitsAll;
import static org.apache.jena.testing_framework.GraphHelper.graphAddTxn;
import static org.apache.jena.testing_framework.GraphHelper.graphWith;
import static org.apache.jena.testing_framework.GraphHelper.iteratorToSet;
import static org.apache.jena.testing_framework.GraphHelper.memGraph;
import static org.apache.jena.testing_framework.GraphHelper.node;
import static org.apache.jena.testing_framework.GraphHelper.nodeSet;
import static org.apache.jena.testing_framework.GraphHelper.triple;
import static org.apache.jena.testing_framework.GraphHelper.tripleArray;
import static org.apache.jena.testing_framework.GraphHelper.tripleSet;
import static org.apache.jena.testing_framework.GraphHelper.txnBegin;
import static org.apache.jena.testing_framework.GraphHelper.txnRun;
import static org.apache.jena.testing_framework.GraphHelper.txnCommit;
import static org.apache.jena.testing_framework.GraphHelper.txnRollback;
import static org.apache.jena.testing_framework.TestUtils.assertDiffer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.jena.graph.impl.LiteralLabelFactory;
import org.apache.jena.mem.TrackingTripleIterator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.impl.ReifierStd;
import org.apache.jena.shared.ClosedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.testing_framework.AbstractGraphProducer;
import org.apache.jena.testing_framework.NodeCreateUtils;
import org.apache.jena.util.iterator.ClosableIterator;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.junit.After;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractTest;

/**
 * Graph contract test.
 */
@Contract(Graph.class)
public class GraphContractTest<T extends Graph>
{

	private static final Logger LOG = LoggerFactory
			.getLogger(GraphContractTest.class);

	private AbstractGraphProducer<T> producer;

	protected RecordingGraphListener GL = new RecordingGraphListener();

	@Contract.Inject
	public final void setGraphContractTestProducer(
			AbstractGraphProducer<T> graphProducer)
	{
		producer = graphProducer;
	}

	@After
	public final void afterGraphContractTest()
	{
		producer.cleanUp();
		GL.clear();
	}

	@ContractTest
	public void testAdd_Triple()
	{
		Graph graph = producer.newInstance();
		graph.getEventManager().register(GL);
		GraphHelper.txnBegin(graph);
		graph.add(GraphHelper.triple("S P O"));
		GraphHelper.txnCommit(graph);
		GL.assertHasStart("add", graph, GraphHelper.triple("S P O"));
		GraphHelper.txnRun(graph, () -> assertTrue("Graph should contain <S P O>",
				graph.contains(GraphHelper.triple("S P O"))));

	}

	/**
	 * Inference graphs can not be truly empty.
	 * 
	 * @param g
	 * @param b
	 */
	private void assertEmpty(Graph g, Graph b)
	{
		if (b.isEmpty())
		{
			assertTrue("Graph should be empty", g.isEmpty());
		} else
		{
			assertEquals("Graph should be in base state",
					b.find(Triple.ANY).toList(), g.find(Triple.ANY).toList());
		}
	}

	/**
	 * Inference graphs can not be truly empty
	 * 
	 * @param g
	 * @param b
	 */
	private void assertNotEmpty(Graph g, Graph b)
	{
		if (b.isEmpty())
		{
			assertFalse("Graph not should be empty", g.isEmpty());
		} else
		{
			assertNotEquals("Graph should not be in base state",
					b.find(Triple.ANY).toList(), g.find(Triple.ANY).toList());
		}
	}

	/**
	 * Test that clear works, in the presence of inferencing graphs that mean
	 * emptyness isn't available. This is why we go round the houses and test
	 * that expected ~= initialContent + addedStuff - removed - initialContent.
	 */
	@ContractTest
	public void testClear_Empty()
	{
		Graph graph = producer.newInstance();
		Graph base = copy(graph);

		graph.getEventManager().register(GL);
		GraphHelper.txnRun(graph, () -> graph.clear());

		GraphHelper.txnRun(graph, () -> assertEmpty(graph, base));
		GL.assertHasStart("someEvent", graph, GraphEvents.removeAll);
		GL.clear();
	}

	@ContractTest
	public void testClear()
	{
		Graph graph = producer.newInstance();
		Graph base = copy(graph);
		// test after adding
		GraphHelper.graphWith(graph, "S P O; S e:ff 27; _1 P P3; S4 P4 'en'");
		graph.getEventManager().register(GL);
		GraphHelper.txnRun(graph, () -> graph.clear());

		GraphHelper.txnRun(graph, () -> assertEmpty(graph, base));
		if (GL.contains("delete"))
		{
			// deletes are listed -- ensure all deletes are listed
			GL.assertContains("delete", graph, GraphHelper.triple("S P O"));
			GL.assertContains("delete", graph, GraphHelper.triple("S e:ff 27"));
			GL.assertContains("delete", graph, GraphHelper.triple("_1 P P3"));
			GL.assertContains("delete", graph, GraphHelper.triple("S4 P4 'en'"));
		}
		GL.assertHasEnd("someEvent", graph, GraphEvents.removeAll);
		GL.clear();

	}

	@ContractTest
	public void testClose()
	{
		Graph graph = GraphHelper.graphWith(producer.newInstance(),
				"S P O; S P2 O2; S3 P P3");
		graph.getEventManager().register(GL);
		assertFalse("Graph was constructed closed", graph.isClosed());

		GraphHelper.txnRun(graph, () -> graph.close());

		assertTrue("Graph should be closed", graph.isClosed());

		// exception may be thrown on begin or on execution.
		try
		{
			GraphHelper.txnBegin(graph);
			try
			{
				graph.add(GraphHelper.triple("S P O"));
				fail("added when closed");
			} catch (Exception expected)
			{
				GL.assertEmpty();
				// expected
			} finally
			{
				GraphHelper.txnRollback(graph);
			}
		} catch (Exception expected)
		{
			GL.assertEmpty();
			// expected
		}

		try
		{
			GraphHelper.txnBegin(graph);
			try
			{
				graph.delete(GraphHelper.triple("x R y"));
				fail("delete when closed");
			} catch (ClosedException c)
			{
				// Expected
			} finally
			{
				GraphHelper.txnRollback(graph);
				GL.assertEmpty();
			}
		} catch (Exception expected)
		{
			GL.assertEmpty();
			// expected
		}

		try
		{
			GraphHelper.txnBegin(graph);

			try
			{
				graph.add(GraphHelper.triple("x R y"));
				fail("add when closed");
			} catch (ClosedException c)
			{ /* as required */
			} finally
			{
				GraphHelper.txnRollback(graph);
				GL.assertEmpty();
			}
		} catch (Exception expected)
		{
			GL.assertEmpty();
			// expected
		}

		try
		{
			GraphHelper.txnBegin(graph);
			try
			{
				graph.contains(GraphHelper.triple("x R y"));
				fail("contains[triple] when closed");
			} catch (ClosedException c)
			{ /* as required */
			} finally
			{
				GraphHelper.txnRollback(graph);
				GL.assertEmpty();
			}
		} catch (Exception expected)
		{
			GL.assertEmpty();
			// expected
		}

		try
		{
			GraphHelper.txnBegin(graph);
			try
			{
				graph.contains(Node.ANY, Node.ANY, Node.ANY);
				fail("contains[SPO] when closed");
			} catch (ClosedException c)
			{ /* as required */
			} finally
			{
				GraphHelper.txnRollback(graph);
				GL.assertEmpty();
			}
		} catch (Exception expected)
		{
			GL.assertEmpty();
			// expected
		}

		try
		{
			GraphHelper.txnBegin(graph);
			try
			{
				graph.find(GraphHelper.triple("x R y"));
				fail("find [triple] when closed");
			} catch (ClosedException c)
			{ /* as required */
			} finally
			{
				GraphHelper.txnRollback(graph);
				GL.assertEmpty();
			}
		} catch (Exception expected)
		{
			GL.assertEmpty();
			// expected
		}

		try
		{
			GraphHelper.txnBegin(graph);
			try
			{
				graph.find(Node.ANY, Node.ANY, Node.ANY);
				fail("find[SPO] when closed");
			} catch (ClosedException c)
			{ /* as required */
			} finally
			{
				GraphHelper.txnRollback(graph);
				GL.assertEmpty();
			}
		} catch (Exception expected)
		{
			GL.assertEmpty();
			// expected
		}

		try
		{
			GraphHelper.txnBegin(graph);
			try
			{
				graph.size();
				fail("size when closed (" + this.getClass() + ")");
			} catch (ClosedException c)
			{ /* as required */
			} finally
			{
				GraphHelper.txnRollback(graph);
				GL.assertEmpty();
			}
		} catch (Exception expected)
		{
			GL.assertEmpty();
			// expected
		}
	}

	@ContractTest
	public void testContains_Node_Node_Node()
	{
		Graph graph = GraphHelper.graphWith(producer.newInstance(),
				"S P O; S2 P2 O2; S3 P3 O3");

		GraphHelper.txnRun(graph, () -> {
			assertTrue(graph.contains(GraphHelper.node("S"), GraphHelper.node("P"), GraphHelper.node("O")));
			assertFalse(graph.contains(GraphHelper.node("S"), GraphHelper.node("P"), GraphHelper.node("O2")));
			assertFalse(graph.contains(GraphHelper.node("S"), GraphHelper.node("P2"), GraphHelper.node("O")));
			assertFalse(graph.contains(GraphHelper.node("S2"), GraphHelper.node("P"), GraphHelper.node("O")));
			assertTrue(graph.contains(Node.ANY, Node.ANY, Node.ANY));
			assertTrue(graph.contains(Node.ANY, Node.ANY, GraphHelper.node("O")));
			assertTrue(graph.contains(Node.ANY, GraphHelper.node("P"), Node.ANY));
			assertTrue(graph.contains(GraphHelper.node("S"), Node.ANY, Node.ANY));
		});
	}

	@ContractTest
	public void testContains_Node_Node_Node_RepeatedSubjectDoesNotConceal()
	{

		Graph g = GraphHelper.graphWith(producer.newInstance(), "s P o; s Q r");
		Node s = GraphHelper.node("s");
		Node P = GraphHelper.node("P");
		Node o = GraphHelper.node("o");
		Node Q = GraphHelper.node("Q");
		Node r = GraphHelper.node("r");
		Node any = GraphHelper.node("??");
		GraphHelper.txnRun(g, () -> {
			assertTrue(g.contains(s, P, o));
			assertTrue(g.contains(s, Q, r));
			assertTrue(g.contains(any, P, o));
			assertTrue(g.contains(any, Q, r));
			assertTrue(g.contains(any, P, any));
			assertTrue(g.contains(any, Q, any));
		});
	}

	@ContractTest
	public void testContains_Node_Node_Node_ByValue()
	{
		Node x = GraphHelper.node("x");
		Node P = GraphHelper.node("P");
		Graph g1 = producer.newInstance();
		if (g1.getCapabilities().handlesLiteralTyping())
		{
			GraphHelper.graphWith(g1, "x P '1'xsd:integer");
			GraphHelper.txnRun(g1,
					() -> assertTrue(
							String.format(
									"literal type equality failed, does %s really implement literal typing",
									g1.getClass()),
							g1.contains(x, P, GraphHelper.node("'01'xsd:int"))));
			//
			Graph g2 = GraphHelper.graphWith(producer.newInstance(), "x P '1'xsd:int");
			GraphHelper.txnRun(g2, () -> {
				assertTrue("Literal equality with '1'xsd:integer failed",
						g2.contains(x, P, GraphHelper.node("'1'xsd:integer")));
			});
			//
			Graph g3 = GraphHelper.graphWith(producer.newInstance(), "x P '123'xsd:string");
			GraphHelper.txnRun(g3, () -> {
				assertTrue("Literal equality with '123' failed",
						g3.contains(x, P, GraphHelper.node("'123'")));
			});
		}
	}

	@ContractTest
	public void testContains_Node_Node_Node_Concrete()
	{
		Node s = GraphHelper.node("s");
		Node P = GraphHelper.node("P");
		Node o = GraphHelper.node("o");

		Node _x = GraphHelper.node("_x");
		Node R = GraphHelper.node("R");
		Node _y = GraphHelper.node("_y");

		Node x = GraphHelper.node("x");
		Node S = GraphHelper.node("S");

		Graph g = GraphHelper.graphWith(producer.newInstance(), "s P o; _x R _y; x S 0");
		GraphHelper.txnRun(g, () -> {

			assertTrue("Graph should have contained s P o",
					g.contains(s, P, o));
			assertTrue("Graph should have contained _x _R _y",
					g.contains(_x, R, _y));
			assertTrue("Graph should have contained x S 'O'",
					g.contains(x, S, GraphHelper.node("0")));
			/* */
			assertFalse(g.contains(s, P, GraphHelper.node("Oh")));
			assertFalse(g.contains(S, P, GraphHelper.node("O")));
			assertFalse(g.contains(s, GraphHelper.node("p"), o));
			assertFalse(g.contains(_x, GraphHelper.node("_r"), _y));
			assertFalse(g.contains(x, S, GraphHelper.node("1")));
		});
	}

	@ContractTest
	public void testContains_Node_Node_Node_Concrete_BlankPredicate()
	{
		Node s = GraphHelper.node("s");
		Node P = GraphHelper.node("P");
		Node o = GraphHelper.node("o");

		Node _x = GraphHelper.node("_x");
		Node _R = GraphHelper.node("_R");
		Node _y = GraphHelper.node("_y");

		Node x = GraphHelper.node("x");
		Node S = GraphHelper.node("S");

		Graph g = GraphHelper.graphWith(producer.newInstance(), "s P o; _x _R _y; x S 0");
		GraphHelper.txnRun(g, () -> {

			assertTrue("Graph should have contained _x _R _y",
					g.contains(_x, _R, _y));
			assertFalse(g.contains(_x, GraphHelper.node("_r"), _y));
		});
	}

	@ContractTest
	public void testContains_Node_Node_Node_Fluid()
	{
		Node x = GraphHelper.node("x");
		Node R = GraphHelper.node("R");
		Node P = GraphHelper.node("P");
		Node y = GraphHelper.node("y");
		Node a = GraphHelper.node("a");
		Node b = GraphHelper.node("b");
		Graph g = GraphHelper.graphWith(producer.newInstance(), "x R y; a P b");

		GraphHelper.txnRun(g, () -> {
			assertTrue(g.contains(Node.ANY, R, y));
			assertTrue(g.contains(x, Node.ANY, y));
			assertTrue(g.contains(x, R, Node.ANY));
			assertTrue(g.contains(Node.ANY, P, b));
			assertTrue(g.contains(a, Node.ANY, b));
			assertTrue(g.contains(a, P, Node.ANY));
			assertTrue(g.contains(Node.ANY, R, y));
			/* */
			assertFalse(g.contains(Node.ANY, R, b));
			assertFalse(g.contains(a, Node.ANY, y));
			assertFalse(g.contains(x, P, Node.ANY));
			assertFalse(g.contains(Node.ANY, R, x));
			assertFalse(g.contains(x, Node.ANY, R));
			assertFalse(g.contains(a, GraphHelper.node("S"), Node.ANY));
		});
	}

	@ContractTest
	public void testContains_Triple()
	{
		Graph graph = GraphHelper.graphWith(producer.newInstance(),
				"S P O; S2 P2 O2; S3 P3 O3");
		GraphHelper.txnRun(graph, () -> {
			assertTrue(graph.contains(GraphHelper.triple("S P O")));
			assertFalse(graph.contains(GraphHelper.triple("S P O2")));
			assertFalse(graph.contains(GraphHelper.triple("S P2 O")));
			assertFalse(graph.contains(GraphHelper.triple("S2 P O")));
			assertTrue(graph.contains(Triple.ANY));
			assertTrue(
					graph.contains(new Triple(Node.ANY, Node.ANY, GraphHelper.node("O"))));
			assertTrue(
					graph.contains(new Triple(Node.ANY, GraphHelper.node("P"), Node.ANY)));
			assertTrue(
					graph.contains(new Triple(GraphHelper.node("S"), Node.ANY, Node.ANY)));
		});

	}

	@ContractTest
	public void testContains_Triple_RepeatedSubjectDoesNotConceal()
	{

		Graph g = GraphHelper.graphWith(producer.newInstance(), "s P o; s Q r");
		GraphHelper.txnRun(g, () -> {
			assertTrue(g.contains(GraphHelper.triple("s P o")));
			assertTrue(g.contains(GraphHelper.triple("s Q r")));
			assertTrue(g.contains(GraphHelper.triple("?? P o")));
			assertTrue(g.contains(GraphHelper.triple("?? Q r")));
			assertTrue(g.contains(GraphHelper.triple("?? P ??")));
			assertTrue(g.contains(GraphHelper.triple("?? Q ??")));
		});
	}

	@ContractTest
	public void testContains_Triple_ByValue()
	{
		Graph g1 = producer.newInstance();
		if (g1.getCapabilities().handlesLiteralTyping())
		{
			GraphHelper.graphWith(g1, "x P '1'xsd:integer");
			GraphHelper.txnRun(g1, () -> {
				assertTrue(
						String.format(
								"did not find x P '01'xsd:int, does %s really implement literal typing",
								g1.getClass()),
						g1.contains(GraphHelper.triple("x P '01'xsd:int")));
			});
			//
			Graph g2 = GraphHelper.graphWith(producer.newInstance(), "x P '1'xsd:int");
			GraphHelper.txnRun(g2, () -> {
				assertTrue("did not find x P '1'xsd:integer",
						g2.contains(GraphHelper.triple("x P '1'xsd:integer")));
			});
			//
			Graph g3 = GraphHelper.graphWith(producer.newInstance(), "x P '123'xsd:string");
			GraphHelper.txnRun(g3, () -> assertTrue("did not find x P '123'xsd:string",
					g3.contains(GraphHelper.triple("x P '123'"))));
		}
	}

	@ContractTest
	public void testContains_Triple_Concrete()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "s P o; _x R _y; x S 0");
		GraphHelper.txnRun(g, () -> {
			assertTrue(g.contains(GraphHelper.triple("s P o")));
			assertTrue(g.contains(GraphHelper.triple("_x R _y")));
			assertTrue(g.contains(GraphHelper.triple("x S 0")));
			/* */
			assertFalse(g.contains(GraphHelper.triple("s P Oh")));
			assertFalse(g.contains(GraphHelper.triple("S P O")));
			assertFalse(g.contains(GraphHelper.triple("s p o")));
			assertFalse(g.contains(GraphHelper.triple("_x _r _y")));
			assertFalse(g.contains(GraphHelper.triple("x S 1")));
		});
	}

	@ContractTest
	public void testContains_Triple_Concrete_BlankPredicate()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "s P o; _x _R _y; x S 0");
		GraphHelper.txnRun(g, () -> {
			assertTrue(g.contains(GraphHelper.triple("s P o")));
			assertTrue(g.contains(GraphHelper.triple("_x _R _y")));
			assertTrue(g.contains(GraphHelper.triple("x S 0")));
			/* */
			assertFalse(g.contains(GraphHelper.triple("s P Oh")));
			assertFalse(g.contains(GraphHelper.triple("S P O")));
			assertFalse(g.contains(GraphHelper.triple("s p o")));
			assertFalse(g.contains(GraphHelper.triple("_x _r _y")));
			assertFalse(g.contains(GraphHelper.triple("x S 1")));
		});
	}

	@ContractTest
	public void testContains_Triple_Fluid()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "x R y; a P b");
		GraphHelper.txnRun(g, () -> {

			assertTrue(g.contains(GraphHelper.triple("?? R y")));
			assertTrue(g.contains(GraphHelper.triple("x ?? y")));
			assertTrue(g.contains(GraphHelper.triple("x R ??")));
			assertTrue(g.contains(GraphHelper.triple("?? P b")));
			assertTrue(g.contains(GraphHelper.triple("a ?? b")));
			assertTrue(g.contains(GraphHelper.triple("a P ??")));
			assertTrue(g.contains(GraphHelper.triple("?? R y")));
			/* */
			assertFalse(g.contains(GraphHelper.triple("?? R b")));
			assertFalse(g.contains(GraphHelper.triple("a ?? y")));
			assertFalse(g.contains(GraphHelper.triple("x P ??")));
			assertFalse(g.contains(GraphHelper.triple("?? R x")));
			assertFalse(g.contains(GraphHelper.triple("x ?? R")));
			assertFalse(g.contains(GraphHelper.triple("a S ??")));
		});
	}

	/**
	 * Inference graphs can not be empty
	 */
	@ContractTest
	public void testDelete_Triple()
	{
		Graph graph = GraphHelper.graphWith(producer.newInstance(),
				"S P O; S2 P2 O2; S3 P3 O3");
		Graph base = producer.newInstance();
		graph.getEventManager().register(GL);

		try
		{
			GraphHelper.txnBegin(graph);
			graph.delete(GraphHelper.triple("S P O"));
			GraphHelper.txnCommit(graph);
		} catch (DeleteDeniedException expected)
		{
			GraphHelper.txnRollback(graph);
			fail("delete( S P O ) failed: " + expected.getMessage());
		}

		GL.assertContains("delete", graph, GraphHelper.triple("S P O"));

		GraphHelper.txnRun(graph, () -> {
			assertFalse("Graph should not contain <S P O>",
					graph.contains(GraphHelper.triple("S P O")));
			assertNotEmpty(graph, base);
			assertTrue("Graph should contain <S2 P2 O2>",
					graph.contains(GraphHelper.triple("S2 P2 O2")));
			assertTrue("Graph should contain <S3 P3 O3>",
					graph.contains(GraphHelper.triple("S3 P3 O3")));
		});
	}

	@ContractTest
	public void testDelete_Triple_Wildcard()
	{
		Graph graph = GraphHelper.graphWith(producer.newInstance(),
				"S P O; S2 P2 O2; S3 P3 O3");
		Graph base = producer.newInstance();
		graph.getEventManager().register(GL);

		// should not modify anything on wildcard delete
		GL.clear();
		try
		{
			GraphHelper.txnBegin(graph);
			graph.delete(new Triple(GraphHelper.node("S2"), GraphHelper.node("P2"), Node.ANY));
			GraphHelper.txnCommit(graph);
		} catch (DeleteDeniedException expected)
		{
			GraphHelper.txnRollback(graph);
		}
		GraphHelper.txnRun(graph, () -> {

			assertTrue("Graph should contain <S2 P2 O2>",
					graph.contains(GraphHelper.triple("S2 P2 O2")));
			assertTrue("Graph should contain <S3 P3 O3>",
					graph.contains(GraphHelper.triple("S3 P3 O3")));
		});
		GL.assertHas("delete", graph,
				new Triple(GraphHelper.node("S2"), GraphHelper.node("P2"), Node.ANY));
	}

	@ContractTest
	public void testDelete_Triple_FromNothing()
	{
		Graph g = producer.newInstance();
		g.getEventManager().register(GL);
		GraphHelper.txnBegin(g);
		g.delete(GraphHelper.triple("quint rdf:subject S"));
		GraphHelper.txnCommit(g);
		GL.assertContains("delete", g, GraphHelper.triple("quint rdf:subject S"));
	}

	@ContractTest
	public void testDependsOn()
	{
		Graph g = producer.newInstance();
		Graph[] depGraphs = producer.getDependsOn(g);
		if (depGraphs != null)
		{
			for (Graph dg : depGraphs)
			{
				assertTrue(
						String.format("Graph %s should depend upon %s", g, dg),
						g.dependsOn(dg));
			}
		}
		depGraphs = producer.getNotDependsOn(g);
		if (depGraphs != null)
		{
			for (Graph dg : depGraphs)
			{
				assertFalse(String.format("Graph %s should not depend upon %s",
						g, dg), g.dependsOn(dg));
			}
		}
	}

	@ContractTest
	public void testFind_Node_Node_Node()
	{
		Graph graph = GraphHelper.graphWith(producer.newInstance(),
				"S P O; S2 P2 O2; S3 P3 O3");
		GraphHelper.txnBegin(graph);
		List<Triple> s = graph.find(Node.ANY, Node.ANY, Node.ANY).toList();
		assertEquals(3, s.size());
		List<Triple> expected = Arrays.asList(new Triple[] { GraphHelper.triple("S P O"),
				GraphHelper.triple("S2 P2 O2"), GraphHelper.triple("S3 P3 O3") });
		assertTrue("Missing some values",
				expected.containsAll(s) && s.containsAll(expected));

		s = graph.find(GraphHelper.node("S"), Node.ANY, Node.ANY).toList();
		assertEquals(1, s.size());
		assertTrue("Missing some values", s.contains(GraphHelper.triple("S P O")));

		s = graph.find(Node.ANY, GraphHelper.node("P"), Node.ANY).toList();
		assertEquals(1, s.size());
		assertTrue("Missing some values", s.contains(GraphHelper.triple("S P O")));

		s = graph.find(Node.ANY, Node.ANY, GraphHelper.node("O")).toList();
		assertEquals(1, s.size());
		assertTrue("Missing some values", s.contains(GraphHelper.triple("S P O")));

		s = graph.find(GraphHelper.node("S2"), GraphHelper.node("P2"), GraphHelper.node("O2")).toList();
		assertEquals(1, s.size());
		assertTrue("Missing some values", s.contains(GraphHelper.triple("S2 P2 O2")));

		s = graph.find(GraphHelper.node("S2"), GraphHelper.node("P3"), GraphHelper.node("O2")).toList();
		assertEquals(0, s.size());

		s = graph.find(Node.ANY, GraphHelper.node("P3"), GraphHelper.node("O2")).toList();
		assertEquals(0, s.size());

		s = graph.find(GraphHelper.node("S3"), Node.ANY, GraphHelper.node("O2")).toList();
		assertEquals(0, s.size());

		s = graph.find(GraphHelper.node("S3"), GraphHelper.node("P2"), Node.ANY).toList();
		assertEquals(0, s.size());
		GraphHelper.txnRollback(graph);
	}

	@ContractTest
	public void testFind_Node_Node_Node_ByFluidTriple()
	{
		Node x = GraphHelper.node("x");
		Node y = GraphHelper.node("y");
		Node z = GraphHelper.node("z");
		Graph g = GraphHelper.graphWith(producer.newInstance(), "x y z ");
		Set<Triple> expect = GraphHelper.tripleSet("x y z");
		GraphHelper.txnBegin(g);
		assertEquals(expect, g.find(Node.ANY, y, z).toSet());
		assertEquals(expect, g.find(x, Node.ANY, z).toSet());
		assertEquals(expect, g.find(x, y, Node.ANY).toSet());
		GraphHelper.txnRollback(g);
	}

	@ContractTest
	public void testFind_Node_Node_Node_ProgrammaticValues()
	{
		Graph g = producer.newInstance();
		if (g.getCapabilities().handlesLiteralTyping())
		{
			Node ab = NodeFactory.createLiteral(LiteralLabelFactory
					.createTypedLiteral(new Byte((byte) 42)));
			Node as = NodeFactory.createLiteral(LiteralLabelFactory
					.createTypedLiteral(new Short((short) 42)));
			Node ai = NodeFactory.createLiteral(
					LiteralLabelFactory.createTypedLiteral(new Integer(42)));
			Node al = NodeFactory.createLiteral(
					LiteralLabelFactory.createTypedLiteral(new Long(42)));

			Node SB = NodeCreateUtils.create("SB");
			Node SS = NodeCreateUtils.create("SS");
			Node SI = NodeCreateUtils.create("SI");
			Node SL = NodeCreateUtils.create("SL");
			Node P = NodeCreateUtils.create("P");

			GraphHelper.txnBegin(g);
			try
			{
				g.add(Triple.create(SB, P, ab));
				g.add(Triple.create(SS, P, as));
				g.add(Triple.create(SI, P, ai));
				g.add(Triple.create(SL, P, al));
			} catch (Exception e)
			{
				GraphHelper.txnRollback(g);
				fail(e.getMessage());
			}
			GraphHelper.txnCommit(g);
			GraphHelper.txnBegin(g);
			Assert.assertEquals(
					String.format(
							"Should have found 4 elements, does %s really implement literal typing",
							g.getClass()),
					4,
					GraphHelper.iteratorToSet(
							g.find(Node.ANY, P, NodeCreateUtils.create("42")))
                                                   .size());
			GraphHelper.txnRollback(g);
		}
	}

	@ContractTest
	public void testFind_Node_Node_Node_MatchLanguagedLiteralCaseInsensitive()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "a p 'chat'en");
		if (g.getCapabilities().handlesLiteralTyping())
		{
			Node chaten = GraphHelper.node("'chat'en"), chatEN = GraphHelper.node("'chat'EN");
			TestUtils.assertDiffer(chaten, chatEN);
			assertTrue(chaten.sameValueAs(chatEN));
			assertEquals(chaten.getIndexingValue(), chatEN.getIndexingValue());
			GraphHelper.txnBegin(g);
			assertEquals(1, g.find(Node.ANY, Node.ANY, chaten).toList().size());
			assertEquals(1, g.find(Node.ANY, Node.ANY, chatEN).toList().size());
			GraphHelper.txnRollback(g);
		}
	}

	@ContractTest
	public void testFind_Node_Node_Node_NoMatchAgainstUnlanguagesLiteral()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "a p 'chat'en; a p 'chat'");
		if (g.getCapabilities().handlesLiteralTyping())
		{
			Node chaten = GraphHelper.node("'chat'en"), chatEN = GraphHelper.node("'chat'EN");
			TestUtils.assertDiffer(chaten, chatEN);
			assertTrue(chaten.sameValueAs(chatEN));
			assertEquals(chaten.getIndexingValue(), chatEN.getIndexingValue());
			GraphHelper.txnBegin(g);
			assertEquals(1, g.find(Node.ANY, Node.ANY, chaten).toList().size());
			assertEquals(1, g.find(Node.ANY, Node.ANY, chatEN).toList().size());
			GraphHelper.txnRollback(g);
		}
	}

	@ContractTest
	public void testFind_Triple()
	{
		Graph graph = GraphHelper.graphWith(producer.newInstance(),
				"S P O; S2 P2 O2; S3 P3 O3");
		GraphHelper.txnBegin(graph);
		List<Triple> s = graph.find(Triple.ANY).toList();
		assertEquals(3, s.size());
		List<Triple> expected = Arrays.asList(new Triple[] { GraphHelper.triple("S P O"),
				GraphHelper.triple("S2 P2 O2"), GraphHelper.triple("S3 P3 O3") });
		assertTrue("Missing some values", expected.containsAll(s));

		s = graph.find(new Triple(GraphHelper.node("S"), Node.ANY, Node.ANY)).toList();
		assertEquals(1, s.size());
		assertTrue("Missing some values", s.contains(GraphHelper.triple("S P O")));

		s = graph.find(new Triple(Node.ANY, GraphHelper.node("P"), Node.ANY)).toList();
		assertEquals(1, s.size());
		assertTrue("Missing some values", s.contains(GraphHelper.triple("S P O")));

		s = graph.find(new Triple(Node.ANY, Node.ANY, GraphHelper.node("O"))).toList();
		assertEquals(1, s.size());
		assertTrue("Missing some values", s.contains(GraphHelper.triple("S P O")));

		s = graph.find(new Triple(GraphHelper.node("S2"), GraphHelper.node("P2"), GraphHelper.node("O2"))).toList();
		assertEquals(1, s.size());
		assertTrue("Missing some values", s.contains(GraphHelper.triple("S2 P2 O2")));

		s = graph.find(new Triple(GraphHelper.node("S2"), GraphHelper.node("P3"), GraphHelper.node("O2"))).toList();
		assertEquals(0, s.size());

		s = graph.find(new Triple(Node.ANY, GraphHelper.node("P3"), GraphHelper.node("O2"))).toList();
		assertEquals(0, s.size());

		s = graph.find(new Triple(GraphHelper.node("S3"), Node.ANY, GraphHelper.node("O2"))).toList();
		assertEquals(0, s.size());

		s = graph.find(new Triple(GraphHelper.node("S3"), GraphHelper.node("P2"), Node.ANY)).toList();
		assertEquals(0, s.size());
		GraphHelper.txnRollback(graph);
	}

	@ContractTest
	public void testFind_Triple_ByFluidTriple()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "x y z ");
		Set<Triple> expect = GraphHelper.tripleSet("x y z");
		GraphHelper.txnBegin(g);
		assertEquals(expect, g.find(GraphHelper.triple("?? y z")).toSet());
		assertEquals(expect, g.find(GraphHelper.triple("x ?? z")).toSet());
		assertEquals(expect, g.find(GraphHelper.triple("x y ??")).toSet());
		GraphHelper.txnRollback(g);
	}

	@ContractTest
	public void testFind_Triple_ProgrammaticValues()
	{
		Graph g = producer.newInstance();
		if (g.getCapabilities().handlesLiteralTyping())
		{
			Node ab = NodeFactory.createLiteral(LiteralLabelFactory
					.createTypedLiteral(new Byte((byte) 42)));
			Node as = NodeFactory.createLiteral(LiteralLabelFactory
					.createTypedLiteral(new Short((short) 42)));
			Node ai = NodeFactory.createLiteral(
					LiteralLabelFactory.createTypedLiteral(new Integer(42)));
			Node al = NodeFactory.createLiteral(
					LiteralLabelFactory.createTypedLiteral(new Long(42)));

			Node SB = NodeCreateUtils.create("SB");
			Node SS = NodeCreateUtils.create("SS");
			Node SI = NodeCreateUtils.create("SI");
			Node SL = NodeCreateUtils.create("SL");
			Node P = NodeCreateUtils.create("P");

			GraphHelper.txnBegin(g);
			try
			{
				g.add(Triple.create(SB, P, ab));
				g.add(Triple.create(SS, P, as));
				g.add(Triple.create(SI, P, ai));
				g.add(Triple.create(SL, P, al));
			} catch (Exception e)
			{
				GraphHelper.txnRollback(g);
				fail(e.getMessage());
			}
			GraphHelper.txnCommit(g);
			GraphHelper.txnBegin(g);
			Assert.assertEquals(
					String.format(
							"Should have found 4 elements, does %s really implement literal typing",

							g.getClass()),
					4, GraphHelper.iteratorToSet(g.find(new Triple(Node.ANY, P,
							NodeCreateUtils.create("42")))).size());
			GraphHelper.txnRollback(g);
		}
	}

	@ContractTest
	public void testFind_Triple_MatchLanguagedLiteralCaseInsensitive()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "a p 'chat'en");
		if (g.getCapabilities().handlesLiteralTyping())
		{
			Node chaten = GraphHelper.node("'chat'en"), chatEN = GraphHelper.node("'chat'EN");
			TestUtils.assertDiffer(chaten, chatEN);
			assertTrue(chaten.sameValueAs(chatEN));
			assertEquals(chaten.getIndexingValue(), chatEN.getIndexingValue());
			GraphHelper.txnBegin(g);
			assertEquals(1, g.find(new Triple(Node.ANY, Node.ANY, chaten))
					.toList().size());
			assertEquals(1, g.find(new Triple(Node.ANY, Node.ANY, chatEN))
					.toList().size());
			GraphHelper.txnRollback(g);
		}
	}

	@ContractTest
	public void testFind_Triple_NoMatchAgainstUnlanguagesLiteral()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "a p 'chat'en; a p 'chat'");
		if (g.getCapabilities().handlesLiteralTyping())
		{
			Node chaten = GraphHelper.node("'chat'en"), chatEN = GraphHelper.node("'chat'EN");
			TestUtils.assertDiffer(chaten, chatEN);
			assertTrue(chaten.sameValueAs(chatEN));
			assertEquals(chaten.getIndexingValue(), chatEN.getIndexingValue());
			GraphHelper.txnBegin(g);
			assertEquals(1, g.find(new Triple(Node.ANY, Node.ANY, chaten))
					.toList().size());
			assertEquals(1, g.find(new Triple(Node.ANY, Node.ANY, chatEN))
					.toList().size());
			GraphHelper.txnRollback(g);
		}
	}

	@ContractTest
	public void testGetCapabilities()
	{
		Graph g = producer.newInstance();
		Capabilities c = g.getCapabilities();
		assertNotNull("Capabilities are not returned", c);
		try
		{
			c.sizeAccurate();
		} catch (Exception e)
		{
			fail("sizeAccurate() threw Exception: " + e.toString());
		}
		try
		{
			c.addAllowed();
		} catch (Exception e)
		{
			fail("addAllowed() threw Exception: " + e.toString());
		}
		try
		{
			c.deleteAllowed();
		} catch (Exception e)
		{
			fail("deleteAllowed() threw Exception: " + e.toString());
		}
	}

	@ContractTest
	public void testGetEventManager()
	{
		assertNotNull("Must return an EventManager",
				producer.newInstance().getEventManager());
	}

	@ContractTest
	public void testGetPrefixMapping()
	{
		Graph g = producer.newInstance();
		GraphHelper.txnBegin(g);
		PrefixMapping pm = g.getPrefixMapping();
		assertNotNull("Must return prefix mapping", pm);
		assertSame("getPrefixMapping must always return the same object", pm,
				g.getPrefixMapping());
		GraphHelper.txnRollback(g);
		pm.setNsPrefix("pfx1", "http://example.com/");
		pm.setNsPrefix("pfx2", "scheme:rope/string#");

		GraphHelper.txnBegin(g);
		// assert same after adding to other mapl
		assertSame("getPrefixMapping must always return the same object", pm,
				g.getPrefixMapping());
		GraphHelper.txnRollback(g);

	}

	@ContractTest
	public void testGetStatisticsHandler()
	{
		Graph g = producer.newInstance();
		GraphStatisticsHandler sh = g.getStatisticsHandler();
		if (sh != null)
		{
			assertSame(
					"getStatisticsHandler must always return the same object",
					sh, g.getStatisticsHandler());
		}
	}

	@ContractTest
	public void testGetTransactionHandler()
	{
		Graph g = producer.newInstance();
		assertNotNull("Must return a Transaction handler",
				g.getTransactionHandler());
	}

	@ContractTest
	public void testIsClosed()
	{
		Graph g = producer.newInstance();
		assertFalse("Graph created in closed state", g.isClosed());
		GraphHelper.txnBegin(g);
		g.close();
		GraphHelper.txnCommit(g);
		GraphHelper.txnBegin(g);
		assertTrue("Graph does not report closed state after close called",
				g.isClosed());
		GraphHelper.txnRollback(g);
	}

	@ContractTest
	public void testIsEmpty()
	{
		Graph g = producer.newInstance();
		GraphHelper.txnBegin(g);
		if (!g.isEmpty())
		{
			LOG.warn(String.format(
					"Graph type %s can not be empty (Empty test skipped)",
					g.getClass()));
			GraphHelper.txnRollback(g);
		} else
		{
			GraphHelper.txnRollback(g);
			GraphHelper.graphAddTxn(g, "S P O");
			GraphHelper.txnBegin(g);
			assertFalse("Graph reports empty after add", g.isEmpty());
			GraphHelper.txnRollback(g);

			GraphHelper.txnBegin(g);
			g.add(NodeCreateUtils.createTriple("Foo B C"));
			g.delete(NodeCreateUtils.createTriple("S P O"));
			GraphHelper.txnCommit(g);
			GraphHelper.txnBegin(g);
			assertFalse("Should not report empty", g.isEmpty());
			GraphHelper.txnRollback(g);

			GraphHelper.txnBegin(g);
			g.delete(NodeCreateUtils.createTriple("Foo B C"));
			GraphHelper.txnCommit(g);
			GraphHelper.txnBegin(g);
			assertTrue("Should report empty after all entries deleted",
					g.isEmpty());
			GraphHelper.txnRollback(g);
		}
	}

	@ContractTest
	public void testIsIsomorphicWith_Graph()
	{
		Graph graph = producer.newInstance();
		Graph g2 = GraphHelper.memGraph();
		GraphHelper.txnBegin(graph);
		assertTrue("Empty graphs should be isomorphic",
				graph.isIsomorphicWith(g2));
		GraphHelper.txnRollback(graph);
		GraphHelper.graphWith(graph, "S P O; S2 P2 O2; S3 P3 O3");
		g2 = GraphHelper.graphWith("S3 P3 O3; S2 P2 O2; S P O");
		GraphHelper.txnBegin(graph);
		assertTrue("Should be isomorphic", graph.isIsomorphicWith(g2));
		GraphHelper.txnRollback(graph);
		GraphHelper.txnBegin(graph);
		graph.add(GraphHelper.triple("_1, P4 S4"));
		GraphHelper.txnCommit(graph);

		GraphHelper.txnBegin(g2);
		g2.add(GraphHelper.triple("_2, P4 S4"));
		GraphHelper.txnCommit(g2);
		GraphHelper.txnBegin(graph);
		assertTrue("Should be isomorphic after adding anonymous nodes",
				graph.isIsomorphicWith(g2));
		GraphHelper.txnRollback(graph);

		GraphHelper.txnBegin(graph);
		graph.add(GraphHelper.triple("_1, P3 S4"));
		GraphHelper.txnCommit(graph);

		GraphHelper.txnBegin(g2);
		g2.add(GraphHelper.triple("_2, P4 S4"));
		GraphHelper.txnCommit(g2);
		GraphHelper.txnBegin(graph);
		assertFalse("Should not be isomorphic", graph.isIsomorphicWith(g2));
		GraphHelper.txnRollback(graph);
	}

	private Graph copy(Graph g)
	{
		Graph result = producer.newInstance();
		GraphHelper.txnBegin(result);
		GraphUtil.addInto(result, g);
		GraphHelper.txnCommit(result);
		return result;
	}

	private Graph remove(Graph toUpdate, Graph toRemove)
	{
		GraphHelper.txnBegin(toUpdate);
		GraphUtil.deleteFrom(toUpdate, toRemove);
		GraphHelper.txnCommit(toUpdate);
		return toUpdate;
	}

	/**
	 * Test that remove(s, p, o) works, in the presence of inferencing graphs
	 * that mean emptyness isn't available. This is why we go round the houses
	 * and test that expected ~= initialContent + addedStuff - removed -
	 * initialContent.
	 */
	@ContractTest
	public void testRemove_Node_Node_Node()
	{
		for (int i = 0; i < cases.length; i += 1)
			for (int j = 0; j < 3; j += 1)
			{
				Graph content = producer.newInstance();

				Graph baseContent = copy(content);
				GraphHelper.graphAddTxn(content, cases[i][0]);
				Triple remove = GraphHelper.triple(cases[i][1]);
				Graph expected = GraphHelper.graphWith(cases[i][2]);
				Triple[] removed = GraphHelper.tripleArray(cases[i][3]);
				content.getEventManager().register(GL);
				GL.clear();
				GraphHelper.txnBegin(content);
				content.remove(remove.getSubject(), remove.getPredicate(),
						remove.getObject());
				GraphHelper.txnCommit(content);

				// check for optional delete notifications
				if (GL.contains("delete"))
				{
					// if it contains any it must contain all.
					for (Triple t : removed)
					{
						GL.assertContains("delete", content, t);
					}
				}
				GL.assertHasEnd("someEvent", content,
						GraphEvents.remove(remove.getSubject(),
								remove.getPredicate(), remove.getObject()));

				content.getEventManager().unregister(GL);
				Graph finalContent = remove(copy(content), baseContent);
				GraphHelper.txnBegin(finalContent);
				GraphHelper.assertIsomorphic(cases[i][1], expected, finalContent);
				GraphHelper.txnRollback(finalContent);
			}
	}

	@ContractTest
	public void testRemove_ByIterator()
	{
		testRemove("?? ?? ??", "?? ?? ??");
		testRemove("S ?? ??", "S ?? ??");
		testRemove("S ?? ??", "?? P ??");
		testRemove("S ?? ??", "?? ?? O");
		testRemove("?? P ??", "S ?? ??");
		testRemove("?? P ??", "?? P ??");
		testRemove("?? P ??", "?? ?? O");
		testRemove("?? ?? O", "S ?? ??");
		testRemove("?? ?? O", "?? P ??");
		testRemove("?? ?? O", "?? ?? O");
	}

	private void testRemove(String findRemove, String findCheck)
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "S P O");
		GraphHelper.txnBegin(g);
		ExtendedIterator<Triple> it = g
				.find(NodeCreateUtils.createTriple(findRemove));
		GraphHelper.txnRollback(g);
		try
		{
			it.next();
			it.remove();
			it.close();
			assertEquals("remove with " + findRemove + ":", 0, g.size());
			assertFalse(g.contains(NodeCreateUtils.createTriple(findCheck)));
		} catch (UnsupportedOperationException e)
		{
			it.close();
		}
	}

	/**
	 * This test case was generated by Ian and was caused by GraphMem not
	 * keeping up with changes to the find interface.
	 */
	@ContractTest
	public void testFindAndContains()
	{
		Graph g = producer.newInstance();
		Node r = NodeCreateUtils.create("r"), s = NodeCreateUtils.create("s"),
				p = NodeCreateUtils.create("P");
		GraphHelper.txnBegin(g);
		try
		{
			g.add(Triple.create(r, p, s));
			GraphHelper.txnCommit(g);
			GraphHelper.txnBegin(g);
			assertTrue(g.contains(r, p, Node.ANY));
			assertEquals(1, g.find(r, p, Node.ANY).toList().size());
		} catch (Exception e)
		{
			fail(e.getMessage());
		} finally
		{
			GraphHelper.txnRollback(g);
		}
	}

	/**
	 * Check that contains respects by-value semantics.
	 */

	@ContractTest
	public void testAGraph()
	{
		String title = this.getClass().getName();
		Graph g = producer.newInstance();
		GraphHelper.txnBegin(g);
		int baseSize = g.size();
		GraphHelper.txnRollback(g);
		GraphHelper.graphAddTxn(g, "x R y; p S q; a T b");
		/* */
		GraphHelper.txnBegin(g);
		GraphHelper.assertContainsAll(title + ": simple graph", g, "x R y; p S q; a T b");
		assertEquals(title + ": size", baseSize + 3, g.size());
		GraphHelper.txnRollback(g);

		GraphHelper.graphAddTxn(g,
				"spindizzies lift cities; Diracs communicate instantaneously");
		GraphHelper.txnBegin(g);
		assertEquals(title + ": size after adding", baseSize + 5, g.size());

		g.delete(GraphHelper.triple("x R y"));
		g.delete(GraphHelper.triple("a T b"));
		GraphHelper.txnCommit(g);
		GraphHelper.txnBegin(g);
		assertEquals(title + ": size after deleting", baseSize + 3, g.size());
		GraphHelper.assertContainsAll(title + ": modified simple graph", g,
				"p S q; spindizzies lift cities; Diracs communicate instantaneously");
		GraphHelper.assertOmitsAll(title + ": modified simple graph", g, "x R y; a T b");
		/* */
		ClosableIterator<Triple> it = g.find(Node.ANY, GraphHelper.node("lift"), Node.ANY);
		assertTrue(title + ": finds some triple(s)", it.hasNext());
		assertEquals(title + ": finds a 'lift' triple",
				GraphHelper.triple("spindizzies lift cities"), it.next());
		assertFalse(title + ": finds exactly one triple", it.hasNext());
		GraphHelper.txnRollback(g);
		it.close();
	}

	@ContractTest
	public void testAddWithReificationPreamble()
	{
		Graph g = producer.newInstance();
		GraphHelper.txnBegin(g);
		xSPO(g);
		GraphHelper.txnCommit(g);
		GraphHelper.txnBegin(g);
		assertFalse(g.isEmpty());
		GraphHelper.txnRollback(g);
	}

	protected void xSPOyXYZ(Graph g)
	{
		xSPO(g);
		ReifierStd.reifyAs(g, NodeCreateUtils.create("y"),
				NodeCreateUtils.createTriple("X Y Z"));
	}

	protected void aABC(Graph g)
	{
		ReifierStd.reifyAs(g, NodeCreateUtils.create("a"),
				NodeCreateUtils.createTriple("Foo B C"));
	}

	protected void xSPO(Graph g)
	{
		ReifierStd.reifyAs(g, NodeCreateUtils.create("x"),
				NodeCreateUtils.createTriple("S P O"));
	}

	@ContractTest
	public void failingTestDoubleRemoveAll()
	{
		final Graph g = producer.newInstance();
		try
		{
			GraphHelper.graphAddTxn(g, "c S d; e:ff GGG hhhh; _i J 27; Ell Em 'en'");
			GraphHelper.txnBegin(g);
			Iterator<Triple> it = new TrackingTripleIterator(g.find(Triple.ANY))
			{
				@Override
				public void remove()
				{
					super.remove(); // removes current
					g.delete(current); // no-op.
				}
			};
			GraphHelper.txnRollback(g);
			while (it.hasNext())
			{
				it.next();
				it.remove();
			}
			GraphHelper.txnBegin(g);
			assertTrue(g.isEmpty());
			GraphHelper.txnRollback(g);
		} catch (UnsupportedOperationException e)
		{
			// No Iterator.remove
		}
	}

	/**
	 * Test cases for RemoveSPO(); each entry is a triple (add, remove, result).
	 * <ul>
	 * <li>add - the triples to add to the graph to start with
	 * <li>remove - the pattern to use in the removal
	 * <li>result - the triples that should remain in the graph
	 * </ul>
	 */
	protected static String[][] cases = { { "x R y", "x R y", "", "x R y" },
			{ "x R y; a P b", "x R y", "a P b", "x R y" },
			{ "x R y; a P b", "?? R y", "a P b", "x R y" },
			{ "x R y; a P b", "x R ??", "a P b", "x R y" },
			{ "x R y; a P b", "x ?? y", "a P b", "x R y" },
			{ "x R y; a P b", "?? ?? ??", "", "x R y; a P b" },
			{ "x R y; a P b; c P d", "?? P ??", "x R y", "a P b; c P d" },
			{ "x R y; a P b; x S y", "x ?? ??", "a P b", "x R y; x S y" }, };

	/**
	 * testIsomorphism from file data
	 * 
	 * @throws URISyntaxException
	 * @throws MalformedURLException
	 */
	@ContractTest
	public void testIsomorphismFile()
			throws URISyntaxException, MalformedURLException
	{
		testIsomorphismXMLFile(1, true);
		testIsomorphismXMLFile(2, true);
		testIsomorphismXMLFile(3, true);
		testIsomorphismXMLFile(4, true);
		testIsomorphismXMLFile(5, false);
		testIsomorphismXMLFile(6, false);
		testIsomorphismNTripleFile(7, true);
		testIsomorphismNTripleFile(8, false);

	}

	private void testIsomorphismNTripleFile(int i, boolean result)
	{
		testIsomorphismFile(i, "N-TRIPLE", "nt", result);
	}

	private void testIsomorphismXMLFile(int i, boolean result)
	{
		testIsomorphismFile(i, "RDF/XML", "rdf", result);
	}

	private InputStream getInputStream(int n, int n2, String suffix)
	{
		String urlStr = String.format("regression/testModelEquals/%s-%s.%s", n,
				n2, suffix);
		return GraphContractTest.class.getClassLoader()
				.getResourceAsStream(urlStr);
	}

	private void testIsomorphismFile(int n, String lang, String suffix,
			boolean result)
	{
		Graph g1 = producer.newInstance();
		Graph g2 = producer.newInstance();
		Model m1 = ModelFactory.createModelForGraph(g1);
		Model m2 = ModelFactory.createModelForGraph(g2);

		GraphHelper.txnBegin(g1);
		m1.read(getInputStream(n, 1, suffix), "http://www.example.org/", lang);
		GraphHelper.txnCommit(g1);

		GraphHelper.txnBegin(g2);
		m2.read(getInputStream(n, 2, suffix), "http://www.example.org/", lang);
		GraphHelper.txnCommit(g2);

		GraphHelper.txnBegin(g1);
		boolean rslt = g1.isIsomorphicWith(g2) == result;
		GraphHelper.txnRollback(g1);
		if (!rslt)
		{
			System.out.println("g1:");
			m1.write(System.out, "N-TRIPLE");
			System.out.println("g2:");
			m2.write(System.out, "N-TRIPLE");
		}
		assertTrue("Isomorphism test failed", rslt);
	}

	protected Graph getClosed()
	{
		Graph result = producer.newInstance();
		result.close();
		return result;
	}

	// @ContractTest
	// public void testTransactionCommit()
	// {
	// Graph g = producer.newInstance();
	// if (g.getTransactionHandler().transactionsSupported())
	// {
	// Graph initial = graphWithTxn( "initial hasValue 42; also hasURI hello" );
	// Graph extra = graphWithTxn( "extra hasValue 17; also hasURI world" );
	// //File foo = FileUtils.tempFileName( "fileGraph", ".nt" );
	//
	// //Graph g = new FileGraph( foo, true, true );
	//
	// GraphUtil.addInto( g, initial );
	// g.getTransactionHandler().begin();
	// GraphUtil.addInto( g, extra );
	// g.getTransactionHandler().commit();
	// Graph union = graphWithTxn( "" );
	// GraphUtil.addInto(union, initial );
	// GraphUtil.addInto(union, extra );
	// assertIsomorphic( union, g );
	// //Model inFile = ModelFactory.createDefaultModel();
	// //inFile.read( "file:///" + foo, "N-TRIPLES" );
	// //assertIsomorphic( union, inFile.getGraph() );
	// }
	// }
	//
	// @ContractTest
	// public void testTransactionAbort()
	// {
	// Graph g = producer.newInstance();
	// if (g.getTransactionHandler().transactionsSupported())
	// {
	// Graph initial = graphWithTxn( "initial hasValue 42; also hasURI hello" );
	// Graph extra = graphWithTxn( "extra hasValue 17; also hasURI world" );
	// File foo = FileUtils.tempFileName( "fileGraph", ".n3" );
	// //Graph g = new FileGraph( foo, true, true );
	// GraphUtil.addInto( g, initial );
	// g.getTransactionHandler().begin();
	// GraphUtil.addInto( g, extra );
	// g.getTransactionHandler().abort();
	// assertIsomorphic( initial, g );
	// }
	// }
	//
	// @ContractTest
	// public void testTransactionCommitThenAbort()
	// {
	// Graph g = producer.newInstance();
	// if (g.getTransactionHandler().transactionsSupported())
	// {
	// Graph initial = graphWithTxn( "Foo pings B; B pings C" );
	// Graph extra = graphWithTxn( "C pingedBy B; fileGraph rdf:type Graph" );
	// //Graph g = producer.newInstance();
	// //File foo = FileUtils.tempFileName( "fileGraph", ".nt" );
	// //Graph g = new FileGraph( foo, true, true );
	// g.getTransactionHandler().begin();
	// GraphUtil.addInto( g, initial );
	// g.getTransactionHandler().commit();
	// g.getTransactionHandler().begin();
	// GraphUtil.addInto( g, extra );
	// g.getTransactionHandler().abort();
	// assertIsomorphic( initial, g );
	// //Model inFile = ModelFactory.createDefaultModel();
	// // inFile.read( "file:///" + foo, "N-TRIPLES" );
	// //assertIsomorphic( initial, inFile.getGraph() );
	// }
	// }

	/**
	 * This test exposed that the update-existing-graph functionality was broken
	 * if the target graph already contained any statements with a subject S
	 * appearing as subject in the source graph - no further Spo statements were
	 * added.
	 */
	@ContractTest
	public void testPartialUpdate()
	{
		Graph source = GraphHelper.graphWith(producer.newInstance(), "a R b; b S e");
		Graph dest = GraphHelper.graphWith(producer.newInstance(), "b R d");
		GraphHelper.txnBegin(source);
		try
		{
			GraphExtract e = new GraphExtract(TripleBoundary.stopNowhere);
			e.extractInto(dest, GraphHelper.node("a"), source);
			GraphHelper.txnCommit(source);
		} catch (RuntimeException e)
		{
			GraphHelper.txnRollback(source);
			e.printStackTrace();
			fail(e.getMessage());

		}
		GraphHelper.txnBegin(source);
		GraphHelper.assertIsomorphic(GraphHelper.graphWith("a R b; b S e; b R d"), dest);
		GraphHelper.txnRollback(source);

	}

	/**
	 * Ensure that triples removed by calling .remove() on the iterator returned
	 * by a find() will generate deletion notifications.
	 */
	@ContractTest
	public void testIterator_Remove()
	{
		Graph graph = GraphHelper.graphWith(producer.newInstance(), "a R b; b S e");
		graph.getEventManager().register(GL);
		GraphHelper.txnBegin(graph);
		Triple toRemove = GraphHelper.triple("a R b");
		ExtendedIterator<Triple> rtr = graph.find(toRemove);
		assertTrue("ensure a(t least) one triple", rtr.hasNext());
		GraphHelper.txnRollback(graph);
		try
		{
			rtr.next();
			rtr.remove();
			rtr.close();
			GL.assertHas("delete", graph, toRemove);
		} catch (UnsupportedOperationException e)
		{
			// No Iterator.remove
		}
	}

	@ContractTest
	public void testTransactionHandler_Commit()
	{
		Graph g = producer.newInstance();
		if (g.getTransactionHandler().transactionsSupported())
		{
			Graph initial = GraphHelper.graphWith("initial hasValue 42; also hasURI hello");
			Graph extra = GraphHelper.graphWith("extra hasValue 17; also hasURI world");
			g.getTransactionHandler().begin();
			GraphUtil.addInto(g, initial);
			g.getTransactionHandler().commit();
			g.getTransactionHandler().begin();
			GraphUtil.addInto(g, extra);
			g.getTransactionHandler().commit();
			Graph union = GraphHelper.memGraph();
			GraphUtil.addInto(union, initial);
			GraphUtil.addInto(union, extra);
			g.getTransactionHandler().begin();
			GraphHelper.assertIsomorphic(union, g);
			g.getTransactionHandler().abort();
			// Model inFiIProducer<TransactionHandler>le =
			// ModelFactory.createDefaultModel();
			// inFile.read( "file:///" + foo, "N-TRIPLES" );
			// assertIsomorphic( union, inFile.getGraph() );
		}
	}

	@ContractTest
	public void testTransactionHandler_Abort()
	{
		Graph g = producer.newInstance();
		if (g.getTransactionHandler().transactionsSupported())
		{
			Graph initial = GraphHelper.graphWith(producer.newInstance(),
					"initial hasValue 42; also hasURI hello");
			Graph extra = GraphHelper.graphWith(producer.newInstance(),
					"extra hasValue 17; also hasURI world");
			g.getTransactionHandler().begin();
			GraphUtil.addInto(g, initial);
			g.getTransactionHandler().commit();

			g.getTransactionHandler().begin();
			GraphUtil.addInto(g, extra);
			g.getTransactionHandler().abort();

			g.getTransactionHandler().begin();
			GraphHelper.assertIsomorphic(initial, g);
			g.getTransactionHandler().abort();
		}
	}

	@ContractTest
	public void testTransactionHandler_CommitThenAbort()
	{
		Graph g = producer.newInstance();
		if (g.getTransactionHandler().transactionsSupported())
		{
			Graph initial = GraphHelper.graphWith(producer.newInstance(),
					"Foo pings B; B pings C");
			Graph extra = GraphHelper.graphWith(producer.newInstance(),
					"C pingedBy B; fileGraph rdf:type Graph");
			g.getTransactionHandler().begin();
			GraphUtil.addInto(g, initial);
			g.getTransactionHandler().commit();
			g.getTransactionHandler().begin();
			GraphUtil.addInto(g, extra);
			g.getTransactionHandler().abort();
			g.getTransactionHandler().begin();
			GraphHelper.assertIsomorphic(initial, g);
			g.getTransactionHandler().abort();
			// Model inFile = ModelFactory.createDefaultModel();
			// inFile.read( "file:///" + foo, "N-TRIPLES" );
			// assertIsomorphic( initial, inFile.getGraph() );
		}
	}

	//
	// Test that literal typing works when supported
	//

	// used to find the object set from the returned set for literal testing

	private static final Function<Triple, Node> getObject = new Function<Triple, Node>()
	{
		@Override
		public Node apply(Triple t)
		{
			return t.getObject();
		}
	};

	private void testLiteralTypingBasedFind(final String data, final int size,
			final String search, final String results, boolean reqLitType)
	{

		Graph g = producer.newInstance();

		if (!reqLitType || g.getCapabilities().handlesLiteralTyping())
		{
			GraphHelper.graphWith(g, data);

			Node literal = NodeCreateUtils.create(search);
			//
			GraphHelper.txnBegin(g);
			assertEquals("graph has wrong size", size, g.size());
			Set<Node> got = g.find(Node.ANY, Node.ANY, literal)
					.mapWith(getObject).toSet();
			Assert.assertEquals(GraphHelper.nodeSet(results), got);
			GraphHelper.txnRollback(g);
		}
	}

	@ContractTest
	public void testLiteralTypingBasedFind()
	{
		testLiteralTypingBasedFind("a P 'simple'", 1, "'simple'", "'simple'",
				false);
		testLiteralTypingBasedFind("a P 'simple'xsd:string", 1, "'simple'",
				"'simple'xsd:string", true);
		testLiteralTypingBasedFind("a P 'simple'", 1, "'simple'xsd:string",
				"'simple'", true);
		// ensure that adding identical strings one with type yields single
		// result
		// and that querying with or without type works
		testLiteralTypingBasedFind("a P 'simple'xsd:string", 1,
				"'simple'xsd:string", "'simple'xsd:string", false);
		testLiteralTypingBasedFind("a P 'simple'; a P 'simple'xsd:string", 1,
				"'simple'", "'simple'xsd:string", true);
		testLiteralTypingBasedFind("a P 'simple'; a P 'simple'xsd:string", 1,
				"'simple'xsd:string", "'simple'", true);
		testLiteralTypingBasedFind("a P 'simple'; a P 'simple'xsd:string", 1,
				"'simple'", "'simple'", true);
		testLiteralTypingBasedFind("a P 'simple'; a P 'simple'xsd:string", 1,
				"'simple'xsd:string", "'simple'xsd:string", true);
		testLiteralTypingBasedFind("a P 1", 1, "1", "1", false);
		testLiteralTypingBasedFind("a P '1'xsd:float", 1, "'1'xsd:float",
				"'1'xsd:float", false);
		testLiteralTypingBasedFind("a P '1'xsd:double", 1, "'1'xsd:double",
				"'1'xsd:double", false);
		testLiteralTypingBasedFind("a P '1'xsd:float", 1, "'1'xsd:float",
				"'1'xsd:float", false);
		testLiteralTypingBasedFind("a P '1.1'xsd:float", 1, "'1'xsd:float", "",
				false);
		testLiteralTypingBasedFind("a P '1'xsd:double", 1, "'1'xsd:int", "",
				false);
		testLiteralTypingBasedFind("a P 'abc'rdf:XMLLiteral", 1, "'abc'", "",
				false);
		testLiteralTypingBasedFind("a P 'abc'", 1, "'abc'rdf:XMLLiteral", "",
				false);
		//
		// floats & doubles are not compatible
		//
		testLiteralTypingBasedFind("a P '1'xsd:float", 1, "'1'xsd:double", "",
				false);
		testLiteralTypingBasedFind("a P '1'xsd:double", 1, "'1'xsd:float", "",
				false);
		testLiteralTypingBasedFind("a P 1", 1, "'1'", "", false);
		testLiteralTypingBasedFind("a P 1", 1, "'1'xsd:integer",
				"'1'xsd:integer", false);
		testLiteralTypingBasedFind("a P 1", 1, "'1'", "", false);
		testLiteralTypingBasedFind("a P '1'xsd:short", 1, "'1'xsd:integer",
				"'1'xsd:short", true);
		testLiteralTypingBasedFind("a P '1'xsd:int", 1, "'1'xsd:integer",
				"'1'xsd:int", true);
	}

	@ContractTest
	public void testQuadRemove()
	{
		Graph g = producer.newInstance();
		GraphHelper.txnBegin(g);
		assertEquals(0, g.size());
		GraphHelper.txnRollback(g);
		Triple s = GraphHelper.triple("x rdf:subject s");
		Triple p = GraphHelper.triple("x rdf:predicate p");
		Triple o = GraphHelper.triple("x rdf:object o");
		Triple t = GraphHelper.triple("x rdf:type rdf:Statement");
		GraphHelper.txnBegin(g);
		g.add(s);
		g.add(p);
		g.add(o);
		g.add(t);
		GraphHelper.txnCommit(g);
		GraphHelper.txnBegin(g);
		assertEquals(4, g.size());
		GraphHelper.txnRollback(g);
		GraphHelper.txnBegin(g);
		g.delete(s);
		g.delete(p);
		g.delete(o);
		g.delete(t);
		GraphHelper.txnCommit(g);
		GraphHelper.txnBegin(g);
		assertEquals(0, g.size());
		GraphHelper.txnRollback(g);
	}

	@ContractTest
	public void testSizeAfterRemove()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "x p y");
		try
		{
			GraphHelper.txnBegin(g);
			ExtendedIterator<Triple> it = g.find(GraphHelper.triple("x ?? ??"));
			GraphHelper.txnRollback(g);
			it.removeNext();
			GraphHelper.txnBegin(g);
			assertEquals(0, g.size());
			GraphHelper.txnRollback(g);
		} catch (UnsupportedOperationException e)
		{
			// No Iterator.remove
		}
	}

	@ContractTest
	public void testSingletonStatisticsWithSingleTriple()
	{

		Graph g = GraphHelper.graphWith(producer.newInstance(), "a P b");
		GraphStatisticsHandler h = g.getStatisticsHandler();
		if (h != null)
		{
			assertEquals(1L, h.getStatistic(GraphHelper.node("a"), Node.ANY, Node.ANY));
			assertEquals(0L, h.getStatistic(GraphHelper.node("x"), Node.ANY, Node.ANY));
			//
			assertEquals(1L, h.getStatistic(Node.ANY, GraphHelper.node("P"), Node.ANY));
			assertEquals(0L, h.getStatistic(Node.ANY, GraphHelper.node("Q"), Node.ANY));
			//
			assertEquals(1L, h.getStatistic(Node.ANY, Node.ANY, GraphHelper.node("b")));
			assertEquals(0L, h.getStatistic(Node.ANY, Node.ANY, GraphHelper.node("y")));
		}
	}

	@ContractTest
	public void testSingletonStatisticsWithSeveralTriples()
	{

		Graph g = GraphHelper.graphWith(producer.newInstance(),
				"a P b; a P c; a Q b; x S y");
		GraphStatisticsHandler h = g.getStatisticsHandler();
		if (h != null)
		{
			assertEquals(3L, h.getStatistic(GraphHelper.node("a"), Node.ANY, Node.ANY));
			assertEquals(1L, h.getStatistic(GraphHelper.node("x"), Node.ANY, Node.ANY));
			assertEquals(0L, h.getStatistic(GraphHelper.node("y"), Node.ANY, Node.ANY));
			//
			assertEquals(2L, h.getStatistic(Node.ANY, GraphHelper.node("P"), Node.ANY));
			assertEquals(1L, h.getStatistic(Node.ANY, GraphHelper.node("Q"), Node.ANY));
			assertEquals(0L, h.getStatistic(Node.ANY, GraphHelper.node("R"), Node.ANY));
			//
			assertEquals(2L, h.getStatistic(Node.ANY, Node.ANY, GraphHelper.node("b")));
			assertEquals(1L, h.getStatistic(Node.ANY, Node.ANY, GraphHelper.node("c")));
			assertEquals(0L, h.getStatistic(Node.ANY, Node.ANY, GraphHelper.node("d")));
		}
	}

	@ContractTest
	public void testDoubletonStatisticsWithTriples()
	{

		Graph g = GraphHelper.graphWith(producer.newInstance(),
				"a P b; a P c; a Q b; x S y");
		GraphStatisticsHandler h = g.getStatisticsHandler();
		if (h != null)
		{
			assertEquals(-1L, h.getStatistic(GraphHelper.node("a"), GraphHelper.node("P"), Node.ANY));
			assertEquals(-1L, h.getStatistic(Node.ANY, GraphHelper.node("P"), GraphHelper.node("b")));
			assertEquals(-1L, h.getStatistic(GraphHelper.node("a"), Node.ANY, GraphHelper.node("b")));
			//
			assertEquals(0L, h.getStatistic(GraphHelper.node("no"), GraphHelper.node("P"), Node.ANY));
		}
	}

	@ContractTest
	public void testStatisticsWithOnlyVariables()
	{
		testStatsWithAllVariables("");
		testStatsWithAllVariables("a P b");
		testStatsWithAllVariables("a P b; a P c");
		testStatsWithAllVariables("a P b; a P c; a Q b; x S y");
	}

	private void testStatsWithAllVariables(String triples)
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), triples);
		GraphStatisticsHandler h = g.getStatisticsHandler();
		if (h != null)
		{
			assertEquals(g.size(),
					h.getStatistic(Node.ANY, Node.ANY, Node.ANY));
		}
	}

	@ContractTest
	public void testStatsWithConcreteTriple()
	{
		testStatsWithConcreteTriple(0, "x P y", "");
	}

	private void testStatsWithConcreteTriple(int expect, String triple,
			String graph)
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), graph);
		GraphStatisticsHandler h = g.getStatisticsHandler();
		if (h != null)
		{
			Triple t = GraphHelper.triple(triple);
			assertEquals(expect, h.getStatistic(t.getSubject(),
					t.getPredicate(), t.getObject()));
		}
	}

	@ContractTest
	public void testBrokenIndexes()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "x R y; x S z");
		try
		{
			GraphHelper.txnBegin(g);
			ExtendedIterator<Triple> it = g.find(Node.ANY, Node.ANY, Node.ANY);
			GraphHelper.txnRollback(g);
			it.removeNext();
			it.removeNext();
			GraphHelper.txnBegin(g);
			assertFalse(g.find(GraphHelper.node("x"), Node.ANY, Node.ANY).hasNext());
			assertFalse(g.find(Node.ANY, GraphHelper.node("R"), Node.ANY).hasNext());
			assertFalse(g.find(Node.ANY, Node.ANY, GraphHelper.node("y")).hasNext());
			GraphHelper.txnRollback(g);
		} catch (UnsupportedOperationException e)
		{
			// No Iterator.remove
		}
	}

	@ContractTest
	public void testBrokenSubject()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "x brokenSubject y");
		try
		{
			GraphHelper.txnBegin(g);
			ExtendedIterator<Triple> it = g.find(GraphHelper.node("x"), Node.ANY, Node.ANY);
			GraphHelper.txnRollback(g);
			it.removeNext();
			GraphHelper.txnBegin(g);
			assertFalse(g.find(Node.ANY, Node.ANY, Node.ANY).hasNext());
			GraphHelper.txnRollback(g);
		} catch (UnsupportedOperationException e)
		{
			// No Iterator.remove
		}
	}

	@ContractTest
	public void testBrokenPredicate()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "x brokenPredicate y");
		try
		{
			GraphHelper.txnBegin(g);
			ExtendedIterator<Triple> it = g.find(Node.ANY,
					GraphHelper.node("brokenPredicate"), Node.ANY);
			GraphHelper.txnRollback(g);
			it.removeNext();
			GraphHelper.txnBegin(g);
			assertFalse(g.find(Node.ANY, Node.ANY, Node.ANY).hasNext());
			GraphHelper.txnRollback(g);
		} catch (UnsupportedOperationException e)
		{
			// No Iterator.remove
		}
	}

	@ContractTest
	public void testBrokenObject()
	{
		Graph g = GraphHelper.graphWith(producer.newInstance(), "x brokenObject y");

		try
		{
			GraphHelper.txnBegin(g);
			ExtendedIterator<Triple> it = g.find(Node.ANY, Node.ANY, GraphHelper.node("y"));
			GraphHelper.txnRollback(g);
			it.removeNext();
			GraphHelper.txnBegin(g);
			assertFalse(g.find(Node.ANY, Node.ANY, Node.ANY).hasNext());
			GraphHelper.txnRollback(g);

		} catch (UnsupportedOperationException e)
		{
			// No Iterator.remove
		}

	}

}
