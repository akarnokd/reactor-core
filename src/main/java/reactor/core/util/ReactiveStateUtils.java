/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import reactor.core.flow.Loopback;
import reactor.core.flow.MultiProducer;
import reactor.core.flow.MultiReceiver;
import reactor.core.flow.Producer;
import reactor.core.flow.Receiver;
import reactor.core.state.Backpressurable;
import reactor.core.state.Cancellable;
import reactor.core.state.Completable;
import reactor.core.state.Introspectable;
import reactor.core.state.Prefetchable;
import reactor.core.state.Requestable;
import reactor.core.state.Timeable;

/**
 * Navigate and introspect
 * {@link Loopback}, {@link Receiver}, {@link MultiReceiver}, {@link MultiProducer} and {@link Producer}. The scan
 * will produce a {@link ReactiveStateUtils.Graph} representation of a data flow, used for monitoring, debugging or
 * contextual informations.
 *
 * @author Stephane Maldini
 * @since 2.5
 */
public enum ReactiveStateUtils {
	;

	/**
	 * Create an empty graph
	 * @return a new Graph
	 */
	public static Graph createGraph() {
		return createGraph(false);
	}

	/**
	 * Create an empty graph
	 * @param trace force introspection on components presenting the option {@link Introspectable#TRACE_ONLY}
	 * @return a new Graph
	 */
	public static Graph createGraph(boolean trace) {
		return new Graph(false, trace);
	}

	/**
	 * Create a "Nodes" and "Links" complete representation of a given component if available
	 * @return a new Graph
	 */
	public static Graph scan(Object o) {
		return scan(o, false);
	}

	/**
	 * Create a "Nodes" and "Links" complete representation of a given component if available
	 * @return a new Graph
	 */
	public static Graph scan(Object o, boolean trace) {
		if (o == null) {
			return null;
		}
		if (Graph.class.equals(o.getClass())) {
			return (Graph) o;
		}

		Graph graph = new Graph(false, trace);
		Node origin = graph.expandReactiveSate(o, null);
		graph.addUpstream(origin, null);
		graph.addDownstream(origin, null);

		return graph;
	}

	/**
	 * Create a "Nodes" and "Links" downstream representation of a given component if available
	 * @return a Graph
	 */
	public static Graph subscan(Object o) {
		return subscan(o, false);
	}

	/**
	 * Create a "Nodes" and "Links" downstream representation of a given component if available
	 * @return a Graph
	 */
	public static Graph subscan(Object o, boolean trace) {
		if (o == null) {
			return null;
		}

		if (Graph.class.equals(o.getClass())) {
			return (Graph) o;
		}

		Graph graph = new Graph(true, trace);
		Node root = graph.expandReactiveSate(o, null);
		graph.addDownstream(root, null);

		return graph;
	}

	/**
	 * Introspect the passed object and return a JSON representation as a String. Indent and line carriages will be
	 * added to the result.
	 *
	 * @param o the candidate object
	 * @return a JSON result
	 */
	public static String prettyPrint(Object o) {
		return print(o, true);
	}

	/**
	 * Introspect the passed object and return a JSON representation as a String.
	 *
	 * @param o the candidate object
	 * @return a JSON result
	 */
	public static String print(Object o) {
		return print(o, false);
	}

	/**
	 * Introspect the passed object and return a JSON representation as a String.
	 *
	 * @param o the candidate object
	 * @param prettyPrint true if indent and line return should be inserted
	 *
	 * @return a JSON result
	 */
	public static String print(Object o, boolean prettyPrint) {
		if (o == null) {
			return null;
		}
		Node n = new Node(getName(o), getIdOrDefault(o), o, null);
		if (prettyPrint) {
			return n.toJSON();
		}
		else {
			return n.toString();
		}
	}

	/**
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Receiver}
	 */
	public static boolean hasUpstream(Object o) {
		return reactiveStateCheck(o, Receiver.class) && ((Receiver) o).upstream() != null;
	}

	/**
	 * @param o candidate instance
	 * @return true if the tested instance is {@link MultiReceiver}
	 */
	public static boolean hasUpstreams(Object o) {
		return reactiveStateCheck(o, MultiReceiver.class);
	}

	/**
	 *
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Producer}
	 */
	public static boolean hasDownstream(Object o) {
		return reactiveStateCheck(o, Producer.class) && ((Producer) o).downstream() != null;
	}

