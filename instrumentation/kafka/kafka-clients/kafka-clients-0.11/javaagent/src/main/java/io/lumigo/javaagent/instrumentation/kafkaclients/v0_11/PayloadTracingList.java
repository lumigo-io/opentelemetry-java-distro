/*
 * Copyright 2024 Lumigo LTD
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.lumigo.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class PayloadTracingList<K, V> extends PayloadTracingIterable<K, V>
    implements List<ConsumerRecord<K, V>> {
  private final List<ConsumerRecord<K, V>> delegate;

  private PayloadTracingList(List<ConsumerRecord<K, V>> delegate) {
    super(delegate);
    this.delegate = delegate;
  }

  public static <K, V> List<ConsumerRecord<K, V>> wrap(List<ConsumerRecord<K, V>> delegate) {
    if (KafkaClientsConsumerProcessTracing.wrappingEnabled()) {
      return new PayloadTracingList<>(delegate);
    }
    return delegate;
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean contains(Object o) {
    return delegate.contains(o);
  }

  @Override
  public Object[] toArray() {
    return delegate.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return delegate.toArray(a);
  }

  @Override
  public boolean add(ConsumerRecord<K, V> consumerRecord) {
    return delegate.add(consumerRecord);
  }

  @Override
  public void add(int index, ConsumerRecord<K, V> element) {
    delegate.add(index, element);
  }

  @Override
  public boolean remove(Object o) {
    return delegate.remove(o);
  }

  @Override
  public ConsumerRecord<K, V> remove(int index) {
    return delegate.remove(index);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return delegate.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends ConsumerRecord<K, V>> c) {
    return delegate.addAll(c);
  }

  @Override
  public boolean addAll(int index, Collection<? extends ConsumerRecord<K, V>> c) {
    return delegate.addAll(index, c);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return delegate.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return delegate.retainAll(c);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ConsumerRecord<K, V> get(int index) {
    return delegate.get(index);
  }

  @Override
  public ConsumerRecord<K, V> set(int index, ConsumerRecord<K, V> element) {
    return delegate.set(index, element);
  }

  @Override
  public int indexOf(Object o) {
    return delegate.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return delegate.lastIndexOf(o);
  }

  @Override
  public ListIterator<ConsumerRecord<K, V>> listIterator() {
    return delegate.listIterator();
  }

  @Override
  public ListIterator<ConsumerRecord<K, V>> listIterator(int index) {
    return delegate.listIterator(index);
  }

  @Override
  public List<ConsumerRecord<K, V>> subList(int fromIndex, int toIndex) {
    return delegate.subList(fromIndex, toIndex);
  }
}
