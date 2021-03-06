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

import org.reactivestreams.Subscriber;
import reactor.core.state.Timeable;
import reactor.core.timer.Timer;
import reactor.core.util.EmptySubscription;
import reactor.core.util.Exceptions;

/**
 * @author Stephane Maldini
 */
final class FluxInterval extends Flux<Long> implements Timeable {

	final private long     delay;
	final private long     period;
	final private Timer    timer;

	public FluxInterval(long delay, long period, Timer timer) {
		this.delay = delay >= 0L ? delay : -1L;
		this.period = period;
		this.timer = timer;
	}

	@Override
	public void subscribe(final Subscriber<? super Long> s) {
		try {
			s.onSubscribe(timer.interval(s, period, delay));
		}
		catch (Throwable t) {
			Exceptions.throwIfFatal(t);
			EmptySubscription.error(s, Exceptions.unwrap(t));
		}
	}

	@Override
	public long period() {
		return delay;
	}

	@Override
	public long getCapacity() {
		return Long.MAX_VALUE;
	}
}