	/**
	 * @param o candidate instance
	 * @return true if the tested instance is {@link MultiProducer}
	 */
	public static boolean hasDownstreams(Object o) {
		return reactiveStateCheck(o, MultiProducer.class);
	}

	/**
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Loopback}
	 */
	public static boolean hasFeedbackLoop(Object o) {
		return reactiveStateCheck(o, Loopback.class);
	}

	/**
	 *
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Introspectable#TRACE_ONLY}
	 */
	public static boolean isTraceOnly(Object o) {
		return reactiveStateCheck(o, Introspectable.class) &&
				(((Introspectable)o).getMode() & Introspectable.TRACE_ONLY) == Introspectable.TRACE_ONLY;
	}

	/**
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Completable}
	 */
	public static boolean hasSubscription(Object o) {
		return reactiveStateCheck(o, Completable.class);
	}

	/**
	 *
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Cancellable}
	 */
	public static boolean isCancellable(Object o) {
		return reactiveStateCheck(o, Cancellable.class);
	}

	/**
	 *
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Introspectable#INNER}
	 */
	public static boolean isContained(Object o) {
		return reactiveStateCheck(o, Introspectable.class) &&
				(((Introspectable)o).getMode() & Introspectable.INNER) == Introspectable.INNER;
	}

	/**
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Introspectable#LOGGING}
	 */
	public static boolean isLogging(Object o) {
		return reactiveStateCheck(o, Introspectable.class) &&
				(((Introspectable)o).getMode() & Introspectable.LOGGING) == Introspectable.LOGGING;
	}

	/**
	 * @param o candidate instance
	 * @return a capacity if the tested instance is {@link Backpressurable} otherwise {@literal -1}
	 */
	public static long getCapacity(Object o) {
		if (reactiveStateCheck(o, Backpressurable.class)) {
			return ((Backpressurable) o).getCapacity();
		}
		return -1L;
	}

	/**
	 * @param o candidate instance
	 * @return an error if the tested instance is {@link Introspectable} and is failed otherwise {@literal null}
	 */
	public static Throwable getFailedState(Object o) {
		if (reactiveStateCheck(o, Introspectable.class)) {
			return ((Introspectable) o).getError();
		}
		return null;
	}

	/**
	 * @param o candidate instance
	 * @return a time resolution if the tested instance is {@link Timeable} otherwise {@literal -1}
	 */
	public static long getTimedPeriod(Object o) {
		if (reactiveStateCheck(o, Timeable.class)) {
			return ((Timeable) o).period();
		}
		return -1L;
	}

	/**
	 * @param o candidate instance
	 * @return a threshold limit if the tested instance is {@link Prefetchable} otherwise {@literal -1}
	 */
	public static long getUpstreamLimit(Object o) {
		if (reactiveStateCheck(o, Prefetchable.class)) {
			return ((Prefetchable) o).limit();
		}
		return -1L;
	}

	/**
	 * @param o candidate instance
	 * @return an expected produced metric if the tested instance is {@link Prefetchable} otherwise {@literal -1}
	 */
	public static long getExpectedUpstream(Object o) {
		if (reactiveStateCheck(o, Prefetchable.class)) {
			return ((Prefetchable) o).expectedFromUpstream();
		}
		return -1L;
	}

	/**
	 * @param o candidate instance
	 * @return a current requested count if the tested instance is {@link Requestable} otherwise {@literal -1}
	 */
	public static long getRequestedDownstream(Object o) {
		if (reactiveStateCheck(o, Requestable.class)) {
			return ((Requestable) o).requestedFromDownstream();
		}
		return -1L;
	}

	/**
	 * @param o candidate instance
	 * @return an assigned name if the tested instance is {@link Introspectable} otherwise {@literal anonymous}
	 */
	public static String getName(Object o) {
		if (o == null) {
			return null;
		}

		String name =
				Introspectable.class.isAssignableFrom(o.getClass()) ? (((Introspectable) o).getName()) :
						null;

		name = name == null ? (o.getClass()
		                       .getSimpleName()
		                       .isEmpty() ?
				o.toString() : o.getClass()
				                .getSimpleName()): name;

		name = name.replaceAll("Flux|Subscriber","");

		return name.isEmpty() ? "anonymous" : name;
	}

