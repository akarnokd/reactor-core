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
final class MonoDelay extends Mono<Long> implements Timeable {

	final Timer    parent;
	final long     delay;

	public MonoDelay(Timer timer, long delay) {
		this.parent = timer;
		this.delay = delay;
	}

	@Override
	public void subscribe(Subscriber<? super Long> s) {
		try {
			s.onSubscribe(parent.single(s, delay));
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

}
