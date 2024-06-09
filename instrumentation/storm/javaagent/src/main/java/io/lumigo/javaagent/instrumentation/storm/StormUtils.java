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
package io.lumigo.javaagent.instrumentation.storm;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.storm.tuple.AddressedTuple;
import org.apache.storm.tuple.Tuple;

public class StormUtils {

  public static String STORM_TYPE_KEY = "storm.type";
  public static String STORM_ID_KEY = "storm.id";
  public static String STORM_VERSION_KEY = "storm.version";
  public static String STORM_TUPLE_VALUES_KEY = "storm.tuple.values";
  public static String SOURCE_COMPONENT_KEY = "source.component";
  public static String COMPONENT_NAME_KEY = "component.name";

  public static String getThreadName() {
    return Thread.currentThread().getName();
  }

  public static String getComponentName() {
    // The only way to get the name is from the thread name
    return getThreadName().split("-")[2];
  }

  public static String getStormVersion(Tuple tuple) {
    return tuple.getContext().getRawTopology().get_storm_version();
  }

  public static String getStormId(Tuple tuple) {
    return tuple.getContext().getStormId();
  }

  public static String getSourceComponent(Tuple tuple) {
    return tuple.getSourceComponent();
  }

  public static List<String> getValues(Tuple tuple) {
    return tuple.getValues().stream().map(Object::toString).collect(Collectors.toList());
  }

  public static String getMessageId(Tuple tuple) {
    return tuple.getMessageId().toString();
  }

  public static String getDestComponent(AddressedTuple addressedTuple) {
    return addressedTuple.tuple.getContext().getComponentId(addressedTuple.getDest());
  }
}