	/**
	 * @param o candidate instance
	 * @return a String key if the tested instance is {@link Introspectable} otherwise {@literal null}
	 */
	public static String getGroup(Object o) {
		if (o == null) {
			return null;
		}

		Object key = Introspectable.class.isAssignableFrom(o.getClass()) ? (((Introspectable) o).key()) : null;

		if (key == null) {
			return null;
		}

		return key.toString();
	}

	/**
	 * @param o candidate instance
	 * @return an identifier name  produced metric if the tested instance is {@link Introspectable#UNIQUE} otherwise return {@code getName(o).hashCode() + ":" + o.hashCode()}
	 */
	public static String getIdOrDefault(Object o) {
		if (reactiveStateCheck(o, Introspectable.class) &&
				(((Introspectable)o).getMode() & Introspectable.UNIQUE) == Introspectable.UNIQUE) {
			return  ((Introspectable)o).key() != null ? ((Introspectable)o).key().toString() : ((Introspectable) o)
					.getName();
		}
		return getName(o).hashCode() + ":" + o.hashCode();
	}

	/**
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Introspectable#UNIQUE}
	 */
	public static boolean isUnique(Object o) {
		return reactiveStateCheck(o, Introspectable.class) &&
				(((Introspectable)o).getMode() & Introspectable.UNIQUE) == Introspectable.UNIQUE;
	}

	/**
	 * @param o candidate instance
	 * @return true if the tested instance is {@link Introspectable#FACTORY}
	 */
	public static boolean isFactory(Object o) {
		return reactiveStateCheck(o, Introspectable.class) &&
				(((Introspectable)o).getMode() & Introspectable.FACTORY) == Introspectable.FACTORY;
	}

	/**
	 *
	 * @param o candidate instance
	 * @return a waiting count if the tested instance is {@link Backpressurable} otherwise {@literal -1}
	 */
	public static long getBuffered(Object o) {
		if (reactiveStateCheck(o, Backpressurable.class)) {
			return ((Backpressurable) o).getPending();
		}
		return -1L;
	}

	private static boolean reactiveStateCheck(Object o, Class<?> clazz){
		return o != null && clazz.isAssignableFrom(o.getClass());
	}

	/**
	 *
	 */
	public static final class Graph {

		private final Map<String, Node> nodes = new HashMap<>();
		private final Map<String, Edge> edges = new WeakHashMap<>();

		private final boolean subscan;
		private final boolean trace;

		private boolean cyclic;

		Graph() {
			this(false, false);
		}

		Graph(boolean subscan, boolean trace) {
			this.subscan = subscan;
			this.trace = trace;
		}

		/**
		 * Merge this instance with an external graph thus overriding any overlapping local keys.
		 *
		 * @param graph the Graph to append to this instance
		 * @return this instance eventually augmented by the passed Graph
		 */
		public Graph mergeWith(Graph graph) {
			if (graph == null || (graph.nodes.isEmpty() && graph.edges.isEmpty())) {
				return this;
			}
			nodes.putAll(graph.nodes);
			edges.putAll(graph.edges);
			return this;
		}

		/**
		 * Clear unreferenced nodes from a graph
		 *
		 * @return a json array of terminated ids
		 */
		public Collection<String> removeTerminatedNodes() {
			if (nodes.isEmpty()) {
				return null;
			}
			Set<String> removedGraph = new HashSet<>();

			Iterator<Node> nodeIterator = nodes.values()
			                                   .iterator();
			Node node;
			Boolean bool1;
			boolean remove;
			while (nodeIterator.hasNext()) {
				node = nodeIterator.next();

				if (node.isReference()) {
					Node n;
					remove = true;
					for (Edge edge : node.connectionsRef) {
						n = nodes.get(edge.from);
						if (n == null) {
							continue;
						}
						bool1 = n.isCancelled();
						if (bool1 == null || !bool1) {
							remove = false;
							break;
						}
						bool1 = n.isTerminated();
						if (bool1 == null || !bool1) {
							remove = false;
							break;
						}
					}
				}
				else {
					bool1 = node.isTerminated();
					remove = bool1 != null && bool1;

					if (!remove) {
						bool1 = node.isCancelled();
						remove = bool1 != null && bool1;
					}

					if (!remove) {
						remove = node.connectionsRef != null && node.connectionsRef.length == 0;
					}
				}
				if (remove) {
					nodeIterator.remove();
					removedGraph.add("\"" + node.getId() + "\"");
				}
			}

			return removedGraph;
		}

