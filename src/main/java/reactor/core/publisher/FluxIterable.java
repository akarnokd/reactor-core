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
package reactor.core.publisher;

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.reactivestreams.Subscriber;
import reactor.core.flow.Fuseable;
import reactor.core.flow.Producer;
import reactor.core.flow.Receiver;
import reactor.core.state.Cancellable;
import reactor.core.state.Completable;
import reactor.core.state.Requestable;
import reactor.core.util.BackpressureUtils;
import reactor.core.util.EmptySubscription;

/**
 * Emits the contents of an Iterable source.
 *
 * @param <T> the value type
 */

/**
 * {@see <a href='https://github.com/reactor/reactive-streams-commons'>https://github.com/reactor/reactive-streams-commons</a>}
 * @since 2.5
 */
final class FluxIterable<T>
		extends Flux<T>
		implements Receiver, Fuseable {

	final Iterable<? extends T> iterable;

	public FluxIterable(Iterable<? extends T> iterable) {
		this.iterable = Objects.requireNonNull(iterable, "iterable");
	}

	@Override
	public void subscribe(Subscriber<? super T> s) {
		Iterator<? extends T> it;

		try {
			it = iterable.iterator();
		} catch (Throwable e) {
			EmptySubscription.error(s, e);
			return;
		}

		subscribe(s, it);
	}

	@Override
	public Object upstream() {
		return iterable;
	}

	/**
	 * Common method to take an Iterator as a source of values.
	 *
	 * @param s
	 * @param it
	 */
	static <T> void subscribe(Subscriber<? super T> s, Iterator<? extends T> it) {
		if (it == null) {
			EmptySubscription.error(s, new NullPointerException("The iterator is null"));
			return;
		}

		boolean b;

		try {
			b = it.hasNext();
		} catch (Throwable e) {
			EmptySubscription.error(s, e);
			return;
		}
		if (!b) {
			EmptySubscription.complete(s);
			return;
		}

		s.onSubscribe(new IterableSubscription<>(s, it));
	}

	static final class IterableSubscription<T>
			implements Producer, Completable, Requestable, Cancellable, SynchronousSubscription<T> {

		final Subscriber<? super T> actual;

		final Iterator<? extends T> iterator;

		volatile boolean cancelled;

		volatile long requested;
		@SuppressWarnings("rawtypes")
		static final AtomicLongFieldUpdater<IterableSubscription> REQUESTED =
		  AtomicLongFieldUpdater.newUpdater(IterableSubscription.class, "requested");

		int state;
		
		/** Indicates that the iterator's hasNext returned true before but the value is not yet retrieved. */
		static final int STATE_HAS_NEXT_NO_VALUE = 0;
		/** Indicates that there is a value available in current. */
		static final int STATE_HAS_NEXT_HAS_VALUE = 1;
		/** Indicates that there are no more values available. */
		static final int STATE_NO_NEXT = 2;
		/** Indicates that the value has been consumed and a new value should be retrieved. */
		static final int STATE_CALL_HAS_NEXT = 3;
		
		T current;
		
		public IterableSubscription(Subscriber<? super T> actual, Iterator<? extends T> iterator) {
			this.actual = actual;
			this.iterator = iterator;
		}

		@Override
		public void request(long n) {
			if (BackpressureUtils.validate(n)) {
				if (BackpressureUtils.addAndGet(REQUESTED, this, n) == 0) {
					if (n == Long.MAX_VALUE) {
						fastPath();
					} else {
						slowPath(n);
					}
				}
			}
		}

		void slowPath(long n) {
			final Iterator<? extends T> a = iterator;
			final Subscriber<? super T> s = actual;

			long e = 0L;

			for (; ; ) {

				while (e != n) {
					T t;

					try {
						t = a.next();
					} catch (Throwable ex) {
						s.onError(ex);
						return;
					}

					if (cancelled) {
						return;
					}

					if (t == null) {
						s.onError(new NullPointerException("The iterator returned a null value"));
						return;
					}

					s.onNext(t);

					if (cancelled) {
						return;
					}

					boolean b;

					try {
						b = a.hasNext();
					} catch (Throwable ex) {
						s.onError(ex);
						return;
					}

					if (cancelled) {
						return;
					}

					if (!b) {
						s.onComplete();
						return;
					}

					e++;
				}

				n = requested;

				if (n == e) {
					n = REQUESTED.addAndGet(this, -e);
					if (n == 0L) {
						return;
					}
					e = 0L;
				}
			}
		}

		void fastPath() {
			final Iterator<? extends T> a = iterator;
			final Subscriber<? super T> s = actual;

			for (; ; ) {

				if (cancelled) {
					return;
				}

				T t;

				try {
					t = a.next();
				} catch (Exception ex) {
					s.onError(ex);
					return;
				}

				if (cancelled) {
					return;
				}

				if (t == null) {
					s.onError(new NullPointerException("The iterator returned a null value"));
					return;
				}

				s.onNext(t);

				if (cancelled) {
					return;
				}

				boolean b;

				try {
					b = a.hasNext();
				} catch (Exception ex) {
					s.onError(ex);
					return;
				}

				if (cancelled) {
					return;
				}

				if (!b) {
					s.onComplete();
					return;
				}
			}
		}

		@Override
		public void cancel() {
			cancelled = true;
		}

		@Override
		public boolean isCancelled() {
			return cancelled;
		}

		@Override
		public boolean isStarted() {
			return iterator.hasNext();
		}

		@Override
		public boolean isTerminated() {
			return !iterator.hasNext();
		}

		@Override
		public Object downstream() {
			return actual;
		}

		@Override
		public long requestedFromDownstream() {
			return requested;
		}

		@Override
		public void clear() {
			// no op
		}
		
		@Override
		public boolean isEmpty() {
		   int s = state;
		   if (s == STATE_NO_NEXT) {
			   return true;
		   } else
		   if (s == STATE_HAS_NEXT_HAS_VALUE || s == STATE_HAS_NEXT_NO_VALUE) {
			   return false;
		   } else
		   if (iterator.hasNext()) {
			   state = STATE_HAS_NEXT_NO_VALUE;
			   return false;
		   }
		   state = STATE_NO_NEXT;
		   return true;
		}
		
		@Override
		public T peek() {
			if (!isEmpty()) {
				T c;
				if (state == STATE_HAS_NEXT_NO_VALUE) {
					c = iterator.next();
					current = c;
					state = STATE_HAS_NEXT_HAS_VALUE;
				} else {
					c = current;
				}
				if (c == null) {
					throw new NullPointerException();
				}
				return c;
			}
			return null;
		}
		
		@Override
		public T poll() {
			if (!isEmpty()) {
				T c;
				if (state == STATE_HAS_NEXT_NO_VALUE) {
					c = iterator.next();
				} else {
					c = current;
					current = null;
				}
				state = STATE_CALL_HAS_NEXT;
				if (c == null) {
					throw new NullPointerException();
				}
				return c;
			}
			return null;
		}
		
		@Override
		public void drop() {
			current = null;
			state = STATE_CALL_HAS_NEXT;
		}
		
		@Override
		public int size() {
			if (state == STATE_NO_NEXT) {
				return 0;
			}
			return 1;
		}
	}
}
