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

import org.junit.Test;
import reactor.core.test.TestSubscriber;
import reactor.core.util.Assert;

public class FluxWithLatestFromTest {


	@Test(expected = NullPointerException.class)
	public void sourceNull() {
		new FluxWithLatestFrom<>(null, Flux.never(), (a, b) -> a);
	}

	@Test(expected = NullPointerException.class)
	public void otherNull() {
		new FluxWithLatestFrom<>(Flux.never(), null, (a, b) -> a);
	}

	@Test(expected = NullPointerException.class)
	public void combinerNull() {
		new FluxWithLatestFrom<>(Flux.never(), Flux.never(), null);
	}

	@Test
	public void normal() {
		TestSubscriber<Integer> ts = new TestSubscriber<>();

		new FluxWithLatestFrom<>(new FluxRange(1, 10), new FluxJust<>(10), (a, b) -> a + b).subscribe
		  (ts);

		ts.assertValues(11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
		  .assertComplete()
		  .assertNoError();
	}

	@Test
	public void normalBackpressured() {
		TestSubscriber<Integer> ts = new TestSubscriber<>(0);

		new FluxWithLatestFrom<>(new FluxRange(1, 10), new FluxJust<>(10), (a, b) -> a + b).subscribe
		  (ts);

		ts.assertNoValues()
		  .assertNotComplete()
		  .assertNoError();

		ts.request(2);

		ts.assertValues(11, 12)
		  .assertNotComplete()
		  .assertNoError();

		ts.request(5);

		ts.assertValues(11, 12, 13, 14, 15, 16, 17)
		  .assertNotComplete()
		  .assertNoError();

		ts.request(10);

		ts.assertValues(11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
		  .assertComplete()
		  .assertNoError();
	}

	@Test
	public void otherIsNever() {
		TestSubscriber<Integer> ts = new TestSubscriber<>();

		new FluxWithLatestFrom<>(new FluxRange(1, 10), Flux.<Integer>empty(), (a, b) -> a + b)
		  .subscribe(ts);

		ts.assertNoValues()
		  .assertNoError()
		  .assertComplete();
	}

	@Test
	public void otherIsEmpty() {
		TestSubscriber<Integer> ts = new TestSubscriber<>(0);

		new FluxWithLatestFrom<>(new FluxRange(1, 10), Flux.<Integer>empty(), (a, b) -> a + b)
		  .subscribe(ts);

		ts.assertNoValues()
		  .assertNoError()
		  .assertComplete();
	}

	@Test
	public void combinerReturnsNull() {
		TestSubscriber<Integer> ts = new TestSubscriber<>();

		new FluxWithLatestFrom<>(new FluxRange(1, 10), new FluxJust<>(10), (a, b) -> (Integer) null)
		  .subscribe(ts);

		ts.assertNoValues()
		  .assertNotComplete()
		  .assertError(NullPointerException.class);
	}

	@Test
	public void combinerThrows() {
		TestSubscriber<Integer> ts = new TestSubscriber<>();

		new FluxWithLatestFrom<Integer, Integer, Integer>(
		  new FluxRange(1, 10), new FluxJust<>(10),
		  (a, b) -> {
			  throw new RuntimeException("forced failure");
		  }).subscribe(ts);

		ts.assertNoValues()
		  .assertNotComplete()
		  .assertError(RuntimeException.class)
		  .assertErrorWith( e -> Assert.isTrue(e.getMessage().contains("forced failure")));
	}
}