		public Collection<Node> getNodes() {
			return nodes.values();
		}

		public Collection<Edge> getEdges() {
			return edges.values();
		}

		public boolean isCyclic() {
			return cyclic;
		}

		private void addUpstream(Node target, Node grandchild) {
			if (target == null) {
				return;
			}
			Node child;
			if (trace || !isTraceOnly(target.object)) {
				child = target;
				if (nodes.containsKey(child.getId()) && grandchild != null) {
					cyclic = true;
					return;
				}
				nodes.put(child.getId(), child);
			}
			else {
				child = grandchild;
			}
			if (hasUpstream(target.object)) {
				Object in = ((Receiver) target.object).upstream();
				if (!virtualRef(in, target)) {
					Node upstream = expandReactiveSate(in, target.rootId);
					if (child != null && (trace || !isTraceOnly(upstream.object))) {
						addEdge(upstream.createEdgeTo(child));
					}
					addUpstream(upstream, child);
				}
			}
			if (hasUpstreams(target.object)) {
				addUpstreams(child, ((MultiReceiver) target.object).upstreams());
			}
			if (hasDownstreams(target.object)) {
				addDownstreams(child, ((MultiProducer) target.object).downstreams());
			}
		}

		private void addUpstreams(Node target, Iterator o) {
			if (o == null) {
				return;
			}
			Node source;
			Object in;
			while (o.hasNext()) {
				in = o.next();
				if (virtualRef(in, target)) {
					continue;
				}
				source = expandReactiveSate(in, target != null ? target.rootId : null);
				if (target != null && source != null) {
					addEdge(source.createEdgeTo(target, Edge.Type.inner));
				}
				addUpstream(source, target);
			}
		}

		private void addDownstream(Node origin, Node ancestor) {
			if (origin == null) {
				return;
			}
			Node root;
			if (trace || !isTraceOnly(origin.object)) {
				root = origin;
				if (nodes.containsKey(root.getId()) && ancestor != null) {
					cyclic = true;
					return;
				}
				nodes.put(root.getId(), root);
			}
			else {
				root = ancestor;
			}
			if (hasDownstream(origin.object)) {
				Object out = ((Producer) origin.object).downstream();
				if (!virtualRef(out, origin)) {
					Node downstream = expandReactiveSate(out, origin.rootId);
					if (root != null && (trace || !isTraceOnly(downstream.object))) {
						addEdge(root.createEdgeTo(downstream));
					}
					addDownstream(downstream, root);
				}
			}
			if (hasDownstreams(origin.object)) {
				addDownstreams(root, ((MultiProducer) origin.object).downstreams());
			}

			if (hasUpstreams(origin.object)) {
				addUpstreams(root, ((MultiReceiver) origin.object).upstreams());
			}
		}

		private void addDownstreams(Node source, Iterator o) {
			if (o == null) {
				return;
			}
			Node downstream;
			Object out;
			while (o.hasNext()) {
				out = o.next();
				if (virtualRef(out, source)) {
					continue;
				}
				downstream = expandReactiveSate(out, source != null ? source.rootId : null);
				if (source != null && downstream != null) {
					addEdge(source.createEdgeTo(downstream, Edge.Type.inner));
				}
				addDownstream(downstream, source);
			}
		}

		private Node expandReactiveSate(Object o, String rootid) {
			if (o == null) {
				return null;
			}

			String name = getName(o);
			String id = getIdOrDefault(o);

			Node r = new Node(name, id, o, rootid);

			if ((trace || !isTraceOnly(o)) && hasFeedbackLoop(o)) {
				Loopback loop = (Loopback) o;

				Object target = loop.connectedInput();
				if (target != null && target != loop && !virtualRef(target, r)) {
					Node input = expandReactiveSate(target, r.rootId);
					addEdge(r.createEdgeTo(input, Edge.Type.feedbackLoop));
					addDownstream(input, null);
				}

				target = loop.connectedOutput();

				if (target != null && target != loop && !virtualRef(target, r)) {
					Node output = expandReactiveSate(target, r.rootId);
					addEdge(output.createEdgeTo(r, Edge.Type.feedbackLoop));
					addUpstream(output, null);
				}
			}

			return r;
		}

		private void addEdge(Edge edge) {
			edges.put(edge.getId(), edge);
		}

		private boolean virtualRef(Object o, Node ancestor) {
			if (o != null && ancestor != null && String.class.isAssignableFrom(o.getClass())) {
				Node virtualNode = new Node(o.toString(), o.toString(), null, ancestor.rootId);
				Edge edge = ancestor.createEdgeTo(o.toString(), Edge.Type.reference);
				virtualNode.addEdgeRef(edge);
				nodes.put(virtualNode.id, virtualNode);
				addEdge(edge);
				return true;
			}
			return false;
		}

		/**
		 * @param timestamp should the json include a generated timestamp
		 * @return a formated json string including a timestamp
		 */
		public String toString(boolean timestamp) {
			return "{" +
					" \"edges\" : " + edges.values() +
					(trace ? ", \"trace\" : true" : "") +
					", \"nodes\" : " + nodes.values() +
					(subscan ? ", \"full\" : false" : "") +
					(timestamp ? ", \"timestamp\" : " + System.currentTimeMillis() : "") +
					'}';
		}

		@Override
		public String toString() {
			return toString(true);
		}
	}

	/**
	 * A logical view representation of an introspected reference from {@link ReactiveStateUtils#scan(Object)}
	 */
	public static class Node implements Comparable<Node> {

		transient private final Object  object;
		transient private       Edge[]  connectionsRef;
		private final           String  id;
		private final           String  name;
		private final           String group;
		private final           boolean unique;
		private final           boolean factory;
		private final           boolean inner;
		private final           boolean logging;
		private final           String rootId;

		protected Node(String name, String id, Object o, String rootId) {
			this.object = o;
			this.id = id;
			this.name = name;
			this.factory = ReactiveStateUtils.isFactory(o);
			this.inner = isContained(o);
			this.group = ReactiveStateUtils.getGroup(o);
			this.unique = isUnique(o);
			this.rootId = rootId == null ? id : rootId;
			this.logging = ReactiveStateUtils.isLogging(o);
		}

		private void addEdgeRef(Edge edge) {
			if (connectionsRef == null) {
				connectionsRef = new Edge[1];
				connectionsRef[0] = edge;
				return;
			}
			int n = connectionsRef.length;
			Edge[] b = new Edge[n + 1];
			System.arraycopy(connectionsRef, 0, b, 0, n);
			b[n] = edge;
			connectionsRef = b;
		}

		public final Object value() {
			return object;
		}

		public final String getId() {
			return id;
		}

		public final String getName() {
			return name;
		}

		public final String getGroup() {
			return group;
		}

		public final boolean isFactory() {
			return factory;
		}

		public final boolean isInner() {
			return inner;
		}

		public final boolean isLogging() {
			return logging;
		}

		public final boolean isReference() {
			return object == null;
		}

		public final boolean isDefinedId() {
			return unique;
		}

		public final long getCapacity() {
			return ReactiveStateUtils.getCapacity(object);
		}

		public final long getBuffered() {
			return ReactiveStateUtils.getBuffered(object);
		}

		public final long getUpstreamLimit() {
			return ReactiveStateUtils.getUpstreamLimit(object);
		}

		public final long getPeriod() {
			return ReactiveStateUtils.getTimedPeriod(object);
		}

		public final Throwable getFailedState() {
			return ReactiveStateUtils.getFailedState(object);
		}

		public final long getExpectedUpstream() {
			return ReactiveStateUtils.getExpectedUpstream(object);
		}

		public final long getRequestedDownstream() {
			return ReactiveStateUtils.getRequestedDownstream(object);
		}

		public final Boolean isActive() {
			if (!hasSubscription(object)) {
				return null;
			}
			return ((Completable) object).isStarted();
		}

		public final Boolean isTerminated() {
			if (!hasSubscription(object)) {
				return null;
			}
			return ((Completable) object).isTerminated();
		}

		public final Boolean isCancelled() {
			if (!isCancellable(object)) {
				return null;
			}
			return ((Cancellable) object).isCancelled();
		}

		protected final Edge createEdgeTo(Node to) {
			return createEdgeTo(to.id, null);
		}

		protected final Edge createEdgeTo(Node to, Edge.Type type) {
			return createEdgeTo(to.id, type);
		}

		protected final Edge createEdgeTo(String to, Edge.Type type) {
			Edge edge = new Edge(id, to, type);
			addEdgeRef(edge);
			return edge;
		}

		@Override
		public final boolean equals(Object o) {
			if (this == o) {
				return true;
			}

			Node node = (Node) o;

			return id.equals(node.id);
		}

		@Override
		public final int hashCode() {
			return id.hashCode();
		}

		@Override
		public String toString() {
			return toJSON(-1);
		}

		public String toJSON() {
			return toJSON(1);
		}

		public String toJSON(int indent) {
			int i = indent;

			StringBuffer res = new StringBuffer();

			indent("{", res, indent != -1 ? 0 : -1, false);

			indent(property("id", getId()), res, i, true);
			indent(property("origin", rootId), res, i, true);
			if (isDefinedId()) {
				indent(property("definedId", "true"), res, i, true);
			}
			indent(property("name", getName()), res, i, true);
			if (isInner()) {
				indent(property("inner", "true"), res, i, true);
			}
			if (isReference()) {
				indent(property("reference", "true"), res, i, true);
			}
			else {
				Boolean cancelled = isCancelled(); //force volatile access first
				if (getFailedState() != null) {
					indent(property("failed", getFailedState().getMessage()), res, i, true);
				}

				indent(property("period", getPeriod()), res, i, true);
				indent(property("capacity", getCapacity()), res, i, true);
				indent(property("group", getGroup()), res, i, true);
				//indent(property("type", object.getClass().getName()), res, i, true);
				indent(property("buffered", getBuffered()), res, i, true);

				if (isFactory()) {
					indent(property("factory", "true"), res, i, true);
				}

				if (isLogging()) {
					indent(property("logging", "true"), res, i, true);
				}

				indent(property("upstreamLimit", getUpstreamLimit()), res, i, true);
				indent(property("expectedUpstream", getExpectedUpstream()), res, i, true);
				indent(property("requestedDownstream", getRequestedDownstream()), res, i, true);
				indent(property("active", isActive()), res, i, true);
				indent(property("terminated", isTerminated()), res, i, true);
				indent(property("cancelled", cancelled), res, i, false);
			}

			indent("}", res, indent != -1 ? 0 : -1, false);

			return res.toString();
		}

		@Override
		public int compareTo(Node o) {
			return name.compareTo(o.name);
		}
	}

	/**
	 * A relationship between 2 concrete or virtual node references
	 */
	public static class Edge {

		public enum Type {feedbackLoop, inner, reference}

		private final String from;
		private final String to;
		private final Type   type;

		protected Edge(String from, String to, Type type) {
			this.from = from;
			this.to = to;
			this.type = type;
		}

		public final String getFrom() {
			return from;
		}

		public final String getTo() {
			return to;
		}

		public final Type getType() {
			return type;
		}

		public final String getId() {
			return from + "_" + to;
		}

		@Override
		public final boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}

			Edge edge = (Edge) o;

			if (!from.equals(edge.from)) {
				return false;
			}
			return to.equals(edge.to);

		}

		@Override
		public final int hashCode() {
			int result = from.hashCode();
			result = 31 * result + to.hashCode();
			return result;
		}

		@Override
		public String toString() {
			return "{ " + property("id", getId()) +
					(type != null ? ", " + property("type", type.name()) : "") +
					", " + property("from", from) +
					", " + property("to", to) + " }";
		}
	}

	/**
	 * Parse and encode a property as a json map entry.
	 * @param name the property name
	 * @param value the raw value to encode
	 *
	 * @return an encoded json map entry
	 */
	public static String property(String name, Object value) {
		if (value == null || value.equals(-1) || value.equals(-1L)) {
			return "";
		}

		if (Number.class.isAssignableFrom(value.getClass())) {
			if (Long.MAX_VALUE == ((Number) value).longValue()) {
				return "\"" + name + "\" : \"unbounded\"";
			}
			return "\"" + name + "\" : " + value.toString();
		}

		if (Boolean.class.isAssignableFrom(value.getClass())) {
			return "\"" + name + "\" : " + value.toString();
		}

		return "\"" + name + "\" : " +
				(String.class.isAssignableFrom(value.getClass()) ? "\"" + value.toString().replaceAll("\"", "\\\"")
						+ "\"" : value);
	}

	static void indent(String symbol, StringBuffer res, int indent, boolean comma) {
		if (symbol.isEmpty()) {
			return;
		}
		for (int i = 0; i < indent; i++) {
			res.append("\t");
		}
		res.append(symbol);
		if (comma) {
			res.append(", ");
		}
		if (indent > -1) {
			res.append("\n");
		}
	}
}
